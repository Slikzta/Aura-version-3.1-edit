package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aura.core.agent.AgentPlan
import com.example.aura.core.agent.AgentState
import com.example.aura.core.agent.AgentStep
import com.example.aura.core.agent.StepStatus
import com.example.aura.core.security.ApprovalPolicy
import com.example.aura.core.security.ApprovalRequest
import com.example.aura.core.security.SecurityLevel
import com.example.aura.core.tools.AuraTool
import com.example.aura.core.tools.ToolCategory
import com.example.aura.core.tools.ToolExecutionContext
import com.example.aura.core.tools.ToolExecutionResult
import com.example.aura.core.tools.ToolParameter
import com.example.aura.core.tools.ToolSchema
import com.example.aura.di.AuraContainer
import com.example.aura.ui.AuraMainViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraAutonomousExecutionTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var container: AuraContainer
    private lateinit var viewModel: AuraMainViewModel

    @Before
    fun setUp() {
        AuraContainer.setInstance(null)
        container = AuraContainer(context)
        AuraContainer.setInstance(container)
        viewModel = AuraMainViewModel(container)
    }

    @After
    fun tearDown() {
        AuraContainer.setInstance(null)
    }

    @Test
    fun testAutonomousPlanCreationAndSuccessfulExecution() = runTest {
        // Formulate a real autonomous plan using built-in tool implementations
        val plan = container.agent.formulateAutonomousPlan(
            goalTitle = "Daily Health and Connectivity Audit",
            description = "Audit device connectivity and system telemetry",
            projectId = "test_proj_001"
        )

        assertNotNull(plan)
        assertTrue(plan.steps.isNotEmpty())
        assertEquals("Daily Health and Connectivity Audit", plan.goalTitle)
        assertTrue(plan.steps.all { it.status == StepStatus.PENDING })

        // Execute the plan
        val job = container.agent.executeAutonomousPlan()

        // Wait for coroutine and looper execution, resolving any required approval gates
        for (i in 1..40) {
            val pending = container.approvalManager.pendingRequests.value
            pending.forEach { req ->
                container.approvalManager.resolveRequest(req.id, approved = true)
            }
            ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (!container.agent.activePlan.value!!.isRunning) break
            Thread.sleep(25)
        }
        job.join()

        val completedPlan = container.agent.activePlan.value
        assertNotNull(completedPlan)
        assertFalse("Plan should not be running after completion", completedPlan!!.isRunning)
        assertTrue("All steps should be COMPLETED", completedPlan.steps.all { it.status == StepStatus.COMPLETED })
        assertEquals(AgentState.IDLE, container.agent.agentState.value)

        // Verify output was passed to sandboxed file report
        val sandboxReportStep = completedPlan.steps.find { it.toolId == "sandboxed_file" || it.toolId == "sandboxed_filesystem" }
        assertNotNull(sandboxReportStep)
        assertNotNull(sandboxReportStep!!.resultSummary)
        assertTrue(sandboxReportStep.resultSummary!!.contains("saved") || sandboxReportStep.resultSummary!!.contains("Wrote") || sandboxReportStep.resultSummary!!.contains("File"))

        // Verify report file was physically created in sandbox directory
        val sandboxDir = File(context.filesDir, "aura_sandbox")
        assertTrue(sandboxDir.exists())
        val files = sandboxDir.listFiles()
        assertTrue("Sandbox files should exist", files != null && files.isNotEmpty())
    }

    @Test
    fun testPlanStopsOnToolFailureAndAllowsRetry() = runTest {
        val executionCount = AtomicInteger(0)

        // Register a flaky tool that fails on first attempt and succeeds on second attempt
        val flakyTool = object : AuraTool {
            override val id: String = "flaky_test_tool"
            override val name: String = "Flaky Test Tool"
            override val description: String = "Tool that fails initially"
            override val category: ToolCategory = ToolCategory.AUTOMATION
            override val securityLevel: SecurityLevel = SecurityLevel.SAFE
            override val inputSchema: ToolSchema = ToolSchema(listOf(ToolParameter("flag", "string", "Test flag", required = true)))

            override suspend fun execute(context: ToolExecutionContext, arguments: Map<String, Any?>): ToolExecutionResult {
                val count = executionCount.incrementAndGet()
                return if (count == 1) {
                    ToolExecutionResult.Failure("Network socket timeout on remote endpoint")
                } else {
                    ToolExecutionResult.Success("Flaky operation succeeded on retry attempt $count")
                }
            }
        }
        container.toolRegistry.registerTool(flakyTool)

        val step1 = AgentStep(
            stepIndex = 1,
            description = "Run flaky network operation",
            toolId = "flaky_test_tool",
            arguments = mapOf("flag" to "test_value"),
            status = StepStatus.PENDING
        )
        val step2 = AgentStep(
            stepIndex = 2,
            description = "Subsequent dependent step",
            toolId = "system_diagnostics",
            arguments = mapOf("action" to "get_metrics"),
            status = StepStatus.PENDING
        )

        val plan = AgentPlan(
            goalTitle = "Flaky Resilience Plan",
            steps = listOf(step1, step2)
        )
        container.agent.setAutonomousPlan(plan)

        // 1. Initial execution fails on Step 1
        val job1 = container.agent.executeAutonomousPlan()
        for (i in 1..40) {
            ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (!container.agent.activePlan.value!!.isRunning) break
            Thread.sleep(25)
        }
        job1.join()

        val failedPlan = container.agent.activePlan.value!!
        assertFalse(failedPlan.isRunning)
        assertNotNull(failedPlan.failureReason)
        assertEquals(StepStatus.FAILED, failedPlan.steps[0].status)
        assertEquals("Network socket timeout on remote endpoint", failedPlan.steps[0].error)
        // Step 2 must NOT have run (stops subsequent steps on failure)
        assertEquals(StepStatus.PENDING, failedPlan.steps[1].status)

        // 2. Retry the failed step
        val job2 = container.agent.retryPlanStep(1)
        assertNotNull(job2)
        for (i in 1..40) {
            ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (!container.agent.activePlan.value!!.isRunning) break
            Thread.sleep(25)
        }
        job2!!.join()

        val retriedPlan = container.agent.activePlan.value!!
        assertFalse(retriedPlan.isRunning)
        assertEquals(StepStatus.COMPLETED, retriedPlan.steps[0].status)
        assertEquals(StepStatus.COMPLETED, retriedPlan.steps[1].status)
        assertTrue(retriedPlan.steps[0].resultSummary!!.contains("succeeded on retry"))
    }

    @Test
    fun testSecurityApprovalGateForHighRiskAutonomousStep() = runTest {
        // Set approval policy to ZERO_TRUST
        container.approvalManager.setPolicy(ApprovalPolicy.ZERO_TRUST)

        // Step requires high-risk messaging tool
        val step = AgentStep(
            stepIndex = 1,
            description = "Send high-risk SMS notification",
            toolId = "messaging",
            arguments = mapOf("recipient_phone" to "+15550199", "message_body" to "High risk alert"),
            status = StepStatus.PENDING
        )
        val plan = AgentPlan(goalTitle = "High Risk Gate Plan", steps = listOf(step))
        container.agent.setAutonomousPlan(plan)

        val job = container.agent.executeAutonomousPlan()

        // Give coroutine time to hit the approval gate
        for (i in 1..20) {
            ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (container.approvalManager.pendingRequests.value.isNotEmpty()) break
            Thread.sleep(25)
        }

        // Verify plan entered AWAITING_APPROVAL state
        val pendingList = container.approvalManager.pendingRequests.value
        assertEquals(1, pendingList.size)
        val request = pendingList[0]
        assertEquals("messaging", request.toolId)

        // Decline approval
        container.approvalManager.resolveRequest(request.id, approved = false)

        for (i in 1..20) {
            ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (!container.agent.activePlan.value!!.isRunning) break
            Thread.sleep(25)
        }
        job.join()

        val finishedPlan = container.agent.activePlan.value!!
        assertEquals(StepStatus.FAILED, finishedPlan.steps[0].status)
        assertTrue(finishedPlan.steps[0].error!!.contains("declined") || finishedPlan.steps[0].error!!.contains("Declined"))
    }

    @Test
    fun testPlanCancellationHaltsExecution() = runTest {
        val longRunningTool = object : AuraTool {
            override val id: String = "long_running_tool"
            override val name: String = "Long Running Tool"
            override val description: String = "Simulates long operation"
            override val category: ToolCategory = ToolCategory.AUTOMATION
            override val securityLevel: SecurityLevel = SecurityLevel.SAFE
            override val inputSchema: ToolSchema = ToolSchema(emptyList())

            override suspend fun execute(context: ToolExecutionContext, arguments: Map<String, Any?>): ToolExecutionResult {
                kotlinx.coroutines.delay(5000L)
                return ToolExecutionResult.Success("Completed after delay")
            }
        }
        container.toolRegistry.registerTool(longRunningTool)

        val step1 = AgentStep(stepIndex = 1, description = "Long task", toolId = "long_running_tool")
        val step2 = AgentStep(stepIndex = 2, description = "Next task", toolId = "system_diagnostics", arguments = mapOf("action" to "get_metrics"))
        val plan = AgentPlan(goalTitle = "Cancel Plan", steps = listOf(step1, step2))
        container.agent.setAutonomousPlan(plan)

        val job = container.agent.executeAutonomousPlan()
        ShadowLooper.idleMainLooper()
        testScheduler.runCurrent()

        // Cancel while running
        container.agent.cancelAutonomousPlan()

        for (i in 1..20) {
            ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (!container.agent.activePlan.value!!.isRunning) break
            Thread.sleep(20)
        }

        val cancelledPlan = container.agent.activePlan.value!!
        assertFalse(cancelledPlan.isRunning)
        assertTrue(cancelledPlan.failureReason!!.contains("Cancelled") || cancelledPlan.failureReason!!.contains("cancelled"))
        assertEquals(StepStatus.PENDING, cancelledPlan.steps[1].status)
    }

    @Test
    fun testInputValidationFailureStopsExecution() = runTest {
        // sandboxed_filesystem requires 'action' and 'path' for 'write_file'
        val invalidStep = AgentStep(
            stepIndex = 1,
            description = "Write file without required action parameter",
            toolId = "sandboxed_file",
            arguments = emptyMap(), // Missing required 'action'
            status = StepStatus.PENDING
        )
        val plan = AgentPlan(goalTitle = "Validation Test Plan", steps = listOf(invalidStep))
        container.agent.setAutonomousPlan(plan)

        val job = container.agent.executeAutonomousPlan()
        for (i in 1..20) {
            ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (!container.agent.activePlan.value!!.isRunning) break
            Thread.sleep(20)
        }
        job.join()

        val planResult = container.agent.activePlan.value!!
        assertFalse(planResult.isRunning)
        assertEquals(StepStatus.FAILED, planResult.steps[0].status)
        assertTrue(planResult.steps[0].error!!.contains("validation") || planResult.steps[0].error!!.contains("Missing"))
    }
}

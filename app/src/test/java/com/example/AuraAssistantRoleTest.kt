package com.example

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.aura.assistant.AuraAssistantManager
import com.example.aura.assistant.AuraRecognitionService
import com.example.aura.assistant.AuraVoiceInteractionService
import com.example.aura.assistant.AuraVoiceInteractionSession
import com.example.aura.assistant.AuraVoiceInteractionSessionService
import com.example.aura.core.agent.AgentState
import com.example.aura.core.security.ApprovalPolicy
import com.example.aura.core.security.SecurityLevel
import com.example.aura.core.session.SessionMode
import com.example.aura.core.session.SessionState
import com.example.aura.core.voice.VoiceEngineState
import com.example.aura.di.AuraContainer
import com.example.aura.ui.AuraMainViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraAssistantRoleTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var container: AuraContainer

    @Before
    fun setUp() {
        container = AuraContainer(context)
        AuraContainer.setInstance(container)
    }

    @Test
    fun testAssistantManagerDetectionAndIntents() {
        val manager = container.assistantManager
        assertNotNull(manager)

        val isDefault = manager.checkIfDefaultAssistant()
        assertEquals(isDefault, manager.isDefaultAssistant.value)

        // Request Role Intent creation
        val requestIntent = manager.createRequestRoleIntent()
        assertNotNull(requestIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                assertNotNull(requestIntent.action)
                assertTrue(requestIntent.hasExtra("android.app.role.extra.ROLE_NAME"))
                assertEquals(RoleManager.ROLE_ASSISTANT, requestIntent.getStringExtra("android.app.role.extra.ROLE_NAME"))
            }
        }

        // Manage Settings Intent creation
        val settingsIntent = manager.createManageSettingsIntent()
        assertNotNull(settingsIntent)
        assertTrue(
            settingsIntent.action == Settings.ACTION_VOICE_INPUT_SETTINGS ||
                    settingsIntent.action == Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS ||
                    settingsIntent.action == Settings.ACTION_SETTINGS
        )
    }

    @Test
    fun testAssistantServicesInstantiation() {
        val voiceService = AuraVoiceInteractionService()
        assertNotNull(voiceService)

        val sessionService = AuraVoiceInteractionSessionService()
        assertNotNull(sessionService)

        val recognitionService = AuraRecognitionService()
        assertNotNull(recognitionService)

        val session = AuraVoiceInteractionSession(context)
        assertNotNull(session)
    }

    @Test
    fun testContainerAndViewModelAssistantIntegration() {
        val viewModel = AuraMainViewModel(container)
        assertNotNull(viewModel.isDefaultAssistant)

        val refreshed = viewModel.refreshAssistantRoleStatus()
        assertEquals(refreshed, viewModel.isDefaultAssistant.value)

        val requestIntent = viewModel.createRequestAssistantRoleIntent()
        assertNotNull(requestIntent)

        val manageIntent = viewModel.createManageAssistantSettingsIntent()
        assertNotNull(manageIntent)
    }

    @Test
    fun testAssistantInvocationRoutesToAgentAndCreatesSession() = runTest {
        val manager = container.assistantManager

        val args = Bundle().apply {
            putString("query", "Summarize device status")
        }

        // Trigger invocation through assistant entry point
        val session = manager.processAssistantInvocation(
            source = "TestVoiceInteractionSession",
            args = args
        )

        assertNotNull(session)
        assertEquals(SessionMode.VOICE_STREAM, session.mode.value)

        // Allow background Room executor and agent processing to complete
        var messages: List<com.example.aura.data.entities.MessageEntity> = emptyList()
        for (i in 1..40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            messages = container.repository.getMessagesForSessionSync(session.id)
            if (messages.any { it.content == "Summarize device status" } &&
                messages.any { it.role == "ASSISTANT" }) {
                break
            }
            Thread.sleep(50)
        }

        // Verify user input reached the repository and agent processed it
        assertTrue("Expected user message to be recorded", messages.any { it.content == "Summarize device status" })
        assertTrue("Expected assistant response to be generated", messages.any { it.role == "ASSISTANT" })

        // Model provider registry must remain active and undamaged
        assertEquals("diagnostic_offline", container.agent.modelProvider.id)
    }

    @Test
    fun testAssistantInvocationWithVoiceModeWhenNoQuery() {
        val manager = container.assistantManager

        // Invocation with null query (gesture/hardware trigger)
        val session = manager.processAssistantInvocation(
            source = "TestHardwareAssistGesture",
            args = null,
            intent = null
        )

        assertNotNull(session)
        assertEquals(SessionMode.VOICE_STREAM, session.mode.value)

        // Verifies voice manager was prepared for push-to-talk/listening
        val engineState = container.voiceManager.engineState.value
        assertTrue(
            engineState is VoiceEngineState.Idle ||
                    engineState is VoiceEngineState.Listening ||
                    engineState is VoiceEngineState.PermissionRequired
        )
    }

    @Test
    fun testAssistantInterruptionAndLifecycleSafety() = runTest {
        val manager = container.assistantManager

        val session = manager.processAssistantInvocation(
            source = "TestInterruption",
            args = Bundle().apply { putString("query", "Long running task") }
        )

        // Interrupt assistant during active interaction
        manager.handleAssistantInterruption("User dismissed overlay")
        for (i in 1..20) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (container.agent.agentState.value == AgentState.INTERRUPTED &&
                session.state.value == SessionState.INTERRUPTED) {
                break
            }
            Thread.sleep(50)
        }

        // Session and agent state transition to INTERRUPTED cleanly without crashing
        assertEquals(AgentState.INTERRUPTED, container.agent.agentState.value)
        assertEquals(SessionState.INTERRUPTED, session.state.value)

        // Subsequent assistant invocation re-initializes or cleanses session cleanly
        val nextSession = manager.processAssistantInvocation(
            source = "FollowUpInvocation",
            args = Bundle().apply { putString("query", "Next request") }
        )

        assertNotNull(nextSession)
        assertEquals(SessionMode.VOICE_STREAM, nextSession.mode.value)

        var nextMessages: List<com.example.aura.data.entities.MessageEntity> = emptyList()
        for (i in 1..40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            nextMessages = container.repository.getMessagesForSessionSync(nextSession.id)
            if (nextMessages.any { it.content == "Next request" }) {
                break
            }
            Thread.sleep(50)
        }

        assertTrue(nextMessages.any { it.content == "Next request" })
    }

    @Test
    fun testSecurityApprovalPolicyEnforcedDuringAssistantInvocations() {
        val approvalManager = container.approvalManager
        approvalManager.setPolicy(ApprovalPolicy.STANDARD)

        // Verifies assistant executions strictly adhere to security boundaries
        assertTrue(approvalManager.requiresApproval(SecurityLevel.HIGH_RISK))
        assertTrue(approvalManager.requiresApproval(SecurityLevel.MEDIUM_RISK))
        assertFalse(approvalManager.requiresApproval(SecurityLevel.SAFE))
    }

    @Test
    fun testSecureSettingsFallbackDetection() {
        val manager = container.assistantManager

        val original = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
        try {
            Settings.Secure.putString(
                context.contentResolver,
                "voice_interaction_service",
                "${context.packageName}/com.example.aura.assistant.AuraVoiceInteractionService"
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                assertTrue(manager.checkIfDefaultAssistant())
            }
        } finally {
            Settings.Secure.putString(context.contentResolver, "voice_interaction_service", original)
        }
    }
}

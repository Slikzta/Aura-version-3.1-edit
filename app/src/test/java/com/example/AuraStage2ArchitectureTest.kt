package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aura.core.agent.AuraOrchestrator
import com.example.aura.core.agent.OrchestrationPhase
import com.example.aura.core.provider.ChatMessage
import com.example.aura.core.provider.CompletionRequest
import com.example.aura.core.provider.ConfigurableOfflineProvider
import com.example.aura.core.provider.MessageRole
import com.example.aura.core.provider.ModelProviderType
import com.example.aura.core.provider.NetworkModelProvider
import com.example.aura.core.provider.ProviderAuthState
import com.example.aura.core.provider.ProviderConfig
import com.example.aura.core.provider.ProviderError
import com.example.aura.core.provider.StreamChunk
import com.example.aura.core.provider.StreamingEngine
import com.example.aura.core.provider.StreamingState
import com.example.aura.core.security.ApprovalManager
import com.example.aura.core.security.ApprovalPolicy
import com.example.aura.core.security.ApprovalRequest
import com.example.aura.core.security.SecretSanitizer
import com.example.aura.core.security.SecurityLevel
import com.example.aura.core.session.ConversationSession
import com.example.aura.core.session.SessionMode
import com.example.aura.core.session.SessionState
import com.example.aura.core.tools.ToolParameter
import com.example.aura.core.tools.ToolSchema
import com.example.aura.core.voice.EnergyThresholdVAD
import com.example.aura.core.voice.VoiceEngineState
import com.example.aura.core.voice.VoiceInteractionManager
import com.example.aura.core.voice.VoiceMode
import com.example.aura.di.AuraContainer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraStage2ArchitectureTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testProviderAbstractionAndAuthState() = runTest {
        val container = AuraContainer(context)
        val registry = container.providerRegistry

        val active = registry.getActiveProvider()
        assertEquals(ProviderAuthState.Configured, active.authState)

        // Network provider with empty API key must report MissingCredentials
        val unconfiguredOpenAI = NetworkModelProvider(
            ProviderConfig(
                id = "test_openai",
                type = ModelProviderType.OPENAI,
                displayName = "Test OpenAI",
                endpointUrl = "https://api.openai.com/v1",
                apiKey = ""
            )
        )
        assertTrue(unconfiguredOpenAI.authState is ProviderAuthState.MissingCredentials)

        // Generating response with unconfigured credentials throws AuthenticationError without fake AI
        try {
            unconfiguredOpenAI.generateResponse(
                CompletionRequest(messages = listOf(ChatMessage(MessageRole.USER, "Hello")))
            )
            org.junit.Assert.fail("Expected ProviderError.AuthenticationError")
        } catch (e: ProviderError.AuthenticationError) {
            assertTrue(e.message.contains("not configured"))
        }
    }

    @Test
    fun testStreamingEngineLifecycle() = runTest {
        val streamingEngine = StreamingEngine()
        val provider = ConfigurableOfflineProvider()

        val request = CompletionRequest(
            messages = listOf(ChatMessage(MessageRole.USER, "Status check"))
        )

        val chunks = streamingEngine.stream(request, provider).toList()
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.any { it is StreamChunk.TextChunk })
        assertTrue(chunks.any { it is StreamChunk.DoneChunk })

        // Check final completed state
        val finalState = streamingEngine.state.value
        assertTrue(finalState is StreamingState.Completed)
        assertTrue((finalState as StreamingState.Completed).totalTokens > 0)
    }

    @Test
    fun testConversationSessionAndInterruption() {
        val session = ConversationSession(initialMode = SessionMode.INTERACTIVE_TEXT)
        assertEquals(SessionState.IDLE, session.state.value)

        session.appendStreamingToken("Hello world")
        assertEquals("Hello world", session.currentStreamingText.value)

        session.interrupt("User cancelled")
        assertEquals(SessionState.INTERRUPTED, session.state.value)

        session.setMode(SessionMode.VOICE_STREAM)
        assertEquals(SessionMode.VOICE_STREAM, session.mode.value)
    }

    @Test
    fun testToolRegistryAndInputSchemas() {
        val container = AuraContainer(context)
        val registry = container.toolRegistry

        val active = registry.getActiveTools()
        assertTrue(active.any { it.id == "web_access" })
        assertTrue(active.any { it.id == "notifications" })
        assertTrue(active.any { it.id == "device_actions" })
        assertTrue(active.any { it.id == "calendar" })
        assertTrue(active.any { it.id == "messaging" })
        assertTrue(active.any { it.id == "browser_automation" })

        // Schema validation testing
        val schema = ToolSchema(
            parameters = listOf(
                ToolParameter("target_url", "string", "URL", required = true),
                ToolParameter("depth", "integer", "Scan depth", required = false)
            )
        )
        assertTrue(schema.validateInput(mapOf("target_url" to "https://aura.local")).isSuccess)
        assertTrue(schema.validateInput(mapOf("depth" to 1)).isFailure)
    }

    @Test
    fun testSecurityApprovalBoundaries() = runTest {
        val approvalManager = ApprovalManager()
        assertEquals(ApprovalPolicy.STANDARD, approvalManager.currentPolicy.value)

        // LOW_RISK / SAFE does not require prompt in STANDARD policy
        assertFalse(approvalManager.requiresApproval(SecurityLevel.SAFE))
        assertFalse(approvalManager.requiresApproval(SecurityLevel.LOW_RISK))

        // MEDIUM_RISK and HIGH_RISK require prompt
        assertTrue(approvalManager.requiresApproval(SecurityLevel.MEDIUM_RISK))
        assertTrue(approvalManager.requiresApproval(SecurityLevel.HIGH_RISK))

        // Suspending approval flow test
        val request = ApprovalRequest(
            toolId = "messaging",
            toolName = "Messaging",
            actionSummary = "Send test SMS",
            parametersSummary = mapOf("phone" to "123"),
            riskLevel = SecurityLevel.HIGH_RISK
        )

        val job = launch {
            val approved = approvalManager.requestApproval(request)
            assertTrue(approved)
        }
        testScheduler.runCurrent()

        // Verify pending queue
        assertEquals(1, approvalManager.pendingRequests.value.size)
        assertEquals(request.id, approvalManager.pendingRequests.value.first().id)

        // Resolve request
        approvalManager.resolveRequest(request.id, approved = true)
        job.join()
        assertEquals(0, approvalManager.pendingRequests.value.size)
    }

    @Test
    fun testOrchestrationCycleTransitions() {
        val orchestrator = AuraOrchestrator()
        assertEquals(OrchestrationPhase.IDLE, orchestrator.state.value.currentPhase)

        orchestrator.transitionTo(OrchestrationPhase.UNDERSTAND, "Analyzing user prompt")
        assertEquals(OrchestrationPhase.UNDERSTAND, orchestrator.state.value.currentPhase)

        orchestrator.transitionTo(OrchestrationPhase.PLAN, "Formulating plan")
        assertEquals(OrchestrationPhase.PLAN, orchestrator.state.value.currentPhase)

        orchestrator.transitionTo(OrchestrationPhase.SELECT_TOOL, "Selected web_access", "web_access")
        assertEquals(OrchestrationPhase.SELECT_TOOL, orchestrator.state.value.currentPhase)
        assertEquals("web_access", orchestrator.state.value.activeToolName)

        orchestrator.transitionTo(OrchestrationPhase.CHECK_PERMISSION, "Checking permissions")
        assertEquals(OrchestrationPhase.CHECK_PERMISSION, orchestrator.state.value.currentPhase)

        orchestrator.transitionTo(OrchestrationPhase.REQUEST_APPROVAL_IF_REQUIRED, "Waiting user confirmation")
        assertTrue(orchestrator.state.value.isApprovalPending)

        orchestrator.transitionTo(OrchestrationPhase.EXECUTE, "Executing tool")
        assertEquals(OrchestrationPhase.EXECUTE, orchestrator.state.value.currentPhase)

        orchestrator.transitionTo(OrchestrationPhase.OBSERVE_RESULT, "Outcome parsed")
        orchestrator.recordObservation("HTTP 200 OK")
        assertEquals("HTTP 200 OK", orchestrator.state.value.lastObservation)

        orchestrator.transitionTo(OrchestrationPhase.UPDATE_STATE, "Writing session state")
        orchestrator.transitionTo(OrchestrationPhase.CONTINUE_OR_RESPOND, "Completed")
        assertEquals(OrchestrationPhase.CONTINUE_OR_RESPOND, orchestrator.state.value.currentPhase)
    }

    @Test
    fun testSecretSanitization() {
        val textWithApiKey = "Using model with key sk-123456789012345678901234 and Bearer my_secret_token_12345"
        val sanitized = SecretSanitizer.sanitize(textWithApiKey)

        assertFalse(sanitized.contains("sk-123456789012345678901234"))
        assertFalse(sanitized.contains("my_secret_token_12345"))
        assertTrue(sanitized.contains("[REDACTED_API_KEY]"))
        assertTrue(sanitized.contains("Bearer [REDACTED]"))

        val metadata = mapOf("api_key" to "secret123", "tool_name" to "web_access")
        val sanitizedMap = SecretSanitizer.sanitizeMap(metadata)
        assertEquals("[REDACTED]", sanitizedMap["api_key"])
        assertEquals("web_access", sanitizedMap["tool_name"])
    }

    @Test
    fun testVoiceActivityDetectionAndVoiceManager() {
        val vad = EnergyThresholdVAD()
        val silence = ShortArray(512) { 0 }
        assertFalse(vad.processFrame(silence, silence.size))

        val voiceManager = VoiceInteractionManager(context)
        assertEquals(VoiceMode.PUSH_TO_TALK, voiceManager.mode.value)
        assertEquals(VoiceEngineState.Idle, voiceManager.engineState.value)

        voiceManager.setMode(VoiceMode.CONTINUOUS_CONVERSATION)
        assertEquals(VoiceMode.CONTINUOUS_CONVERSATION, voiceManager.mode.value)
    }
}

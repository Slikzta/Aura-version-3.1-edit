package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aura.core.ChatMessage
import com.example.aura.core.CompletionRequest
import com.example.aura.core.CompletionResponse
import com.example.aura.core.MessageRole
import com.example.aura.core.ModelCapability
import com.example.aura.core.ModelExecutionLocation
import com.example.aura.core.ModelProvider
import com.example.aura.core.ModelProviderType
import com.example.aura.core.provider.ProviderAuthState
import com.example.aura.core.provider.ProviderError
import com.example.aura.core.provider.StreamChunk
import com.example.aura.core.agent.AgentState
import com.example.aura.core.agent.AuraAgent
import com.example.aura.core.agent.AuraOrchestrator
import com.example.aura.core.logging.AgentAuditLogger
import com.example.aura.core.provider.ProviderHealth
import com.example.aura.core.provider.StreamingEngine
import com.example.aura.core.security.ApprovalManager
import com.example.aura.core.session.ConversationSession
import com.example.aura.core.session.SessionManager
import com.example.aura.core.session.SessionMode
import com.example.aura.core.session.SessionState
import com.example.aura.core.session.StreamLifecycleCallbacks
import com.example.aura.core.session.StreamingConversationEngine
import com.example.aura.core.tools.ToolRegistry
import com.example.aura.data.database.AuraDatabase
import com.example.aura.data.repository.AuraRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraModelProviderWiringTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * A pure provider-neutral ModelProvider implementation for unit testing.
     * Implements only the com.example.aura.core.ModelProvider interface.
     */
    private class TestNeutralModelProvider(
        override val id: String = "test_neutral_provider",
        override val name: String = "Test Neutral Provider",
        override val type: ModelProviderType = ModelProviderType.DIAGNOSTIC_OFFLINE,
        override val authState: ProviderAuthState = ProviderAuthState.Configured,
        override val executionLocation: ModelExecutionLocation = ModelExecutionLocation.ON_DEVICE,
        override val capabilities: Set<ModelCapability> = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.STREAMING),
        private val chunksToEmit: List<StreamChunk> = listOf(
            StreamChunk.TextChunk("Verified "),
            StreamChunk.TextChunk("ModelProvider "),
            StreamChunk.TextChunk("Wiring."),
            StreamChunk.DoneChunk("stop")
        ),
        private val shouldThrowError: ProviderError? = null
    ) : ModelProvider {

        val streamInvocationCount = AtomicInteger(0)
        val lastReceivedRequest = java.util.concurrent.atomic.AtomicReference<CompletionRequest?>()

        override suspend fun generateResponse(request: CompletionRequest): CompletionResponse {
            lastReceivedRequest.set(request)
            if (shouldThrowError != null) throw shouldThrowError
            return CompletionResponse(
                message = ChatMessage(MessageRole.ASSISTANT, "One-shot response"),
                finishReason = "stop",
                providerId = id
            )
        }

        override fun streamResponse(request: CompletionRequest): Flow<StreamChunk> = flow {
            streamInvocationCount.incrementAndGet()
            lastReceivedRequest.set(request)
            if (shouldThrowError != null) {
                throw shouldThrowError
            }
            for (chunk in chunksToEmit) {
                emit(chunk)
            }
        }

        override suspend fun checkHealth(): ProviderHealth = ProviderHealth.Healthy
    }

    @Test
    fun testConversationEngineAcceptsModelProviderDirectly() {
        val testProvider = TestNeutralModelProvider()
        val repository = AuraRepository(AuraDatabase.getInstance(context))
        val toolRegistry = ToolRegistry()
        val approvalManager = ApprovalManager()
        val auditLogger = AgentAuditLogger()
        val sessionManager = SessionManager()

        // Inject the provider-neutral ModelProvider interface directly into AuraAgent
        val agent = AuraAgent(
            appContext = context,
            repository = repository,
            modelProvider = testProvider,
            toolRegistry = toolRegistry,
            approvalManager = approvalManager,
            auditLogger = auditLogger,
            sessionManager = sessionManager
        )

        assertNotNull(agent)
        assertEquals("test_neutral_provider", agent.modelProvider.id)
        assertEquals("Test Neutral Provider", agent.modelProvider.name)
        assertEquals(ModelExecutionLocation.ON_DEVICE, agent.modelProvider.executionLocation)

        // Verify dynamic update via updateModelProvider
        val alternateProvider = TestNeutralModelProvider(id = "alternate_id", name = "Alternate Provider")
        agent.updateModelProvider(alternateProvider)
        assertEquals("alternate_id", agent.modelProvider.id)
    }

    @Test
    fun testProviderRequestsAreRoutedThroughInterfaceAndStreamingPropagates() = runTest {
        val testProvider = TestNeutralModelProvider()
        val repository = AuraRepository(AuraDatabase.getInstance(context))
        val sessionManager = SessionManager()
        val session = sessionManager.activeSession.value

        val agent = AuraAgent(
            appContext = context,
            repository = repository,
            modelProvider = testProvider,
            toolRegistry = ToolRegistry(),
            approvalManager = ApprovalManager(),
            auditLogger = AgentAuditLogger(),
            sessionManager = sessionManager
        )

        agent.processUserInput(session.id, "Hello Aura through ModelProvider")
        testScheduler.runCurrent()

        // Verify request was routed through the ModelProvider interface
        assertEquals(1, testProvider.streamInvocationCount.get())
        val receivedRequest = testProvider.lastReceivedRequest.get()
        assertNotNull(receivedRequest)
        assertTrue(receivedRequest!!.messages.any { it.content == "Hello Aura through ModelProvider" })

        // Verify streaming tokens accumulated into session current text
        assertEquals("Verified ModelProvider Wiring.", session.currentStreamingText.value)
    }

    @Test
    fun testStreamingConversationEngineLifecycleWithModelProvider() = runTest {
        val engine = StreamingConversationEngine()
        val testProvider = TestNeutralModelProvider()
        val request = CompletionRequest(
            messages = listOf(ChatMessage(MessageRole.USER, "Test lifecycle"))
        )

        val onStartFired = AtomicBoolean(false)
        val chunksReceived = mutableListOf<String>()
        val onCompleteFired = AtomicBoolean(false)

        val job = engine.startStreamingTurn(
            scope = this,
            sessionId = "test_lifecycle_session",
            request = request,
            provider = testProvider,
            callbacks = StreamLifecycleCallbacks(
                onStart = { _, _ -> onStartFired.set(true) },
                onChunk = { _, token, _ -> chunksReceived.add(token) },
                onComplete = { _, _, _, _ -> onCompleteFired.set(true) }
            )
        )

        job.join()

        assertTrue(onStartFired.get())
        assertEquals(3, chunksReceived.size)
        assertEquals("Verified ", chunksReceived[0])
        assertEquals("ModelProvider ", chunksReceived[1])
        assertEquals("Wiring.", chunksReceived[2])
        assertTrue(onCompleteFired.get())
    }

    @Test
    fun testProviderErrorsPropagateCorrectly() = runTest {
        val failingProvider = TestNeutralModelProvider(
            shouldThrowError = ProviderError.NetworkTimeoutError("Connection timed out", 15000L)
        )
        val repository = AuraRepository(AuraDatabase.getInstance(context))
        val sessionManager = SessionManager()
        val session = sessionManager.activeSession.value

        val agent = AuraAgent(
            appContext = context,
            repository = repository,
            modelProvider = failingProvider,
            toolRegistry = ToolRegistry(),
            approvalManager = ApprovalManager(),
            auditLogger = AgentAuditLogger(),
            sessionManager = sessionManager
        )

        agent.processUserInput(session.id, "Will fail")
        testScheduler.runCurrent()

        // Verify error state transition
        assertEquals(AgentState.ERROR, agent.agentState.value)
        assertEquals(SessionState.ERROR, session.state.value)
    }

    @Test
    fun testCancellationAndInterruptionPropagateCorrectly() = runTest {
        val testProvider = TestNeutralModelProvider()
        val repository = AuraRepository(AuraDatabase.getInstance(context))
        val sessionManager = SessionManager()
        val session = sessionManager.activeSession.value

        val agent = AuraAgent(
            appContext = context,
            repository = repository,
            modelProvider = testProvider,
            toolRegistry = ToolRegistry(),
            approvalManager = ApprovalManager(),
            auditLogger = AgentAuditLogger(),
            sessionManager = sessionManager
        )

        // Trigger message and interrupt immediately
        agent.processUserInput(session.id, "Long running stream")
        agent.interrupt()
        testScheduler.runCurrent()

        assertEquals(AgentState.INTERRUPTED, agent.agentState.value)
        assertEquals(SessionState.INTERRUPTED, session.state.value)
    }
}

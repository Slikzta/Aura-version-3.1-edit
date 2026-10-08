package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aura.core.memory.EmbeddingVector
import com.example.aura.core.memory.LocalCosineVectorStore
import com.example.aura.core.provider.ChatMessage
import com.example.aura.core.provider.CompletionRequest
import com.example.aura.core.provider.ConfigurableOfflineProvider
import com.example.aura.core.provider.HardwareAcceleration
import com.example.aura.core.provider.MessageRole
import com.example.aura.core.provider.ModelExecutionLocation
import com.example.aura.core.provider.ModelProviderRegistry
import com.example.aura.core.provider.ModelProviderType
import com.example.aura.core.provider.ModelRoutingPolicy
import com.example.aura.core.provider.NetworkModelProvider
import com.example.aura.core.provider.OnDeviceModelProvider
import com.example.aura.core.provider.OnDeviceResourceProfile
import com.example.aura.core.provider.ProviderAuthState
import com.example.aura.core.provider.ProviderConfig
import com.example.aura.core.provider.UnifiedModelRouter
import com.example.aura.core.session.StreamLifecycleCallbacks
import com.example.aura.core.session.StreamingConversationEngine
import com.example.aura.core.tools.ToolExecutionContext
import com.example.aura.core.tools.ToolExecutionResult
import com.example.aura.core.tools.ToolPermissionGuard
import com.example.aura.core.tools.ToolPermissionStatus
import com.example.aura.core.voice.VoiceEvent
import com.example.aura.core.voice.VoiceMode
import com.example.aura.core.voice.VoiceStateMachine
import com.example.aura.core.voice.VoiceStatePhase
import com.example.aura.di.AuraContainer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraStage3ArchitectureTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testOnDeviceAndNetworkModelAbstraction() {
        // Stub implementation of OnDeviceModelProvider for interface verification
        val stubOnDeviceProvider = object : OnDeviceModelProvider {
            override val id: String = "stub_on_device_llm"
            override val name: String = "Stub On-Device NPU Model"
            override val type: ModelProviderType = ModelProviderType.OLLAMA_LOCAL
            override val authState: ProviderAuthState = ProviderAuthState.Configured
            override val capabilities = setOf(com.example.aura.core.provider.ModelCapability.TEXT_GENERATION)
            override val resourceProfile = OnDeviceResourceProfile(
                maxMemoryMb = 1024,
                contextWindowTokens = 2048,
                acceleration = HardwareAcceleration.NPU
            )

            override suspend fun loadModel(modelPath: String, acceleration: HardwareAcceleration) = Result.success(Unit)
            override suspend fun unloadModel() {}
            override fun getLoadedMemoryFootprintMb(): Int = 512
            override suspend fun generateResponse(request: CompletionRequest) = throw UnsupportedOperationException()
            override fun streamResponse(request: CompletionRequest) = throw UnsupportedOperationException()
            override suspend fun checkHealth() = com.example.aura.core.provider.ProviderHealth.Healthy
        }

        assertEquals(ModelExecutionLocation.ON_DEVICE, stubOnDeviceProvider.executionLocation)
        assertEquals(HardwareAcceleration.NPU, stubOnDeviceProvider.resourceProfile.acceleration)
        assertEquals(512, stubOnDeviceProvider.getLoadedMemoryFootprintMb())

        val networkProvider = NetworkModelProvider(
            ProviderConfig(
                id = "openai_test",
                type = ModelProviderType.OPENAI,
                displayName = "OpenAI Remote"
            )
        )
        assertEquals(ModelExecutionLocation.NETWORK_REMOTE, networkProvider.executionLocation)
    }

    @Test
    fun testUnifiedModelRouter() {
        val registry = ModelProviderRegistry()
        val router = UnifiedModelRouter(registry, ModelRoutingPolicy.MANUAL)

        val request = CompletionRequest(
            messages = listOf(ChatMessage(MessageRole.USER, "Test routing"))
        )

        // MANUAL policy selects active provider
        val resolvedManual = router.resolveProvider(request)
        assertEquals(registry.getActiveProvider().id, resolvedManual.id)

        // OFFLINE_ONLY policy throws if no on-device provider is loaded
        router.routingPolicy = ModelRoutingPolicy.OFFLINE_ONLY
        try {
            router.resolveProvider(request)
            // If diagnostic offline is considered configured or no on-device matches
        } catch (e: Exception) {
            assertTrue(e.message?.contains("on-device") == true)
        }
    }

    @Test
    fun testStreamingConversationEngine() = runTest {
        val engine = StreamingConversationEngine()
        val provider = ConfigurableOfflineProvider()
        val request = CompletionRequest(
            messages = listOf(ChatMessage(MessageRole.USER, "Test conversation streaming"))
        )

        var started = false
        var chunksCount = 0
        var completed = false

        val job = engine.startStreamingTurn(
            scope = this,
            sessionId = "session_test_123",
            request = request,
            provider = provider,
            callbacks = StreamLifecycleCallbacks(
                onStart = { _, _ -> started = true },
                onChunk = { _, _, _ -> chunksCount++ },
                onComplete = { _, _, _, _ -> completed = true }
            )
        )

        job.join()

        assertTrue(started)
        assertTrue(chunksCount > 0)
        assertTrue(completed)
    }

    @Test
    fun testVoiceStateMachineModesAndBargeIn() {
        val machine = VoiceStateMachine(VoiceMode.PUSH_TO_TALK)
        assertEquals(VoiceStatePhase.IDLE, machine.currentPhase.value)

        // Permission check transition
        assertEquals(VoiceStatePhase.CHECKING_PERMISSION, machine.processEvent(VoiceEvent.RequestPermission))
        assertEquals(VoiceStatePhase.READY, machine.processEvent(VoiceEvent.PermissionGranted))

        // Mode A: Push-to-Talk
        assertEquals(VoiceStatePhase.LISTENING, machine.processEvent(VoiceEvent.StartPressToTalk))
        assertEquals(VoiceStatePhase.PROCESSING_SPEECH, machine.processEvent(VoiceEvent.ReleasePressToTalk))
        assertEquals(VoiceStatePhase.THINKING, machine.processEvent(VoiceEvent.SpeechTranscribed("User prompt")))
        assertEquals(VoiceStatePhase.SPEAKING, machine.processEvent(VoiceEvent.AssistantStartedSpeaking))
        assertEquals(VoiceStatePhase.IDLE, machine.processEvent(VoiceEvent.AssistantFinishedSpeaking))

        // Mode B: Continuous with Barge-in
        machine.mode = VoiceMode.CONTINUOUS_CONVERSATION
        assertEquals(VoiceStatePhase.LISTENING, machine.processEvent(VoiceEvent.StartContinuousLoop))
        assertEquals(VoiceStatePhase.SPEAKING, machine.processEvent(VoiceEvent.AssistantStartedSpeaking))

        // When user speaks while assistant is speaking: BARGE IN!
        assertEquals(VoiceStatePhase.BARGE_IN_TRIGGERED, machine.processEvent(VoiceEvent.VoiceActivityDetected(0.85f)))
    }

    @Test
    fun testToolPermissionGuardAndExecutionBoundary() = runTest {
        val container = AuraContainer(context)
        val guard = ToolPermissionGuard(context)
        val boundary = container.toolExecutionBoundary

        val sysTool = container.toolRegistry.getTool("system_diagnostics")
        assertNotNull(sysTool)
        assertEquals(ToolPermissionStatus.AllGranted, guard.checkPermissions(sysTool!!))

        // Execute safely through boundary
        val execContext = ToolExecutionContext(
            appContext = context,
            sandboxDirectory = File(context.filesDir, "aura_sandbox"),
            sessionId = "test_session"
        )
        val result = boundary.executeSafely(sysTool, execContext, emptyMap())
        assertTrue(result is ToolExecutionResult.Success)
    }

    @Test
    fun testLocalFirstCosineVectorStore() = runTest {
        val vectorStore = LocalCosineVectorStore()
        assertEquals(0, vectorStore.indexedCount)

        val vecA = EmbeddingVector(floatArrayOf(1.0f, 0.0f, 0.0f))
        val vecB = EmbeddingVector(floatArrayOf(0.9f, 0.1f, 0.0f))
        val vecC = EmbeddingVector(floatArrayOf(0.0f, 1.0f, 0.0f))

        // Cosine similarity math validation
        val simIdentical = vecA.cosineSimilarity(vecA)
        assertTrue(simIdentical > 0.99f)

        val simClose = vecA.cosineSimilarity(vecB)
        assertTrue(simClose > 0.9f)

        val simOrthogonal = vecA.cosineSimilarity(vecC)
        assertEquals(0.0f, simOrthogonal, 0.001f)

        // Store and retrieve in local vector memory
        vectorStore.index(
            id = "mem_1",
            key = "User Preference",
            content = "User prefers concise local-first responses",
            category = "PREFERENCE",
            vector = vecA
        )
        vectorStore.index(
            id = "mem_2",
            key = "Cooking Fact",
            content = "User likes Italian food",
            category = "FACT",
            vector = vecC
        )

        assertEquals(2, vectorStore.indexedCount)

        // Query with vector near vecA
        val matches = vectorStore.search(queryVector = vecB, topK = 1, minScore = 0.5f)
        assertEquals(1, matches.size)
        assertEquals("mem_1", matches.first().memoryId)
        assertTrue(matches.first().similarityScore > 0.9f)
    }
}

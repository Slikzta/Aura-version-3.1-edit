package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aura.core.provider.ChatMessage
import com.example.aura.core.provider.MessageRole
import com.example.aura.core.provider.ModelProviderType
import com.example.aura.core.provider.NetworkModelProvider
import com.example.aura.core.provider.ProviderConfig
import com.example.aura.core.session.SessionMode
import com.example.aura.core.tools.WebAccessTool
import com.example.aura.core.voice.EnergyThresholdVAD
import com.example.aura.core.voice.VoiceEngineState
import com.example.aura.core.voice.VoiceInteractionManager
import com.example.aura.core.voice.VoiceMode
import com.example.aura.di.AuraContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraLiveModelToolLoopTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var container: AuraContainer
    private lateinit var fakeStt: FakeSpeechToTextEngine
    private lateinit var fakeTts: FakeTextToSpeechEngine
    private lateinit var voiceManager: VoiceInteractionManager

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)

        fakeStt = FakeSpeechToTextEngine()
        fakeTts = FakeTextToSpeechEngine()

        voiceManager = VoiceInteractionManager(
            context = context,
            sttEngine = fakeStt,
            ttsEngine = fakeTts,
            vad = EnergyThresholdVAD()
        )

        container = AuraContainer(context, customVoiceManager = voiceManager)
        AuraContainer.setInstance(container)
    }

    /**
     * Complete intended end-to-end integration flow:
     * Voice input → Aura agent → live OpenAI model → model requests web_access tool
     * → Aura executes web_access tool → result returned to model
     * → final model response → TextToSpeech → return to listening.
     */
    @Test
    fun testCompleteVoiceToModelToToolToFinalSpeechLoop() = runTest {
        // Wire voice manager callbacks to container agent
        voiceManager.setCallbacks(
            onUserInput = { text ->
                val activeSession = container.sessionManager.activeSession.value
                activeSession.setMode(SessionMode.VOICE_STREAM)
                container.agent.processUserInput(activeSession.id, text)
            },
            onBargeIn = {
                container.agent.interrupt()
            }
        )

        // Mock WebAccessTool HTTP client returning mock web content
        val webMockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("<html><body>System status: ALL_SYSTEMS_OPERATIONAL</body></html>".toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()
        container.toolRegistry.registerTool(WebAccessTool(webMockClient), enabledByDefault = true)

        val modelRequestCount = AtomicInteger(0)
        var capturedSecondRequestPayload: String? = null

        // Mock OpenAI-compatible HTTP client that handles the 2-turn tool calling loop
        val liveModelHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val count = modelRequestCount.incrementAndGet()

                // Verify Bearer authorization header is passed
                val authHeader = request.header("Authorization")
                assertEquals("Bearer sk-test-live-key", authHeader)

                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                val bodyStr = buffer.readUtf8()

                if (count == 1) {
                    // Turn 1: Model requests the web_access tool
                    val sseTurn1 = """
                        data: {"id":"call_turn_1","choices":[{"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call_web_123","type":"function","function":{"name":"web_access","arguments":"{\"url\":\"https://example.com/health\"}"}}]}}]}

                        data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                        data: [DONE]

                    """.trimIndent()

                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(sseTurn1.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                } else {
                    // Turn 2: Capture body to verify tool result was returned to model
                    capturedSecondRequestPayload = bodyStr

                    val sseTurn2 = """
                        data: {"id":"turn_2_final","choices":[{"delta":{"content":"The website health check confirmed all systems are operational."}}]}

                        data: {"choices":[{"delta":{},"finish_reason":"stop"}]}{

                        data: [DONE]

                    """.trimIndent()

                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(sseTurn2.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                }
            }
            .build()

        val liveConfig = ProviderConfig(
            id = "openai_provider",
            type = ModelProviderType.OPENAI,
            displayName = "OpenAI",
            endpointUrl = "https://api.openai.com/v1",
            apiKey = "sk-test-live-key",
            defaultModel = "gpt-4o-mini"
        )
        val liveProvider = NetworkModelProvider(liveConfig, liveModelHttpClient)
        container.providerRegistry.registerProvider(liveProvider)
        container.providerRegistry.setActiveProvider("openai_provider")

        // 1. Activate Continuous Chat
        voiceManager.startContinuousConversation()
        assertEquals(VoiceMode.CONTINUOUS_CONVERSATION, voiceManager.mode.value)
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)
        assertTrue(fakeStt.isListening.value)

        // 2. User speaks query
        fakeStt.emitFinalText("Please check the website health at https://example.com/health")

        // 3. Agent transitions to Thinking
        assertEquals(VoiceEngineState.Thinking, voiceManager.engineState.value)

        // 4. Wait for agent to process: Turn 1 (tool call) -> Tool execution -> Turn 2 (final model response)
        for (i in 1..50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (fakeTts.lastSpokenText != null) break
            Thread.sleep(40)
        }

        // Verify model was called twice (tool request then final response)
        assertEquals(2, modelRequestCount.get())

        // Verify tool result was sent back in Turn 2 payload
        assertNotNull(capturedSecondRequestPayload)
        val turn2Json = JSONObject(capturedSecondRequestPayload!!)
        val messages = turn2Json.getJSONArray("messages")
        var foundToolMessage = false
        for (i in 0 until messages.length()) {
            val msg = messages.getJSONObject(i)
            if (msg.optString("role") == "tool") {
                assertEquals("call_web_123", msg.optString("tool_call_id"))
                assertTrue(msg.optString("content").contains("ALL_SYSTEMS_OPERATIONAL"))
                foundToolMessage = true
            }
        }
        assertTrue("Tool response must be present in follow-up request", foundToolMessage)

        // 5. Final spoken response reaches TextToSpeech
        assertNotNull(fakeTts.lastSpokenText)
        assertTrue(fakeTts.lastSpokenText!!.contains("all systems are operational"))
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Speaking)

        // 6. When TTS finishes, Continuous Chat returns to listening
        fakeTts.completeSpeaking()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testScheduler.runCurrent()

        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)
        assertTrue(fakeStt.isListening.value)
    }

    /**
     * Requirement 9: Error handling when API key is missing / UNCONFIGURED
     */
    @Test
    fun testMissingApiKeyInContinuousChatVoicesAuthenticationError() = runTest {
        voiceManager.setCallbacks(
            onUserInput = { text ->
                val activeSession = container.sessionManager.activeSession.value
                activeSession.setMode(SessionMode.VOICE_STREAM)
                container.agent.processUserInput(activeSession.id, text)
            },
            onBargeIn = {
                container.agent.interrupt()
            }
        )

        val unconfiguredConfig = ProviderConfig(
            id = "openai_provider",
            type = ModelProviderType.OPENAI,
            displayName = "OpenAI",
            endpointUrl = "https://api.openai.com/v1",
            apiKey = "UNCONFIGURED"
        )
        val unconfiguredProvider = NetworkModelProvider(unconfiguredConfig)
        container.providerRegistry.registerProvider(unconfiguredProvider)
        container.providerRegistry.setActiveProvider("openai_provider")

        voiceManager.startContinuousConversation()
        fakeStt.emitFinalText("Hello Aura")

        for (i in 1..40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (fakeTts.lastSpokenText != null) break
            Thread.sleep(40)
        }

        assertNotNull(fakeTts.lastSpokenText)
        assertTrue(fakeTts.lastSpokenText!!.contains("Model authentication error"))
        assertTrue(fakeTts.lastSpokenText!!.contains("API key is not configured"))

        fakeTts.completeSpeaking()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testScheduler.runCurrent()

        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)
        assertTrue(fakeStt.isListening.value)
    }

    /**
     * Requirement 9: Error handling when tool arguments JSON is malformed
     */
    @Test
    fun testMalformedToolCallArgumentsRecoversGracefully() = runTest {
        var turn2Payload: String? = null
        val modelRequestCount = AtomicInteger(0)

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val count = modelRequestCount.incrementAndGet()
                val req = chain.request()
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                val bodyStr = buffer.readUtf8()

                if (count == 1) {
                    // Emit malformed arguments JSON
                    val sse = """
                        data: {"id":"malformed_call","choices":[{"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call_malformed_1","type":"function","function":{"name":"web_access","arguments":"{\"url\": invalid_json"}}]}}]}

                        data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                        data: [DONE]

                    """.trimIndent()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(sse.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                } else {
                    turn2Payload = bodyStr
                    val sse = """
                        data: {"id":"final_turn","choices":[{"delta":{"content":"I encountered a syntax error in the tool parameters."}}]}

                        data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                        data: [DONE]

                    """.trimIndent()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(sse.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                }
            }
            .build()

        val provider = NetworkModelProvider(
            ProviderConfig(
                id = "openai_provider",
                type = ModelProviderType.OPENAI,
                displayName = "OpenAI",
                endpointUrl = "https://api.openai.com/v1",
                apiKey = "sk-valid-key"
            ),
            mockClient
        )
        container.providerRegistry.registerProvider(provider)
        container.providerRegistry.setActiveProvider("openai_provider")

        voiceManager.setCallbacks(
            onUserInput = { text ->
                val activeSession = container.sessionManager.activeSession.value
                activeSession.setMode(SessionMode.VOICE_STREAM)
                container.agent.processUserInput(activeSession.id, text)
            },
            onBargeIn = { container.agent.interrupt() }
        )

        voiceManager.startContinuousConversation()
        fakeStt.emitFinalText("Check invalid json")

        for (i in 1..40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (fakeTts.lastSpokenText != null) break
            Thread.sleep(40)
        }

        assertEquals(2, modelRequestCount.get())
        assertNotNull(turn2Payload)
        assertTrue(turn2Payload!!.contains("Malformed arguments JSON"))
        assertNotNull(fakeTts.lastSpokenText)
        assertTrue(fakeTts.lastSpokenText!!.contains("syntax error in the tool parameters"))
    }

    /**
     * Requirement 9: Error handling when model calls an unrecognized tool
     */
    @Test
    fun testUnrecognizedToolSendsErrorMessageBackToModel() = runTest {
        var turn2Payload: String? = null
        val modelRequestCount = AtomicInteger(0)

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val count = modelRequestCount.incrementAndGet()
                val req = chain.request()
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                val bodyStr = buffer.readUtf8()

                if (count == 1) {
                    val sse = """
                        data: {"id":"unknown_call","choices":[{"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call_unk_1","type":"function","function":{"name":"non_existent_tool_xyz","arguments":"{}"}}]}}]}

                        data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                        data: [DONE]

                    """.trimIndent()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(sse.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                } else {
                    turn2Payload = bodyStr
                    val sse = """
                        data: {"id":"final_turn","choices":[{"delta":{"content":"That tool is not available on this system."}}]}

                        data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                        data: [DONE]

                    """.trimIndent()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(sse.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                }
            }
            .build()

        val provider = NetworkModelProvider(
            ProviderConfig(
                id = "openai_provider",
                type = ModelProviderType.OPENAI,
                displayName = "OpenAI",
                endpointUrl = "https://api.openai.com/v1",
                apiKey = "sk-valid-key"
            ),
            mockClient
        )
        container.providerRegistry.registerProvider(provider)
        container.providerRegistry.setActiveProvider("openai_provider")

        voiceManager.setCallbacks(
            onUserInput = { text ->
                val activeSession = container.sessionManager.activeSession.value
                activeSession.setMode(SessionMode.VOICE_STREAM)
                container.agent.processUserInput(activeSession.id, text)
            },
            onBargeIn = { container.agent.interrupt() }
        )

        voiceManager.startContinuousConversation()
        fakeStt.emitFinalText("Run unknown tool")

        for (i in 1..40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (fakeTts.lastSpokenText != null) break
            Thread.sleep(40)
        }

        assertEquals(2, modelRequestCount.get())
        assertNotNull(turn2Payload)
        assertTrue(turn2Payload!!.contains("is not recognized or available"))
        assertNotNull(fakeTts.lastSpokenText)
        assertTrue(fakeTts.lastSpokenText!!.contains("That tool is not available"))
    }

    /**
     * Requirement 9: Error handling on network connection failure
     */
    @Test
    fun testNetworkFailureHandledGracefullyAndSpeaksError() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { _ ->
                throw IOException("Unable to resolve host api.openai.com: No address associated with hostname")
            }
            .build()

        val provider = NetworkModelProvider(
            ProviderConfig(
                id = "openai_provider",
                type = ModelProviderType.OPENAI,
                displayName = "OpenAI",
                endpointUrl = "https://api.openai.com/v1",
                apiKey = "sk-valid-key"
            ),
            mockClient
        )
        container.providerRegistry.registerProvider(provider)
        container.providerRegistry.setActiveProvider("openai_provider")

        voiceManager.setCallbacks(
            onUserInput = { text ->
                val activeSession = container.sessionManager.activeSession.value
                activeSession.setMode(SessionMode.VOICE_STREAM)
                container.agent.processUserInput(activeSession.id, text)
            },
            onBargeIn = { container.agent.interrupt() }
        )

        voiceManager.startContinuousConversation()
        fakeStt.emitFinalText("Query live model")

        for (i in 1..40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (fakeTts.lastSpokenText != null) break
            Thread.sleep(40)
        }

        assertNotNull(fakeTts.lastSpokenText)
        assertTrue(fakeTts.lastSpokenText!!.contains("Model provider unavailable"))

        fakeTts.completeSpeaking()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testScheduler.runCurrent()

        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)
        assertTrue(fakeStt.isListening.value)
    }
}

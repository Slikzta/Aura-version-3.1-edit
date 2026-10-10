package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aura.core.agent.AgentState
import com.example.aura.core.agent.AuraAgent
import com.example.aura.core.session.SessionMode
import com.example.aura.core.voice.EnergyThresholdVAD
import com.example.aura.core.voice.SpeechSynthesisRequest
import com.example.aura.core.voice.SpeechToTextEngine
import com.example.aura.core.voice.TextToSpeechEngine
import com.example.aura.core.voice.TranscriptionResult
import com.example.aura.core.voice.VoiceEngineState
import com.example.aura.core.voice.VoiceInteractionManager
import com.example.aura.core.voice.VoiceMode
import com.example.aura.di.AuraContainer
import com.example.aura.ui.AuraMainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class FakeSpeechToTextEngine : SpeechToTextEngine {
    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    override val isAvailable: Boolean = true

    var onPartialTranscription: ((String) -> Unit)? = null
    var onFinalTranscription: ((TranscriptionResult) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    override fun startListening(
        onPartialTranscription: (String) -> Unit,
        onFinalTranscription: (TranscriptionResult) -> Unit,
        onError: (String) -> Unit
    ) {
        this.onPartialTranscription = onPartialTranscription
        this.onFinalTranscription = onFinalTranscription
        this.onError = onError
        _isListening.value = true
    }

    override fun stopListening() {
        _isListening.value = false
    }

    override fun destroy() {
        stopListening()
    }

    fun emitFinalText(text: String) {
        onFinalTranscription?.invoke(TranscriptionResult(text = text, isFinal = true))
    }

    fun emitError(errorMsg: String) {
        _isListening.value = false
        onError?.invoke(errorMsg)
    }
}

class FakeTextToSpeechEngine : TextToSpeechEngine {
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    var lastSpokenText: String? = null
    var speakCount = 0
    var onStartCallback: (() -> Unit)? = null
    var onDoneCallback: (() -> Unit)? = null

    override fun speak(
        request: SpeechSynthesisRequest,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        lastSpokenText = request.text
        speakCount++
        _isSpeaking.value = true
        onStartCallback = onStart
        onDoneCallback = onDone
        onStart()
    }

    fun completeSpeaking() {
        _isSpeaking.value = false
        onDoneCallback?.invoke()
    }

    override fun stop() {
        _isSpeaking.value = false
    }

    override fun shutdown() {
        stop()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraContinuousChatTest {

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

    @Test
    fun testContinuousChatActivationStartsListening() {
        voiceManager.startContinuousConversation()

        assertEquals(VoiceMode.CONTINUOUS_CONVERSATION, voiceManager.mode.value)
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)
        assertTrue(fakeStt.isListening.value)
    }

    @Test
    fun testContinuousChatSpeechReachesAgentAndSpeaksResponse() = runTest {
        // Wire callbacks to container agent
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

        voiceManager.startContinuousConversation()
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)

        // User speaks
        fakeStt.emitFinalText("Report status of Aura")

        // State transitions to Thinking
        assertEquals(VoiceEngineState.Thinking, voiceManager.engineState.value)

        // Wait for agent to process and generate model response
        for (i in 1..40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (fakeTts.lastSpokenText != null) break
            Thread.sleep(50)
        }

        // Response must reach the voice/TTS engine
        assertNotNull(fakeTts.lastSpokenText)
        assertTrue(fakeTts.lastSpokenText!!.contains("Aura Architecture"))
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Speaking)

        // When TTS completes, Continuous Chat returns to listening
        fakeTts.completeSpeaking()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testScheduler.runCurrent()

        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)
        assertTrue(fakeStt.isListening.value)
    }

    @Test
    fun testRepeatedContinuousChatInteractions() = runTest {
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

        voiceManager.startContinuousConversation()

        // First utterance
        fakeStt.emitFinalText("First question")
        for (i in 1..40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (fakeTts.speakCount == 1) break
            Thread.sleep(50)
        }
        assertEquals(1, fakeTts.speakCount)

        // Complete first speech response
        fakeTts.completeSpeaking()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testScheduler.runCurrent()
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)

        // Second utterance
        fakeStt.emitFinalText("Second question")
        for (i in 1..40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            testScheduler.runCurrent()
            if (fakeTts.speakCount == 2) break
            Thread.sleep(50)
        }
        assertEquals(2, fakeTts.speakCount)

        // Complete second speech response
        fakeTts.completeSpeaking()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testScheduler.runCurrent()
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)
    }

    @Test
    fun testRecoverableErrorRestartsListeningCycle() = runTest {
        voiceManager.startContinuousConversation()
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)

        // Simulate speech timeout / no match
        fakeStt.emitError("Speech input timed out")

        for (i in 1..25) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            testScheduler.runCurrent()
            if (fakeStt.isListening.value && voiceManager.engineState.value is VoiceEngineState.Listening) break
            Thread.sleep(50)
        }

        // Must still be in continuous mode and listening
        assertEquals(VoiceMode.CONTINUOUS_CONVERSATION, voiceManager.mode.value)
        assertTrue(fakeStt.isListening.value)
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)
    }

    @Test
    fun testStopVoiceInteractionCancelsOperationCleanly() {
        voiceManager.startContinuousConversation()
        assertTrue(fakeStt.isListening.value)

        voiceManager.stopVoiceInteraction()

        assertEquals(VoiceEngineState.Idle, voiceManager.engineState.value)
        assertFalse(fakeStt.isListening.value)
        assertFalse(fakeTts.isSpeaking.value)
    }

    @Test
    fun testViewModelContinuousConversationIntegration() {
        val viewModel = AuraMainViewModel(container)

        // Verify startContinuousConversation activates VOICE_STREAM mode
        viewModel.startContinuousConversation()
        assertEquals(SessionMode.VOICE_STREAM, viewModel.activeSession.value.mode.value)
        assertEquals(VoiceMode.CONTINUOUS_CONVERSATION, viewModel.voiceMode.value)

        // Stop cancels cleanly
        viewModel.stopVoiceInteraction()
        assertEquals(VoiceEngineState.Idle, viewModel.voiceEngineState.value)
    }

    @Test
    fun testBargeInInterruptionHaltsSpeakingAndRestartsListening() = runTest {
        voiceManager.startContinuousConversation()
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)

        // Synthesize and speak
        voiceManager.speakResponse("This is Aura speaking a long explanation")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testScheduler.runCurrent()

        assertTrue(fakeTts.isSpeaking.value)
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Speaking)

        // User speaks while Aura is speaking (Barge-in)
        voiceManager.handleBargeIn()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testScheduler.runCurrent()

        // TTS must halt immediately
        assertFalse(fakeTts.isSpeaking.value)
        assertTrue(voiceManager.engineState.value is VoiceEngineState.BargeInInterrupted)

        // Continuous mode must recover and resume listening
        for (i in 1..20) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            testScheduler.runCurrent()
            if (voiceManager.engineState.value is VoiceEngineState.Listening) break
            Thread.sleep(25)
        }
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)
        assertTrue(fakeStt.isListening.value)
    }

    @Test
    fun testTtsSpeakingPreventsOverlappingMicrophoneCapture() {
        voiceManager.startContinuousConversation()
        assertTrue(fakeStt.isListening.value)

        // Speak response stops listening and captures exclusively for TTS
        voiceManager.speakResponse("Aura voice output")
        assertFalse(fakeStt.isListening.value)

        // Direct call to resume while speaking is ignored to avoid audio feedback
        voiceManager.resumeContinuousListening()
        assertFalse(fakeStt.isListening.value)
    }

    @Test
    fun testLifecycleCancellationAndCleanRestart() {
        voiceManager.startContinuousConversation()
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)

        // Simulate activity pause/destroy or assistant dismissal
        voiceManager.destroy()
        assertEquals(VoiceEngineState.Idle, voiceManager.engineState.value)
        assertFalse(fakeStt.isListening.value)
        assertFalse(fakeTts.isSpeaking.value)

        // User re-opens app or activates continuous conversation again
        voiceManager.startContinuousConversation()
        assertEquals(VoiceMode.CONTINUOUS_CONVERSATION, voiceManager.mode.value)
        assertTrue(voiceManager.engineState.value is VoiceEngineState.Listening)
        assertTrue(fakeStt.isListening.value)
    }
}

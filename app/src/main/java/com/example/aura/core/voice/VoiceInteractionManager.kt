package com.example.aura.core.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * High-level coordinator for Aura's voice interaction system.
 * Manages Push-to-Talk (Mode A) and Continuous Conversation (Mode B),
 * barge-in interruptions, and permission boundaries.
 */
class VoiceInteractionManager(
    private val context: Context,
    val sttEngine: SpeechToTextEngine = AndroidSpeechRecognizerEngine(context),
    val ttsEngine: TextToSpeechEngine = AndroidTextToSpeechEngine(context),
    val vad: VoiceActivityDetector = EnergyThresholdVAD(),
    val audioCaptureManager: AudioCaptureManager = AudioCaptureManager(context)
) {
    private val voiceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _mode = MutableStateFlow(VoiceMode.PUSH_TO_TALK)
    val mode: StateFlow<VoiceMode> = _mode.asStateFlow()

    private val _engineState = MutableStateFlow<VoiceEngineState>(VoiceEngineState.Idle)
    val engineState: StateFlow<VoiceEngineState> = _engineState.asStateFlow()

    private var onUserInputReady: ((String) -> Unit)? = null
    private var onBargeInTriggered: (() -> Unit)? = null

    private var isContinuousRunning = false
    private var restartJob: Job? = null

    fun setCallbacks(onUserInput: (String) -> Unit, onBargeIn: () -> Unit) {
        this.onUserInputReady = onUserInput
        this.onBargeInTriggered = onBargeIn
    }

    fun setMode(newMode: VoiceMode) {
        if (_mode.value != newMode) {
            stopVoiceInteraction()
            _mode.value = newMode
        }
    }

    /**
     * Mode A: Push-to-talk START.
     */
    fun startPushToTalk() {
        if (!audioCaptureManager.hasRecordPermission()) {
            _engineState.value = VoiceEngineState.PermissionRequired(android.Manifest.permission.RECORD_AUDIO)
            return
        }

        // Cancel TTS if speaking
        ttsEngine.stop()

        _engineState.value = VoiceEngineState.Listening(0f)
        sttEngine.startListening(
            onPartialTranscription = { _ ->
                _engineState.value = VoiceEngineState.Listening(0.5f)
            },
            onFinalTranscription = { result ->
                _engineState.value = VoiceEngineState.Idle
                if (result.text.isNotBlank()) {
                    onUserInputReady?.invoke(result.text.trim())
                }
            },
            onError = { error ->
                _engineState.value = VoiceEngineState.Error(error)
            }
        )
    }

    /**
     * Mode A: Push-to-talk RELEASE/STOP.
     */
    fun stopPushToTalk() {
        sttEngine.stopListening()
        if (_engineState.value is VoiceEngineState.Listening) {
            _engineState.value = VoiceEngineState.Transcribing
        }
    }

    /**
     * Mode B: Start continuous conversation loop.
     */
    fun startContinuousConversation() {
        if (!audioCaptureManager.hasRecordPermission()) {
            _engineState.value = VoiceEngineState.PermissionRequired(android.Manifest.permission.RECORD_AUDIO)
            return
        }

        _mode.value = VoiceMode.CONTINUOUS_CONVERSATION
        isContinuousRunning = true
        startContinuousListeningCycle()
    }

    private fun startContinuousListeningCycle() {
        if (!isContinuousRunning || _mode.value != VoiceMode.CONTINUOUS_CONVERSATION) {
            return
        }

        restartJob?.cancel()
        restartJob = null

        // Prevent overlapping microphone capture and TTS playback to avoid audio feedback
        if (ttsEngine.isSpeaking.value ||
            _engineState.value is VoiceEngineState.SynthesizingSpeech ||
            _engineState.value is VoiceEngineState.Speaking
        ) {
            return
        }

        // Stop previous recognition cleanly
        audioCaptureManager.stopCapture()
        sttEngine.stopListening()

        _engineState.value = VoiceEngineState.Listening(0f)

        sttEngine.startListening(
            onPartialTranscription = { _ ->
                voiceScope.launch {
                    if (isContinuousRunning && _mode.value == VoiceMode.CONTINUOUS_CONVERSATION) {
                        _engineState.value = VoiceEngineState.Listening(0.7f)
                    }
                }
            },
            onFinalTranscription = { result ->
                voiceScope.launch {
                    if (isContinuousRunning && _mode.value == VoiceMode.CONTINUOUS_CONVERSATION) {
                        val text = result.text.trim()
                        if (text.isNotBlank()) {
                            // Transition to Thinking / Processing
                            _engineState.value = VoiceEngineState.Thinking
                            // Route recognized speech into existing conversation/agent pipeline
                            onUserInputReady?.invoke(text)
                        } else {
                            // Empty transcription result, continue listening
                            scheduleContinuousRestart(100L)
                        }
                    }
                }
            },
            onError = { error ->
                voiceScope.launch {
                    if (isContinuousRunning && _mode.value == VoiceMode.CONTINUOUS_CONVERSATION) {
                        // Recoverable errors (timeouts, no speech match) should restart listening
                        if (error.contains("timeout", ignoreCase = true) ||
                            error.contains("no speech", ignoreCase = true) ||
                            error.contains("7") || error.contains("6")) {
                            scheduleContinuousRestart(250L)
                        } else {
                            _engineState.value = VoiceEngineState.Error(error)
                            scheduleContinuousRestart(1000L)
                        }
                    }
                }
            }
        )
    }

    private fun scheduleContinuousRestart(delayMillis: Long) {
        if (!isContinuousRunning || _mode.value != VoiceMode.CONTINUOUS_CONVERSATION) return
        restartJob?.cancel()
        restartJob = voiceScope.launch {
            delay(delayMillis)
            if (isContinuousRunning &&
                _mode.value == VoiceMode.CONTINUOUS_CONVERSATION &&
                !ttsEngine.isSpeaking.value &&
                _engineState.value !is VoiceEngineState.SynthesizingSpeech &&
                _engineState.value !is VoiceEngineState.Speaking
            ) {
                startContinuousListeningCycle()
            }
        }
    }

    /**
     * Resumes continuous listening if agent processing completed without speech output.
     */
    fun resumeContinuousListening() {
        voiceScope.launch {
            if (isContinuousRunning &&
                _mode.value == VoiceMode.CONTINUOUS_CONVERSATION &&
                !ttsEngine.isSpeaking.value &&
                _engineState.value !is VoiceEngineState.SynthesizingSpeech &&
                _engineState.value !is VoiceEngineState.Speaking
            ) {
                startContinuousListeningCycle()
            }
        }
    }

    /**
     * Triggered when the user interrupts Aura while Aura is speaking.
     */
    fun handleBargeIn() {
        voiceScope.launch {
            ttsEngine.stop()
            _engineState.value = VoiceEngineState.BargeInInterrupted("User interrupted speaking")
            onBargeInTriggered?.invoke()

            if (isContinuousRunning && _mode.value == VoiceMode.CONTINUOUS_CONVERSATION) {
                scheduleContinuousRestart(200L)
            }
        }
    }

    /**
     * Called by Agent Core to speak response aloud when in Voice mode.
     */
    fun speakResponse(text: String, onFinished: () -> Unit = {}) {
        // Prevent overlapping microphone capture and TTS playback
        restartJob?.cancel()
        restartJob = null
        sttEngine.stopListening()
        audioCaptureManager.stopCapture()

        _engineState.value = VoiceEngineState.SynthesizingSpeech
        ttsEngine.speak(
            request = SpeechSynthesisRequest(text = text),
            onStart = {
                voiceScope.launch {
                    _engineState.value = VoiceEngineState.Speaking(0.1f)
                }
            },
            onDone = {
                voiceScope.launch {
                    if (isContinuousRunning && _mode.value == VoiceMode.CONTINUOUS_CONVERSATION) {
                        _engineState.value = VoiceEngineState.Listening(0f)
                        startContinuousListeningCycle()
                    } else {
                        _engineState.value = VoiceEngineState.Idle
                    }
                    onFinished()
                }
            },
            onError = { error ->
                voiceScope.launch {
                    _engineState.value = VoiceEngineState.Error("TTS failed: $error")
                    if (isContinuousRunning && _mode.value == VoiceMode.CONTINUOUS_CONVERSATION) {
                        scheduleContinuousRestart(500L)
                    }
                    onFinished()
                }
            }
        )
    }

    fun stopVoiceInteraction() {
        isContinuousRunning = false
        restartJob?.cancel()
        restartJob = null
        sttEngine.stopListening()
        ttsEngine.stop()
        audioCaptureManager.stopCapture()
        vad.reset()
        _engineState.value = VoiceEngineState.Idle
    }

    fun destroy() {
        stopVoiceInteraction()
        sttEngine.destroy()
        ttsEngine.shutdown()
    }
}

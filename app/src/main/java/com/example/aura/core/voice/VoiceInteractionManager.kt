package com.example.aura.core.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private val voiceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _mode = MutableStateFlow(VoiceMode.PUSH_TO_TALK)
    val mode: StateFlow<VoiceMode> = _mode.asStateFlow()

    private val _engineState = MutableStateFlow<VoiceEngineState>(VoiceEngineState.Idle)
    val engineState: StateFlow<VoiceEngineState> = _engineState.asStateFlow()

    private var onUserInputReady: ((String) -> Unit)? = null
    private var onBargeInTriggered: (() -> Unit)? = null

    fun setCallbacks(onUserInput: (String) -> Unit, onBargeIn: () -> Unit) {
        this.onUserInputReady = onUserInput
        this.onBargeInTriggered = onBargeIn
    }

    fun setMode(newMode: VoiceMode) {
        stopVoiceInteraction()
        _mode.value = newMode
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
            onPartialTranscription = { partial ->
                _engineState.value = VoiceEngineState.Listening(0.5f)
            },
            onFinalTranscription = { result ->
                _engineState.value = VoiceEngineState.Idle
                if (result.text.isNotBlank()) {
                    onUserInputReady?.invoke(result.text)
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

        _engineState.value = VoiceEngineState.Listening(0f)
        audioCaptureManager.startCapture(
            scope = voiceScope,
            vad = vad,
            onSpeechDetected = {
                // If TTS is currently playing when user speaks, trigger immediate BARGE-IN!
                if (ttsEngine.isSpeaking.value) {
                    handleBargeIn()
                } else if (_engineState.value is VoiceEngineState.Listening) {
                    _engineState.value = VoiceEngineState.SpeechDetected(vad.currentEnergyLevel)
                    startSpeechRecognitionForContinuous()
                }
            }
        )
    }

    private fun startSpeechRecognitionForContinuous() {
        sttEngine.startListening(
            onPartialTranscription = {
                _engineState.value = VoiceEngineState.Listening(0.7f)
            },
            onFinalTranscription = { result ->
                if (result.text.isNotBlank()) {
                    _engineState.value = VoiceEngineState.Thinking
                    onUserInputReady?.invoke(result.text)
                } else {
                    _engineState.value = VoiceEngineState.Listening(0f)
                }
            },
            onError = {
                _engineState.value = VoiceEngineState.Listening(0f)
            }
        )
    }

    /**
     * Triggered when the user interrupts Aura while Aura is speaking.
     */
    fun handleBargeIn() {
        ttsEngine.stop()
        _engineState.value = VoiceEngineState.BargeInInterrupted("User interrupted speaking")
        onBargeInTriggered?.invoke()

        // Restart listening after interruption
        voiceScope.launch {
            _engineState.value = VoiceEngineState.Listening(0f)
            startSpeechRecognitionForContinuous()
        }
    }

    /**
     * Called by Agent Core to speak response aloud when in Voice mode.
     */
    fun speakResponse(text: String, onFinished: () -> Unit = {}) {
        _engineState.value = VoiceEngineState.SynthesizingSpeech
        ttsEngine.speak(
            request = SpeechSynthesisRequest(text = text),
            onStart = {
                _engineState.value = VoiceEngineState.Speaking(0.1f)
            },
            onDone = {
                if (_mode.value == VoiceMode.CONTINUOUS_CONVERSATION) {
                    _engineState.value = VoiceEngineState.Listening(0f)
                } else {
                    _engineState.value = VoiceEngineState.Idle
                }
                onFinished()
            },
            onError = { error ->
                _engineState.value = VoiceEngineState.Error("TTS failed: $error")
                onFinished()
            }
        )
    }

    fun stopVoiceInteraction() {
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

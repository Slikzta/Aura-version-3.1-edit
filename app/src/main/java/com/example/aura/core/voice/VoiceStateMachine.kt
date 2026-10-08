package com.example.aura.core.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-level voice lifecycle states for Push-to-Talk and Continuous conversation.
 */
enum class VoiceStatePhase {
    IDLE,
    CHECKING_PERMISSION,
    PERMISSION_DENIED,
    READY,
    LISTENING,
    PROCESSING_SPEECH,
    THINKING,
    SPEAKING,
    BARGE_IN_TRIGGERED,
    STOPPED,
    ERROR
}

/**
 * Triggers that drive the Voice State Machine.
 */
sealed interface VoiceEvent {
    data object RequestPermission : VoiceEvent
    data object PermissionGranted : VoiceEvent
    data object PermissionDenied : VoiceEvent

    // Mode A: Push-to-talk
    data object StartPressToTalk : VoiceEvent
    data object ReleasePressToTalk : VoiceEvent

    // Mode B: Continuous
    data object StartContinuousLoop : VoiceEvent
    data class VoiceActivityDetected(val energy: Float) : VoiceEvent
    data object SilenceDetected : VoiceEvent

    // Speech & Playback
    data class SpeechTranscribed(val text: String) : VoiceEvent
    data object AssistantStartedSpeaking : VoiceEvent
    data object AssistantFinishedSpeaking : VoiceEvent

    // Barge-in & Interruption
    data object UserBargeIn : VoiceEvent
    data class Stop(val reason: String) : VoiceEvent
    data class ErrorOccurred(val message: String) : VoiceEvent
}

/**
 * Pure state machine governing Aura's voice interaction architecture.
 * Enforces valid phase transitions, permission prerequisites, and barge-in contracts
 * without requiring the native audio hardware stack to be initialized.
 */
class VoiceStateMachine(
    var mode: VoiceMode = VoiceMode.PUSH_TO_TALK
) {
    private val _currentPhase = MutableStateFlow(VoiceStatePhase.IDLE)
    val currentPhase: StateFlow<VoiceStatePhase> = _currentPhase.asStateFlow()

    private var _lastError: String? = null
    val lastError: String? get() = _lastError

    fun processEvent(event: VoiceEvent): VoiceStatePhase {
        val current = _currentPhase.value

        val nextPhase = when (event) {
            is VoiceEvent.RequestPermission -> VoiceStatePhase.CHECKING_PERMISSION

            is VoiceEvent.PermissionGranted -> {
                if (current == VoiceStatePhase.CHECKING_PERMISSION || current == VoiceStatePhase.PERMISSION_DENIED) {
                    VoiceStatePhase.READY
                } else current
            }

            is VoiceEvent.PermissionDenied -> VoiceStatePhase.PERMISSION_DENIED

            // Mode A (Push-to-talk) transitions
            is VoiceEvent.StartPressToTalk -> {
                if (mode == VoiceMode.PUSH_TO_TALK && (current == VoiceStatePhase.READY || current == VoiceStatePhase.IDLE)) {
                    VoiceStatePhase.LISTENING
                } else current
            }

            is VoiceEvent.ReleasePressToTalk -> {
                if (current == VoiceStatePhase.LISTENING) {
                    VoiceStatePhase.PROCESSING_SPEECH
                } else current
            }

            // Mode B (Continuous conversation) transitions
            is VoiceEvent.StartContinuousLoop -> {
                if (current == VoiceStatePhase.READY || current == VoiceStatePhase.IDLE) {
                    VoiceStatePhase.LISTENING
                } else current
            }

            is VoiceEvent.VoiceActivityDetected -> {
                if (current == VoiceStatePhase.SPEAKING) {
                    // Speech detected while assistant is speaking: BARGE IN!
                    VoiceStatePhase.BARGE_IN_TRIGGERED
                } else if (current == VoiceStatePhase.LISTENING) {
                    VoiceStatePhase.PROCESSING_SPEECH
                } else current
            }

            is VoiceEvent.SilenceDetected -> {
                if (current == VoiceStatePhase.PROCESSING_SPEECH && mode == VoiceMode.CONTINUOUS_CONVERSATION) {
                    VoiceStatePhase.LISTENING
                } else current
            }

            is VoiceEvent.SpeechTranscribed -> {
                if (event.text.isNotBlank()) {
                    VoiceStatePhase.THINKING
                } else {
                    VoiceStatePhase.READY
                }
            }

            is VoiceEvent.AssistantStartedSpeaking -> VoiceStatePhase.SPEAKING

            is VoiceEvent.AssistantFinishedSpeaking -> {
                if (mode == VoiceMode.CONTINUOUS_CONVERSATION) {
                    VoiceStatePhase.LISTENING
                } else {
                    VoiceStatePhase.IDLE
                }
            }

            is VoiceEvent.UserBargeIn -> VoiceStatePhase.BARGE_IN_TRIGGERED

            is VoiceEvent.Stop -> VoiceStatePhase.STOPPED

            is VoiceEvent.ErrorOccurred -> {
                _lastError = event.message
                VoiceStatePhase.ERROR
            }
        }

        _currentPhase.value = nextPhase
        return nextPhase
    }

    fun reset() {
        _currentPhase.value = VoiceStatePhase.IDLE
        _lastError = null
    }
}

package com.example.aura.core.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Encapsulates an active interaction session between the user and Aura.
 * Handles state transitions, streaming tokens, continuous voice states,
 * and immediate cancellation/interruption.
 */
class ConversationSession(
    val id: String = UUID.randomUUID().toString(),
    initialMode: SessionMode = SessionMode.INTERACTIVE_TEXT
) {
    private val _mode = MutableStateFlow(initialMode)
    val mode: StateFlow<SessionMode> = _mode.asStateFlow()

    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _currentStreamingText = MutableStateFlow("")
    val currentStreamingText: StateFlow<String> = _currentStreamingText.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    fun setMode(newMode: SessionMode) {
        _mode.value = newMode
        _events.tryEmit(SessionEvent.ModeChanged(newMode))
    }

    fun setState(newState: SessionState) {
        _state.value = newState
        _events.tryEmit(SessionEvent.StateChanged(newState))
    }

    fun setVoiceState(newVoiceState: VoiceState) {
        _voiceState.value = newVoiceState
        _events.tryEmit(SessionEvent.VoiceStateChanged(newVoiceState))
    }

    fun appendStreamingToken(token: String) {
        _currentStreamingText.value += token
        _events.tryEmit(SessionEvent.TokenReceived(token))
    }

    fun clearStreamingText() {
        _currentStreamingText.value = ""
    }

    /**
     * Immediately interrupts the active agent execution (e.g. user barge-in, pause, cancel).
     * Shifts state to INTERRUPTED.
     */
    fun interrupt(reason: String = "User requested interruption") {
        _state.value = SessionState.INTERRUPTED
        if (_voiceState.value == VoiceState.SPEAKING || _voiceState.value == VoiceState.LISTENING) {
            _voiceState.value = VoiceState.INTERRUPTED
        }
        _events.tryEmit(SessionEvent.Interrupted(reason))
    }

    fun resetToIdle() {
        _state.value = SessionState.IDLE
        _voiceState.value = VoiceState.IDLE
        clearStreamingText()
    }
}

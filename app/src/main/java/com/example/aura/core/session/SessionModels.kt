package com.example.aura.core.session

/**
 * Modality and operational mode of an active conversation session.
 */
enum class SessionMode {
    /** Standard text-based prompt and response flow */
    INTERACTIVE_TEXT,

    /** Continuous audio streaming input and output with barge-in support */
    VOICE_STREAM,

    /** Autonomous goal execution with background multi-step task loops */
    AUTONOMOUS_GOAL
}

/**
 * High-level execution status of the current session.
 */
enum class SessionState {
    IDLE,
    PROCESSING,
    STREAMING,
    AWAITING_APPROVAL,
    INTERRUPTED,
    COMPLETED,
    ERROR
}

/**
 * Audio pipeline state for voice streaming sessions.
 */
enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    INTERRUPTED,
    MUTED
}

/**
 * Sealed event hierarchy dispatched by a conversation session.
 */
sealed interface SessionEvent {
    data class StateChanged(val state: SessionState) : SessionEvent
    data class ModeChanged(val mode: SessionMode) : SessionEvent
    data class VoiceStateChanged(val voiceState: VoiceState) : SessionEvent
    data class TokenReceived(val token: String) : SessionEvent
    data class Interrupted(val reason: String) : SessionEvent
    data class ErrorOccurred(val message: String, val throwable: Throwable? = null) : SessionEvent
}

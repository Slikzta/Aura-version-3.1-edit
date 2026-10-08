package com.example.aura.core.voice

/**
 * Operational mode of Aura's voice interaction system.
 */
enum class VoiceMode {
    /**
     * User explicitly taps/holds to talk, releasing to commit transcription.
     */
    PUSH_TO_TALK,

    /**
     * Hands-free continuous loop driven by Voice Activity Detection (VAD) and barge-in.
     */
    CONTINUOUS_CONVERSATION
}

/**
 * Fine-grained state of the voice engine pipeline.
 */
sealed interface VoiceEngineState {
    data object Idle : VoiceEngineState
    data class Listening(val amplitude: Float = 0f) : VoiceEngineState
    data class SpeechDetected(val energy: Float) : VoiceEngineState
    data object Transcribing : VoiceEngineState
    data object Thinking : VoiceEngineState
    data object SynthesizingSpeech : VoiceEngineState
    data class Speaking(val progress: Float = 0f) : VoiceEngineState
    data class BargeInInterrupted(val detectedSpeech: String? = null) : VoiceEngineState
    data object Muted : VoiceEngineState
    data class PermissionRequired(val permission: String) : VoiceEngineState
    data class Error(val message: String, val recoverable: Boolean = true) : VoiceEngineState
}

/**
 * Raw audio packet captured from the microphone or stream.
 */
data class AudioChunk(
    val pcmData: ByteArray,
    val timestampMillis: Long = System.currentTimeMillis(),
    val sampleRate: Int = 16000,
    val channelCount: Int = 1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioChunk
        return pcmData.contentEquals(other.pcmData) && timestampMillis == other.timestampMillis
    }

    override fun hashCode(): Int {
        var result = pcmData.contentHashCode()
        result = 31 * result + timestampMillis.hashCode()
        return result
    }
}

/**
 * Speech recognition result from an STT engine.
 */
data class TranscriptionResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f
)

/**
 * Speech synthesis payload requested by Aura.
 */
data class SpeechSynthesisRequest(
    val text: String,
    val voiceId: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f
)

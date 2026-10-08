package com.example.aura.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Universal Text-to-Speech abstraction for Aura.
 * Allows switching between Android native TTS, on-device neural TTS, or cloud audio generation.
 */
interface TextToSpeechEngine {
    val isSpeaking: StateFlow<Boolean>

    fun speak(
        request: SpeechSynthesisRequest,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    )

    fun stop()
    fun shutdown()
}

/**
 * Real Android TextToSpeech engine implementation.
 */
class AndroidTextToSpeechEngine(
    private val context: Context
) : TextToSpeechEngine, TextToSpeech.OnInitListener {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isInitialized = true
        }
    }

    override fun speak(
        request: SpeechSynthesisRequest,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isInitialized || tts == null) {
            onError("TTS engine is not ready.")
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        tts?.setSpeechRate(request.speechRate)
        tts?.setPitch(request.pitch)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                onStart()
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                onDone()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onError("TTS playback error")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                onError("TTS playback error code: $errorCode")
            }
        })

        tts?.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    override fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

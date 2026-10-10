package com.example.aura.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Universal Speech-to-Text abstraction for Aura.
 * Allows switching between Android native SpeechRecognizer, on-device Whisper,
 * or cloud streaming transcription engines.
 */
interface SpeechToTextEngine {
    val isAvailable: Boolean
    val isListening: StateFlow<Boolean>

    fun startListening(
        onPartialTranscription: (String) -> Unit,
        onFinalTranscription: (TranscriptionResult) -> Unit,
        onError: (String) -> Unit
    )

    fun stopListening()
    fun destroy()
}

/**
 * Real Android SpeechRecognizer implementation using system speech services.
 */
class AndroidSpeechRecognizerEngine(
    private val context: Context
) : SpeechToTextEngine {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    override fun startListening(
        onPartialTranscription: (String) -> Unit,
        onFinalTranscription: (TranscriptionResult) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isAvailable) {
            onError("Android SpeechRecognizer is not available on this device.")
            return
        }

        val action: () -> Unit = {
            try {
                stopListening()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isListening.value = true
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            _isListening.value = false
                        }
                        override fun onError(error: Int) {
                            _isListening.value = false
                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out"
                                SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "RECORD_AUDIO permission missing"
                                else -> "Speech recognition error code: $error"
                            }
                            onError(errorMsg)
                        }

                        override fun onResults(results: Bundle?) {
                            _isListening.value = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            onFinalTranscription(TranscriptionResult(text = text, isFinal = true))
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            matches?.firstOrNull()?.let { onPartialTranscription(it) }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                _isListening.value = false
                onError("Failed to initialize speech recognition: ${e.message}")
            }
        }

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    override fun stopListening() {
        val action: () -> Unit = {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}
            _isListening.value = false
        }

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    override fun destroy() {
        stopListening()
    }
}

package com.example.aura.core.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

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
 * Wraps android.speech.tts.TextToSpeech with defensive initialization timeouts,
 * lifecycle safety, request queueing, and error recovery to prevent hanging Continuous Chat sessions.
 */
class AndroidTextToSpeechEngine(
    private val context: Context,
    private val initTimeoutMs: Long = 4000L,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
    private val ttsFactory: ((Context, TextToSpeech.OnInitListener) -> TextToSpeech?)? = null
) : TextToSpeechEngine, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AndroidTTS"
    }

    private enum class InitState {
        INITIALIZING,
        READY,
        FAILED,
        SHUTDOWN
    }

    private data class PendingSpeech(
        val request: SpeechSynthesisRequest,
        val onStart: () -> Unit,
        val onDone: () -> Unit,
        val onError: (String) -> Unit
    )

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val lock = Any()
    private var tts: TextToSpeech? = null
    private var initState = InitState.INITIALIZING
    private var pendingRequest: PendingSpeech? = null
    private var initTimeoutJob: Job? = null

    init {
        initTimeoutJob = coroutineScope.launch {
            delay(initTimeoutMs)
            handleInitTimeout()
        }

        try {
            tts = if (ttsFactory != null) {
                ttsFactory.invoke(context.applicationContext, this)
            } else {
                TextToSpeech(context.applicationContext, this)
            }
            if (tts == null) {
                Log.e(TAG, "TextToSpeech instance is null after creation")
                failInitialization("TextToSpeech instance could not be created")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate Android TextToSpeech", e)
            failInitialization("Exception initializing TextToSpeech: ${e.message}")
        }
    }

    private fun handleInitTimeout() {
        var pendingToNotify: PendingSpeech? = null
        synchronized(lock) {
            if (initState == InitState.INITIALIZING) {
                Log.w(TAG, "TextToSpeech initialization timed out after ${initTimeoutMs}ms")
                initState = InitState.FAILED
                pendingToNotify = pendingRequest
                pendingRequest = null
            }
        }
        pendingToNotify?.onError?.invoke("TTS initialization timed out after ${initTimeoutMs}ms")
    }

    private fun failInitialization(reason: String) {
        initTimeoutJob?.cancel()
        initTimeoutJob = null
        var pendingToNotify: PendingSpeech? = null
        synchronized(lock) {
            initState = InitState.FAILED
            pendingToNotify = pendingRequest
            pendingRequest = null
        }
        pendingToNotify?.onError?.invoke(reason)
    }

    override fun onInit(status: Int) {
        initTimeoutJob?.cancel()
        initTimeoutJob = null

        var requestToSpeak: PendingSpeech? = null
        var errorToNotify: Pair<PendingSpeech, String>? = null

        synchronized(lock) {
            if (initState == InitState.SHUTDOWN) {
                Log.d(TAG, "onInit received after shutdown, ignoring")
                return
            }

            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TextToSpeech engine initialized successfully")
                initState = InitState.READY
                configureLanguage()
                requestToSpeak = pendingRequest
                pendingRequest = null
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with code: $status")
                initState = InitState.FAILED
                val pending = pendingRequest
                pendingRequest = null
                if (pending != null) {
                    errorToNotify = Pair(pending, "TTS initialization failed with code: $status")
                }
            }
        }

        requestToSpeak?.let { pending ->
            speakInternal(pending.request, pending.onStart, pending.onDone, pending.onError)
        }

        errorToNotify?.let { (pending, errorMsg) ->
            pending.onError(errorMsg)
        }
    }

    private fun configureLanguage() {
        try {
            val defaultLocale = Locale.getDefault()
            val langResult = tts?.setLanguage(defaultLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale $defaultLocale not supported or missing data, falling back to Locale.US")
                tts?.setLanguage(Locale.US)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring TTS language", e)
        }
    }

    override fun speak(
        request: SpeechSynthesisRequest,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (request.text.isBlank()) {
            Log.d(TAG, "Requested speech text is blank; completing immediately")
            onDone()
            return
        }

        var supersededRequest: PendingSpeech? = null

        synchronized(lock) {
            when (initState) {
                InitState.SHUTDOWN -> {
                    Log.w(TAG, "speak called on shut down TTS engine")
                    onError("TTS engine is shut down")
                    return
                }
                InitState.FAILED -> {
                    Log.w(TAG, "speak called on failed TTS engine")
                    onError("TTS engine initialization failed or timed out")
                    return
                }
                InitState.INITIALIZING -> {
                    Log.d(TAG, "TTS still initializing, queuing pending request: ${request.text.take(30)}...")
                    supersededRequest = pendingRequest
                    pendingRequest = PendingSpeech(request, onStart, onDone, onError)
                }
                InitState.READY -> {
                    // Ready to speak directly below
                }
            }
        }

        if (supersededRequest != null) {
            supersededRequest?.onError?.invoke("Speech request superseded before TTS initialization completed")
            return
        }

        synchronized(lock) {
            if (initState != InitState.READY) {
                return
            }
        }

        speakInternal(request, onStart, onDone, onError)
    }

    private fun speakInternal(
        request: SpeechSynthesisRequest,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentTts = tts
        if (currentTts == null) {
            onError("TTS engine is not ready.")
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        val isCompleted = AtomicBoolean(false)

        try {
            currentTts.setSpeechRate(request.speechRate)
            currentTts.setPitch(request.pitch)

            currentTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    if (id == utteranceId) {
                        _isSpeaking.value = true
                        Log.d(TAG, "Utterance started: $id")
                        onStart()
                    }
                }

                override fun onDone(id: String?) {
                    if (id == utteranceId && isCompleted.compareAndSet(false, true)) {
                        _isSpeaking.value = false
                        Log.d(TAG, "Utterance done: $id")
                        onDone()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && isCompleted.compareAndSet(false, true)) {
                        _isSpeaking.value = false
                        Log.w(TAG, "Utterance error: $id")
                        onError("TTS playback error")
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && isCompleted.compareAndSet(false, true)) {
                        _isSpeaking.value = false
                        Log.w(TAG, "Utterance error code $errorCode: $id")
                        onError("TTS playback error code: $errorCode")
                    }
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val result = currentTts.speak(request.text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                _isSpeaking.value = false
                if (isCompleted.compareAndSet(false, true)) {
                    Log.e(TAG, "TextToSpeech speak failed with code: $result")
                    onError("TextToSpeech speak failed with code: $result")
                }
            }
        } catch (e: Exception) {
            _isSpeaking.value = false
            if (isCompleted.compareAndSet(false, true)) {
                Log.e(TAG, "Exception during TTS speak", e)
                onError("TTS speak error: ${e.message}")
            }
        }
    }

    override fun stop() {
        Log.d(TAG, "stop() called")
        var pendingToCancel: PendingSpeech? = null
        synchronized(lock) {
            pendingToCancel = pendingRequest
            pendingRequest = null
        }
        pendingToCancel?.onError?.invoke("TTS stopped before speech could start")

        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Exception stopping TTS", e)
        }
        _isSpeaking.value = false
    }

    override fun shutdown() {
        Log.d(TAG, "shutdown() called")
        initTimeoutJob?.cancel()
        initTimeoutJob = null
        stop()
        synchronized(lock) {
            initState = InitState.SHUTDOWN
            try {
                tts?.shutdown()
            } catch (e: Exception) {
                Log.w(TAG, "Exception shutting down TTS", e)
            }
            tts = null
        }
    }
}

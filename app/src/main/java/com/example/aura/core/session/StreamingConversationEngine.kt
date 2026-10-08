package com.example.aura.core.session

import com.example.aura.core.provider.CompletionRequest
import com.example.aura.core.provider.ModelProvider
import com.example.aura.core.provider.ProviderError
import com.example.aura.core.provider.StreamChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Observable lifecycle state of an active conversation stream.
 */
sealed interface ConversationStreamState {
    data object Idle : ConversationStreamState
    data class Connecting(val sessionId: String, val model: String) : ConversationStreamState
    data class Streaming(val sessionId: String, val tokenCount: Int, val elapsedMillis: Long, val currentText: String) : ConversationStreamState
    data class Completed(val sessionId: String, val totalTokens: Int, val durationMillis: Long, val finalContent: String) : ConversationStreamState
    data class Cancelled(val sessionId: String, val reason: String) : ConversationStreamState
    data class TimedOut(val sessionId: String, val timeoutMillis: Long) : ConversationStreamState
    data class Error(val sessionId: String, val error: ProviderError) : ConversationStreamState
}

/**
 * Event callbacks emitted by [StreamingConversationEngine] across the streaming lifecycle.
 */
data class StreamLifecycleCallbacks(
    val onStart: (sessionId: String, model: String) -> Unit = { _, _ -> },
    val onChunk: (sessionId: String, token: String, currentText: String) -> Unit = { _, _, _ -> },
    val onComplete: (sessionId: String, totalTokens: Int, durationMillis: Long, fullText: String) -> Unit = { _, _, _, _ -> },
    val onCancel: (sessionId: String, reason: String) -> Unit = { _, _ -> },
    val onTimeout: (sessionId: String, timeoutMillis: Long) -> Unit = { _, _ -> },
    val onError: (sessionId: String, error: ProviderError) -> Unit = { _, _ -> }
)

/**
 * Core engine managing the streaming conversation lifecycle for Aura.
 * Guarantees lifecycle guarantees (start, chunk, complete, cancel, timeout)
 * without artificial token pacing or delays.
 */
class StreamingConversationEngine {

    private val _streamState = MutableStateFlow<ConversationStreamState>(ConversationStreamState.Idle)
    val streamState: StateFlow<ConversationStreamState> = _streamState.asStateFlow()

    private var activeJob: Job? = null

    /**
     * Starts streaming a conversation turn asynchronously.
     */
    fun startStreamingTurn(
        scope: CoroutineScope,
        sessionId: String,
        request: CompletionRequest,
        provider: ModelProvider,
        callbacks: StreamLifecycleCallbacks = StreamLifecycleCallbacks()
    ): Job {
        cancelActiveStream(sessionId, "Superseded by new turn")

        val job = scope.launch {
            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            val accumulated = StringBuilder()

            _streamState.value = ConversationStreamState.Connecting(
                sessionId = sessionId,
                model = request.modelName ?: provider.name
            )
            callbacks.onStart(sessionId, request.modelName ?: provider.name)

            try {
                withTimeout(request.streamTimeoutMillis) {
                    provider.streamResponse(request).collect { chunk ->
                        when (chunk) {
                            is StreamChunk.TextChunk -> {
                                tokenCount++
                                accumulated.append(chunk.text)
                                val currentText = accumulated.toString()
                                val elapsed = System.currentTimeMillis() - startTime

                                _streamState.value = ConversationStreamState.Streaming(
                                    sessionId = sessionId,
                                    tokenCount = tokenCount,
                                    elapsedMillis = elapsed,
                                    currentText = currentText
                                )
                                callbacks.onChunk(sessionId, chunk.text, currentText)
                            }
                            is StreamChunk.DoneChunk -> {
                                // Handled upon block completion
                            }
                            is StreamChunk.ErrorChunk -> {
                                throw chunk.throwable
                            }
                            else -> Unit
                        }
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                val finalText = accumulated.toString()
                _streamState.value = ConversationStreamState.Completed(
                    sessionId = sessionId,
                    totalTokens = tokenCount,
                    durationMillis = duration,
                    finalContent = finalText
                )
                callbacks.onComplete(sessionId, tokenCount, duration, finalText)

            } catch (e: TimeoutCancellationException) {
                _streamState.value = ConversationStreamState.TimedOut(sessionId, request.streamTimeoutMillis)
                callbacks.onTimeout(sessionId, request.streamTimeoutMillis)
            } catch (e: CancellationException) {
                _streamState.value = ConversationStreamState.Cancelled(sessionId, "User cancelled stream")
                callbacks.onCancel(sessionId, "User cancelled stream")
                throw e
            } catch (e: Exception) {
                val providerError = if (e is ProviderError) e else ProviderError.GeneralError(e.message ?: "Stream failure", e)
                _streamState.value = ConversationStreamState.Error(sessionId, providerError)
                callbacks.onError(sessionId, providerError)
            } finally {
                activeJob = null
            }
        }

        activeJob = job
        return job
    }

    /**
     * Cancels any ongoing streaming turn immediately.
     */
    fun cancelActiveStream(sessionId: String, reason: String = "Interrupted by caller") {
        activeJob?.cancel(CancellationException(reason))
        activeJob = null
        if (_streamState.value is ConversationStreamState.Streaming || _streamState.value is ConversationStreamState.Connecting) {
            _streamState.value = ConversationStreamState.Cancelled(sessionId, reason)
        }
    }

    fun reset() {
        activeJob?.cancel()
        activeJob = null
        _streamState.value = ConversationStreamState.Idle
    }
}

package com.example.aura.core.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/**
 * Observable lifecycle state of an active streaming session.
 */
sealed interface StreamingState {
    data object Idle : StreamingState
    data class Starting(val model: String, val providerId: String) : StreamingState
    data class ReceivingChunks(val tokenCount: Int, val elapsedMillis: Long, val lastTokenPreview: String) : StreamingState
    data class Completed(val totalTokens: Int, val finishReason: String?, val durationMillis: Long) : StreamingState
    data class Cancelled(val reason: String) : StreamingState
    data class Interrupted(val reason: String) : StreamingState
    data class TimedOut(val timeoutMillis: Long) : StreamingState
    data class Failed(val error: ProviderError) : StreamingState
}

/**
 * Production streaming engine for Aura.
 * Manages incremental token streaming, cancellation, timeout enforcement,
 * and real-time state observability without artificial delays.
 */
class StreamingEngine {

    private val _state = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val state: StateFlow<StreamingState> = _state.asStateFlow()

    private val _accumulatedText = MutableStateFlow("")
    val accumulatedText: StateFlow<String> = _accumulatedText.asStateFlow()

    /**
     * Executes a streaming request through the given [ModelProvider], wrapping it with
     * timeout safety, chunk accounting, and observable lifecycle events.
     */
    fun stream(
        request: CompletionRequest,
        provider: ModelProvider
    ): Flow<StreamChunk> = flow {
        val startTime = System.currentTimeMillis()
        var tokenCount = 0
        var lastFinishReason: String? = null
        _accumulatedText.value = ""

        _state.value = StreamingState.Starting(
            model = request.modelName ?: "default",
            providerId = provider.id
        )

        try {
            withTimeout(request.streamTimeoutMillis) {
                provider.streamResponse(request).collect { chunk ->
                    when (chunk) {
                        is StreamChunk.TextChunk -> {
                            tokenCount++
                            val elapsed = System.currentTimeMillis() - startTime
                            _accumulatedText.value += chunk.text
                            _state.value = StreamingState.ReceivingChunks(
                                tokenCount = tokenCount,
                                elapsedMillis = elapsed,
                                lastTokenPreview = chunk.text.take(30)
                            )
                            emit(chunk)
                        }
                        is StreamChunk.ReasoningChunk -> {
                            emit(chunk)
                        }
                        is StreamChunk.ToolCallChunk -> {
                            emit(chunk)
                        }
                        is StreamChunk.UsageChunk -> {
                            emit(chunk)
                        }
                        is StreamChunk.DoneChunk -> {
                            lastFinishReason = chunk.finishReason
                            emit(chunk)
                        }
                        is StreamChunk.ErrorChunk -> {
                            emit(chunk)
                        }
                    }
                }
            }

            val duration = System.currentTimeMillis() - startTime
            _state.value = StreamingState.Completed(
                totalTokens = tokenCount,
                finishReason = lastFinishReason,
                durationMillis = duration
            )

        } catch (e: TimeoutCancellationException) {
            val timeoutError = ProviderError.NetworkTimeoutError(
                message = "Streaming timed out after ${request.streamTimeoutMillis}ms",
                timeoutMillis = request.streamTimeoutMillis
            )
            _state.value = StreamingState.TimedOut(request.streamTimeoutMillis)
            emit(StreamChunk.ErrorChunk(timeoutError, timeoutError.message))
        } catch (e: CancellationException) {
            _state.value = StreamingState.Interrupted("Stream cancelled by user or caller")
            throw e
        } catch (e: Exception) {
            val providerError = if (e is ProviderError) e else ProviderError.GeneralError(e.message ?: "Streaming error", e)
            _state.value = StreamingState.Failed(providerError)
            emit(StreamChunk.ErrorChunk(providerError, providerError.message ?: "Unknown error"))
        }
    }

    fun reset() {
        _state.value = StreamingState.Idle
        _accumulatedText.value = ""
    }
}

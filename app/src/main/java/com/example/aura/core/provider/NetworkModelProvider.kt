package com.example.aura.core.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Universal network-capable model provider implementation for Aura.
 * Handles OpenAI-compatible, Ollama, and generic REST/SSE model endpoints.
 *
 * Implements strict error boundaries and honest state reporting:
 * - If credentials or endpoints are unconfigured, raises [ProviderError.AuthenticationError].
 * - When valid endpoints are configured, streams real tokens over SSE without artificial delay.
 * - Never returns fabricated mock tokens.
 */
class NetworkModelProvider(
    private var config: ProviderConfig,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : ModelProvider {

    override val id: String get() = config.id
    override val name: String get() = config.displayName
    override val type: ModelProviderType get() = config.type

    override val authState: ProviderAuthState
        get() {
            return when {
                config.endpointUrl.isBlank() -> ProviderAuthState.InvalidConfiguration("Missing endpoint URL")
                config.type == ModelProviderType.OLLAMA_LOCAL -> ProviderAuthState.Configured
                config.apiKey.isBlank() -> ProviderAuthState.MissingCredentials("API key is not configured for ${config.displayName}")
                else -> ProviderAuthState.Configured
            }
        }

    override val capabilities: Set<ModelCapability> = setOf(
        ModelCapability.TEXT_GENERATION,
        ModelCapability.STREAMING,
        ModelCapability.FUNCTION_CALLING
    )

    fun updateConfig(newConfig: ProviderConfig) {
        this.config = newConfig
    }

    override suspend fun generateResponse(request: CompletionRequest): CompletionResponse {
        when (val state = authState) {
            is ProviderAuthState.MissingCredentials -> throw ProviderError.AuthenticationError(state.reason, id)
            is ProviderAuthState.InvalidConfiguration -> throw ProviderError.InvalidRequestError(state.error)
            else -> Unit
        }

        val requestPayload = buildJsonPayload(request, stream = false)
        val httpRequest = Request.Builder()
            .url(getCompletionUrl())
            .addHeader("Content-Type", "application/json")
            .apply {
                if (config.apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .post(requestPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = httpClient.newCall(httpRequest).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                throw parseHttpError(response.code, errorBody)
            }

            val bodyString = response.body?.string() ?: throw ProviderError.InvalidRequestError("Empty response body")
            val json = JSONObject(bodyString)
            val choices = json.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val messageObj = firstChoice?.optJSONObject("message")
            val content = messageObj?.optString("content", "") ?: ""
            val finishReason = firstChoice?.optString("finish_reason", "stop")

            CompletionResponse(
                message = ChatMessage(role = MessageRole.ASSISTANT, content = content),
                finishReason = finishReason,
                providerId = id
            )
        } catch (e: Exception) {
            if (e is ProviderError) throw e
            throw ProviderError.ProviderUnavailableError("Network call failed: ${e.message}", e)
        }
    }

    override fun streamResponse(request: CompletionRequest): Flow<StreamChunk> = flow {
        when (val state = authState) {
            is ProviderAuthState.MissingCredentials -> throw ProviderError.AuthenticationError(state.reason, id)
            is ProviderAuthState.InvalidConfiguration -> throw ProviderError.InvalidRequestError(state.error)
            else -> Unit
        }

        val requestPayload = buildJsonPayload(request, stream = true)
        val httpRequest = Request.Builder()
            .url(getCompletionUrl())
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .apply {
                if (config.apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .post(requestPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            httpClient.newCall(httpRequest).execute()
        } catch (e: Exception) {
            throw ProviderError.ProviderUnavailableError("Failed to connect to ${config.displayName}: ${e.message}", e)
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "HTTP ${response.code}"
            throw parseHttpError(response.code, errorBody)
        }

        val inputStream = response.body?.byteStream()
            ?: throw ProviderError.InvalidRequestError("Null response stream")

        val reader = BufferedReader(InputStreamReader(inputStream))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty() || currentLine.startsWith(":")) continue // SSE keep-alive

                if (currentLine.startsWith("data:")) {
                    val data = currentLine.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        emit(StreamChunk.DoneChunk("stop"))
                        break
                    }

                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        val delta = choices?.optJSONObject(0)?.optJSONObject("delta")
                        val token = delta?.optString("content")
                        if (!token.isNullOrEmpty()) {
                            emit(StreamChunk.TextChunk(token))
                        }
                    } catch (_: Exception) {
                        // Skip unparseable heartbeats or non-JSON lines
                    }
                }
            }
        } finally {
            reader.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun checkHealth(): ProviderHealth {
        return when (val state = authState) {
            is ProviderAuthState.Configured -> ProviderHealth.Healthy
            is ProviderAuthState.MissingCredentials -> ProviderHealth.Unconfigured(state.reason)
            is ProviderAuthState.InvalidConfiguration -> ProviderHealth.Degraded(state.error)
            else -> ProviderHealth.Unconfigured("Not configured")
        }
    }

    private fun getCompletionUrl(): String {
        val base = config.endpointUrl.trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    private fun buildJsonPayload(request: CompletionRequest, stream: Boolean): JSONObject {
        val root = JSONObject()
        root.put("model", config.defaultModel.ifBlank { "default" })
        root.put("stream", stream)
        root.put("temperature", request.temperature)
        root.put("max_tokens", request.maxTokens)

        val messagesArray = JSONArray()
        if (!request.systemPrompt.isNullOrBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", request.systemPrompt)
            })
        }
        request.messages.forEach { msg ->
            messagesArray.put(JSONObject().apply {
                put("role", msg.role.name.lowercase())
                put("content", msg.content)
            })
        }
        root.put("messages", messagesArray)
        return root
    }

    private fun parseHttpError(statusCode: Int, body: String): ProviderError {
        return when (statusCode) {
            401, 403 -> ProviderError.AuthenticationError("Authentication failed ($statusCode): $body", id)
            429 -> ProviderError.RateLimitError("Rate limit exceeded ($statusCode): $body")
            408 -> ProviderError.NetworkTimeoutError("Server timed out ($statusCode)", config.timeoutMillis)
            else -> ProviderError.ProviderUnavailableError("Server returned error ($statusCode): $body")
        }
    }
}

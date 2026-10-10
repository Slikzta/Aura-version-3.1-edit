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
 * - Supports complete tool calling protocol and parameter mapping.
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
            val isLocalEndpoint = config.type == ModelProviderType.OLLAMA_LOCAL ||
                config.endpointUrl.contains("localhost") ||
                config.endpointUrl.contains("127.0.0.1") ||
                config.endpointUrl.contains("10.0.2.2")
            return when {
                config.endpointUrl.isBlank() -> ProviderAuthState.InvalidConfiguration("Missing endpoint URL")
                isLocalEndpoint -> ProviderAuthState.Configured
                config.apiKey.isBlank() || config.apiKey == "UNCONFIGURED" ->
                    ProviderAuthState.MissingCredentials("API key is not configured for ${config.displayName}")
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
                if (config.apiKey.isNotBlank() && config.apiKey != "UNCONFIGURED") {
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

            val toolCalls = mutableListOf<ToolCallRequest>()
            val toolCallsJsonArray = messageObj?.optJSONArray("tool_calls")
            if (toolCallsJsonArray != null) {
                for (i in 0 until toolCallsJsonArray.length()) {
                    val tcObj = toolCallsJsonArray.optJSONObject(i) ?: continue
                    val callId = tcObj.optString("id", "call_${System.currentTimeMillis()}_$i")
                    val fnObj = tcObj.optJSONObject("function")
                    val fnName = fnObj?.optString("name", "") ?: ""
                    val fnArgs = fnObj?.optString("arguments", "{}") ?: "{}"
                    if (fnName.isNotBlank()) {
                        toolCalls.add(ToolCallRequest(id = callId, name = fnName, argumentsJson = fnArgs))
                    }
                }
            }

            CompletionResponse(
                message = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = content,
                    toolCalls = toolCalls
                ),
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
                if (config.apiKey.isNotBlank() && config.apiKey != "UNCONFIGURED") {
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
        val streamingToolCalls = mutableMapOf<Int, StreamingToolCallAccumulator>()

        try {
            var line: String?
            var finishReasonEmitted: String? = null

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty() || currentLine.startsWith(":")) continue // SSE keep-alive

                if (currentLine.startsWith("data:")) {
                    val data = currentLine.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        break
                    }

                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        val firstChoice = choices?.optJSONObject(0)
                        val delta = firstChoice?.optJSONObject("delta")
                        val finishReason = firstChoice?.optString("finish_reason")
                        if (!finishReason.isNullOrEmpty() && finishReason != "null") {
                            finishReasonEmitted = finishReason
                        }

                        // Stream text token
                        val token = delta?.optString("content")
                        if (!token.isNullOrEmpty() && token != "null") {
                            emit(StreamChunk.TextChunk(token))
                        }

                        // Stream tool calls
                        val deltaToolCalls = delta?.optJSONArray("tool_calls")
                        if (deltaToolCalls != null) {
                            for (i in 0 until deltaToolCalls.length()) {
                                val tcDelta = deltaToolCalls.optJSONObject(i) ?: continue
                                val index = tcDelta.optInt("index", i)
                                val accumulator = streamingToolCalls.getOrPut(index) { StreamingToolCallAccumulator() }

                                val callId = tcDelta.optString("id")
                                if (!callId.isNullOrEmpty() && callId != "null") {
                                    accumulator.id = callId
                                }

                                val fn = tcDelta.optJSONObject("function")
                                if (fn != null) {
                                    val name = fn.optString("name")
                                    if (!name.isNullOrEmpty() && name != "null") {
                                        accumulator.name = name
                                    }

                                    val argsChunk = fn.optString("arguments")
                                    if (!argsChunk.isNullOrEmpty() && argsChunk != "null") {
                                        accumulator.arguments.append(argsChunk)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Skip unparseable heartbeats or non-JSON lines
                    }
                }
            }

            // Emit accumulated tool calls in order
            streamingToolCalls.keys.sorted().forEach { index ->
                val tc = streamingToolCalls[index]
                if (tc != null && tc.name.isNotBlank()) {
                    emit(
                        StreamChunk.ToolCallChunk(
                            ToolCallRequest(
                                id = tc.id.ifBlank { "call_${System.currentTimeMillis()}_$index" },
                                name = tc.name,
                                argumentsJson = tc.arguments.toString().ifBlank { "{}" }
                            )
                        )
                    )
                }
            }

            emit(StreamChunk.DoneChunk(finishReasonEmitted ?: "stop"))
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
        root.put("model", config.defaultModel.ifBlank { "gpt-4o-mini" })
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
            val msgObj = JSONObject()
            msgObj.put("role", msg.role.name.lowercase())
            when (msg.role) {
                MessageRole.TOOL -> {
                    msgObj.put("content", msg.content)
                    msgObj.put("tool_call_id", if (!msg.toolCallId.isNullOrBlank()) msg.toolCallId else "call_default")
                    if (!msg.name.isNullOrBlank()) {
                        msgObj.put("name", msg.name)
                    }
                }
                MessageRole.ASSISTANT -> {
                    if (msg.toolCalls.isNotEmpty()) {
                        if (msg.content.isNotBlank()) {
                            msgObj.put("content", msg.content)
                        } else {
                            msgObj.put("content", JSONObject.NULL)
                        }
                        val toolCallsArr = JSONArray()
                        msg.toolCalls.forEach { tc ->
                            toolCallsArr.put(JSONObject().apply {
                                put("id", tc.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", tc.name)
                                    put("arguments", tc.argumentsJson)
                                })
                            })
                        }
                        msgObj.put("tool_calls", toolCallsArr)
                    } else {
                        msgObj.put("content", msg.content)
                    }
                }
                else -> {
                    msgObj.put("content", msg.content)
                }
            }
            messagesArray.put(msgObj)
        }
        root.put("messages", messagesArray)

        if (request.availableTools.isNotEmpty()) {
            val toolsArray = JSONArray()
            request.availableTools.forEach { toolDef ->
                val toolObj = JSONObject()
                toolObj.put("type", "function")
                val fnObj = JSONObject()
                fnObj.put("name", toolDef.name)
                fnObj.put("description", toolDef.description)
                try {
                    fnObj.put("parameters", JSONObject(toolDef.parametersJsonSchema))
                } catch (_: Exception) {
                    fnObj.put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                }
                toolObj.put("function", fnObj)
                toolsArray.put(toolObj)
            }
            root.put("tools", toolsArray)
        }

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

    private class StreamingToolCallAccumulator(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    )
}

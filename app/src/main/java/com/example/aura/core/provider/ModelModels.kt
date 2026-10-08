package com.example.aura.core.provider

/**
 * Message participant role in a conversation.
 */
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

/**
 * Authentication and configuration readiness state of an AI model provider.
 */
sealed interface ProviderAuthState {
    data object Configured : ProviderAuthState
    data class MissingCredentials(val reason: String) : ProviderAuthState
    data class InvalidConfiguration(val error: String) : ProviderAuthState
    data object Uninitialized : ProviderAuthState
}

/**
 * Structured error classification for provider interactions.
 */
sealed class ProviderError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class AuthenticationError(override val message: String, val providerId: String) : ProviderError(message)
    data class NetworkTimeoutError(override val message: String, val timeoutMillis: Long) : ProviderError(message)
    data class RateLimitError(override val message: String, val retryAfterSeconds: Int? = null) : ProviderError(message)
    data class InvalidRequestError(override val message: String) : ProviderError(message)
    data class ProviderUnavailableError(override val message: String, override val cause: Throwable? = null) : ProviderError(message, cause)
    data class CancelledError(val reason: String) : ProviderError("Operation cancelled: $reason")
    data class GeneralError(override val message: String, override val cause: Throwable? = null) : ProviderError(message, cause)
}

/**
 * Agnostic chat message representation across providers.
 */
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val name: String? = null,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val toolCallId: String? = null
)

/**
 * Request emitted by a model to invoke a tool.
 */
data class ToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String
)

/**
 * Agnostic schema definition for a tool exposed to a model.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJsonSchema: String
)

/**
 * Standard request payload sent to any model provider.
 */
data class CompletionRequest(
    val messages: List<ChatMessage>,
    val systemPrompt: String? = null,
    val availableTools: List<ToolDefinition> = emptyList(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val modelName: String? = null,
    val timeoutMillis: Long = 30_000L,
    val streamTimeoutMillis: Long = 60_000L
)

/**
 * Standard non-streaming response from a model provider.
 */
data class CompletionResponse(
    val message: ChatMessage,
    val finishReason: String? = null,
    val usage: TokenUsage? = null,
    val providerId: String
)

/**
 * Token usage accounting.
 */
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

/**
 * Streaming response chunk emitted by a model provider.
 */
sealed interface StreamChunk {
    data class TextChunk(val text: String) : StreamChunk
    data class ReasoningChunk(val thought: String) : StreamChunk
    data class ToolCallChunk(val toolCall: ToolCallRequest) : StreamChunk
    data class UsageChunk(val usage: TokenUsage) : StreamChunk
    data class DoneChunk(val finishReason: String? = null) : StreamChunk
    data class ErrorChunk(val throwable: Throwable, val message: String) : StreamChunk
}

/**
 * Configuration for a model provider.
 */
data class ProviderConfig(
    val id: String,
    val type: ModelProviderType,
    val displayName: String,
    val endpointUrl: String = "",
    val apiKey: String = "",
    val defaultModel: String = "",
    val isEnabled: Boolean = true,
    val timeoutMillis: Long = 30_000L
)

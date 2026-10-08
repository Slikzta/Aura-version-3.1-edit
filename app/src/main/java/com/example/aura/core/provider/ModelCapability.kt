package com.example.aura.core.provider

/**
 * Functional capabilities offered by a model provider.
 */
enum class ModelCapability {
    TEXT_GENERATION,
    STREAMING,
    FUNCTION_CALLING,
    VISION,
    AUDIO_INPUT,
    AUDIO_OUTPUT,
    EMBEDDINGS
}

/**
 * Health status of a model provider backend.
 */
sealed interface ProviderHealth {
    data object Healthy : ProviderHealth
    data class Degraded(val message: String) : ProviderHealth
    data class Unreachable(val error: String) : ProviderHealth
    data class Unconfigured(val missingConfig: String) : ProviderHealth
}

/**
 * Classification of known provider backends.
 */
enum class ModelProviderType {
    GEMINI,
    OPENAI,
    ANTHROPIC,
    OLLAMA_LOCAL,
    CUSTOM_ENDPOINT,
    DIAGNOSTIC_OFFLINE
}

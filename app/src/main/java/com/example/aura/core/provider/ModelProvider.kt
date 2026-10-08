package com.example.aura.core.provider

import kotlinx.coroutines.flow.Flow

/**
 * Universal abstraction for AI Model Providers in Aura.
 * Aura is explicitly decoupled from specific vendors (Gemini, OpenAI, Anthropic, Ollama, etc.).
 * Implementations translate between Aura's agnostic format and vendor-specific protocol adapters.
 */
interface ModelProvider {
    /** Unique identifier for this provider instance (e.g. "gemini_pro", "ollama_local") */
    val id: String

    /** Human-readable provider name */
    val name: String

    /** Type classification */
    val type: ModelProviderType

    /** Current authentication / credential readiness state */
    val authState: ProviderAuthState

    /** Execution venue: ON_DEVICE, NETWORK_REMOTE, or HYBRID_ADAPTIVE */
    val executionLocation: ModelExecutionLocation get() = ModelExecutionLocation.NETWORK_REMOTE

    /** Set of capabilities supported by this provider */
    val capabilities: Set<ModelCapability>

    /**
     * Executes a synchronous completion request with timeout and cancellation support.
     * @throws ProviderError on network, timeout, or authentication failure.
     */
    suspend fun generateResponse(request: CompletionRequest): CompletionResponse

    /**
     * Executes a streaming completion request emitting incremental chunks in real time.
     * Must respect Coroutine cancellation immediately when the user or session interrupts.
     */
    fun streamResponse(request: CompletionRequest): Flow<StreamChunk>

    /**
     * Validates configuration, credentials, or network connectivity.
     */
    suspend fun checkHealth(): ProviderHealth

    /**
     * Validates authentication state without sending a full completion request.
     */
    suspend fun validateConfiguration(): ProviderAuthState = authState
}

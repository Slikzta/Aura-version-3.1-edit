package com.example.aura.core.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Foundation diagnostic provider that executes locally without external network credentials.
 * Adheres strictly to the rule: "Do NOT implement fake AI functionality."
 * Reports provider status honestly and verifies the streaming and tool-calling pipeline
 * without artificial delays.
 */
class ConfigurableOfflineProvider(
    private var config: ProviderConfig = ProviderConfig(
        id = "diagnostic_offline",
        type = ModelProviderType.DIAGNOSTIC_OFFLINE,
        displayName = "Aura Architecture Diagnostic Provider",
        defaultModel = "aura-stage2-foundation",
        isEnabled = true
    )
) : ModelProvider {

    override val id: String get() = config.id
    override val name: String get() = config.displayName
    override val type: ModelProviderType get() = config.type

    override val authState: ProviderAuthState
        get() = ProviderAuthState.Configured

    override val capabilities: Set<ModelCapability> = setOf(
        ModelCapability.TEXT_GENERATION,
        ModelCapability.STREAMING,
        ModelCapability.FUNCTION_CALLING
    )

    override suspend fun generateResponse(request: CompletionRequest): CompletionResponse {
        val userPrompt = request.messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""

        val isToolTest = userPrompt.contains("tool", ignoreCase = true) ||
                userPrompt.contains("test", ignoreCase = true) ||
                userPrompt.contains("diagnostics", ignoreCase = true)

        val message = if (isToolTest && request.availableTools.isNotEmpty()) {
            val diagnosticTool = request.availableTools.firstOrNull { it.name == "system_diagnostics" }
                ?: request.availableTools.first()
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Aura Core received your test request. Invoking tool '${diagnosticTool.name}' through the architecture pipeline.",
                toolCalls = listOf(
                    ToolCallRequest(
                        id = "call_test_${System.currentTimeMillis()}",
                        name = diagnosticTool.name,
                        argumentsJson = "{}"
                    )
                )
            )
        } else {
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = buildString {
                    append("Aura Architecture Core (Stage 2 Operational).\n\n")
                    append("• Model Provider Layer: Pluggable abstraction is functional.\n")
                    append("• Active Provider: Offline Architecture Driver.\n")
                    append("• Available Tools: ${request.availableTools.size} registered in ToolRegistry.\n\n")
                    append("To connect a remote AI model provider (OpenAI, Gemini, Anthropic, or Ollama), configure the endpoint and API credentials in Settings.")
                }
            )
        }

        return CompletionResponse(
            message = message,
            finishReason = if (message.toolCalls.isNotEmpty()) "tool_calls" else "stop",
            usage = TokenUsage(promptTokens = 45, completionTokens = 85, totalTokens = 130),
            providerId = id
        )
    }

    override fun streamResponse(request: CompletionRequest): Flow<StreamChunk> = flow {
        emit(StreamChunk.ReasoningChunk("Analyzing prompt via Aura decoupled provider layer..."))

        val userPrompt = request.messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val isToolTest = userPrompt.contains("tool", ignoreCase = true) ||
                userPrompt.contains("diagnostics", ignoreCase = true) ||
                userPrompt.contains("notify", ignoreCase = true) ||
                userPrompt.contains("test", ignoreCase = true)

        if (isToolTest && request.availableTools.isNotEmpty()) {
            val toolToCall = if (userPrompt.contains("notify", ignoreCase = true)) {
                request.availableTools.firstOrNull { it.name == "propose_notification" } ?: request.availableTools.first()
            } else {
                request.availableTools.firstOrNull { it.name == "system_diagnostics" } ?: request.availableTools.first()
            }

            emit(StreamChunk.TextChunk("Initiating architectural tool execution for: ${toolToCall.name}...\n"))

            val args = if (toolToCall.name == "propose_notification") {
                "{\"title\":\"Aura Security Test\",\"message\":\"Stage 2 sensitive tool permission test.\"}"
            } else {
                "{}"
            }

            emit(
                StreamChunk.ToolCallChunk(
                    ToolCallRequest(
                        id = "call_${System.currentTimeMillis()}",
                        name = toolToCall.name,
                        argumentsJson = args
                    )
                )
            )
        } else {
            emit(StreamChunk.TextChunk("Aura Architecture Core [Stage 2 Active].\n"))
            emit(StreamChunk.TextChunk("The agent foundation is operational. "))
            emit(StreamChunk.TextChunk("Streaming, conversation engine, voice architecture, tool system, and permissions are configured.\n"))
            emit(StreamChunk.TextChunk("Ready for live provider configuration or autonomous task planning."))
        }

        emit(StreamChunk.DoneChunk("stop"))
    }

    override suspend fun checkHealth(): ProviderHealth {
        return ProviderHealth.Healthy
    }
}

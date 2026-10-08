package com.example.aura.core

/**
 * Universal abstraction for AI Model Providers in Aura.
 * Aura Agent Core and Conversation Engine depend solely on this interface
 * rather than any vendor-specific implementation.
 */
typealias ModelProvider = com.example.aura.core.provider.ModelProvider
typealias OnDeviceModelProvider = com.example.aura.core.provider.OnDeviceModelProvider
typealias ModelCapability = com.example.aura.core.provider.ModelCapability
typealias ModelProviderType = com.example.aura.core.provider.ModelProviderType
typealias ModelExecutionLocation = com.example.aura.core.provider.ModelExecutionLocation
typealias ProviderAuthState = com.example.aura.core.provider.ProviderAuthState
typealias ProviderError = com.example.aura.core.provider.ProviderError

// Minimum required request/response and streaming communication types
typealias CompletionRequest = com.example.aura.core.provider.CompletionRequest
typealias CompletionResponse = com.example.aura.core.provider.CompletionResponse
typealias ChatMessage = com.example.aura.core.provider.ChatMessage
typealias MessageRole = com.example.aura.core.provider.MessageRole
typealias ToolCallRequest = com.example.aura.core.provider.ToolCallRequest
typealias ToolDefinition = com.example.aura.core.provider.ToolDefinition
typealias TokenUsage = com.example.aura.core.provider.TokenUsage
typealias StreamChunk = com.example.aura.core.provider.StreamChunk
typealias StreamingState = com.example.aura.core.provider.StreamingState

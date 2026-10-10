package com.example.aura.core.provider

import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry managing model providers in Aura.
 * Allows switching providers at runtime without modifying the agent core.
 */
class ModelProviderRegistry(
    private val storage: ModelProviderStorage? = null,
    initialConfigs: List<ProviderConfig>? = null,
    initialActiveProviderId: String? = null
) {

    private val _configs = MutableStateFlow<List<ProviderConfig>>(
        initialConfigs ?: storage?.loadAllConfigs(defaultConfigs()) ?: defaultConfigs()
    )
    val configs: StateFlow<List<ProviderConfig>> = _configs.asStateFlow()

    private val _activeProviderId = MutableStateFlow(
        initialActiveProviderId
            ?: storage?.loadActiveProviderId()
            ?: determineDefaultActiveProviderId(_configs.value)
    )
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val providerInstances = ConcurrentHashMap<String, ModelProvider>()

    init {
        // Register default diagnostic provider
        registerProvider(ConfigurableOfflineProvider())

        // Pre-create NetworkModelProvider instances for known network providers
        _configs.value.forEach { cfg ->
            if (cfg.type != ModelProviderType.DIAGNOSTIC_OFFLINE) {
                providerInstances[cfg.id] = NetworkModelProvider(cfg)
            }
        }
    }

    fun registerProvider(provider: ModelProvider) {
        providerInstances[provider.id] = provider
    }

    fun getActiveProvider(): ModelProvider {
        val id = _activeProviderId.value
        return providerInstances[id] ?: providerInstances["diagnostic_offline"] ?: ConfigurableOfflineProvider()
    }

    fun getProvider(id: String): ModelProvider? = providerInstances[id]

    fun setActiveProvider(providerId: String) {
        if (providerInstances.containsKey(providerId) || _configs.value.any { it.id == providerId }) {
            _activeProviderId.value = providerId
            storage?.saveActiveProviderId(providerId)
        }
    }

    fun updateConfig(updated: ProviderConfig) {
        _configs.update { list ->
            list.map { if (it.id == updated.id) updated else it }
        }
        val existing = providerInstances[updated.id]
        if (existing is NetworkModelProvider) {
            existing.updateConfig(updated)
        } else if (updated.type != ModelProviderType.DIAGNOSTIC_OFFLINE) {
            providerInstances[updated.id] = NetworkModelProvider(updated)
        }
        storage?.saveConfig(updated)
    }

    suspend fun checkProviderHealth(providerId: String): ProviderHealth {
        val instance = providerInstances[providerId]
        if (instance != null) {
            return instance.checkHealth()
        }
        val config = _configs.value.find { it.id == providerId }
        return if (config == null) {
            ProviderHealth.Unconfigured("Unknown provider: $providerId")
        } else if (config.apiKey.isBlank() && config.type != ModelProviderType.OLLAMA_LOCAL && config.type != ModelProviderType.DIAGNOSTIC_OFFLINE) {
            ProviderHealth.Unconfigured("API Key not set for ${config.displayName}")
        } else {
            ProviderHealth.Healthy
        }
    }

    fun getProviderAuthState(providerId: String): ProviderAuthState {
        val instance = providerInstances[providerId]
        return instance?.authState ?: ProviderAuthState.Uninitialized
    }

    companion object {
        fun determineDefaultActiveProviderId(configs: List<ProviderConfig>): String {
            val openAiConfig = configs.find { it.id == "openai_provider" }
            if (openAiConfig != null && openAiConfig.apiKey.isNotBlank() && openAiConfig.apiKey != "UNCONFIGURED") {
                return "openai_provider"
            }
            if (BuildConfig.OPENAI_API_KEY.isNotBlank() && BuildConfig.OPENAI_API_KEY != "UNCONFIGURED") {
                return "openai_provider"
            }
            return "diagnostic_offline"
        }

        fun defaultConfigs(): List<ProviderConfig> = listOf(
            ProviderConfig(
                id = "diagnostic_offline",
                type = ModelProviderType.DIAGNOSTIC_OFFLINE,
                displayName = "Aura Architecture Diagnostic (Offline)",
                defaultModel = "aura-stage2-foundation",
                isEnabled = true
            ),
            ProviderConfig(
                id = "gemini_provider",
                type = ModelProviderType.GEMINI,
                displayName = "Google Gemini (REST/SSE)",
                endpointUrl = "https://generativelanguage.googleapis.com/v1beta",
                defaultModel = "gemini-1.5-flash",
                isEnabled = true
            ),
            ProviderConfig(
                id = "openai_provider",
                type = ModelProviderType.OPENAI,
                displayName = "OpenAI",
                endpointUrl = BuildConfig.OPENAI_BASE_URL.ifBlank { "https://api.openai.com/v1" },
                apiKey = if (BuildConfig.OPENAI_API_KEY.isNotBlank() && BuildConfig.OPENAI_API_KEY != "UNCONFIGURED") {
                    BuildConfig.OPENAI_API_KEY
                } else {
                    ""
                },
                defaultModel = BuildConfig.OPENAI_MODEL.ifBlank { "gpt-4o-mini" },
                isEnabled = true
            ),
            ProviderConfig(
                id = "anthropic_provider",
                type = ModelProviderType.ANTHROPIC,
                displayName = "Anthropic Claude",
                endpointUrl = "https://api.anthropic.com/v1",
                defaultModel = "claude-3-5-sonnet-20241022",
                isEnabled = true
            ),
            ProviderConfig(
                id = "ollama_local",
                type = ModelProviderType.OLLAMA_LOCAL,
                displayName = "Ollama (Local / On-Device / LAN)",
                endpointUrl = "http://10.0.2.2:11434/v1",
                defaultModel = "llama3.2:latest",
                isEnabled = true
            ),
            ProviderConfig(
                id = "custom_endpoint",
                type = ModelProviderType.CUSTOM_ENDPOINT,
                displayName = "Custom OpenAI-Compatible Endpoint",
                endpointUrl = "",
                defaultModel = "default",
                isEnabled = false
            )
        )
    }
}

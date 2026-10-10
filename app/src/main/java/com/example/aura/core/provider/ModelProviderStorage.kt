package com.example.aura.core.provider

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Handles persistent storage for Aura model provider configurations and the active provider selection.
 * Uses Android SharedPreferences (Context.MODE_PRIVATE) for atomic, persistent storage across app restarts.
 * API keys and credentials are stored securely in private app storage and never logged or exposed.
 */
class ModelProviderStorage(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Persists the ID of the currently selected active provider separately from provider configurations.
     */
    fun saveActiveProviderId(providerId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, providerId).apply()
    }

    /**
     * Loads the persisted active provider ID, or null if no provider was explicitly saved.
     */
    fun loadActiveProviderId(): String? {
        return prefs.getString(KEY_ACTIVE_PROVIDER, null)?.takeIf { it.isNotBlank() }
    }

    /**
     * Persists a single [ProviderConfig] to Android persistent storage.
     * Never logs or exposes API keys.
     */
    fun saveConfig(config: ProviderConfig) {
        val json = JSONObject().apply {
            put("id", config.id)
            put("type", config.type.name)
            put("displayName", config.displayName)
            put("endpointUrl", config.endpointUrl)
            put("apiKey", config.apiKey)
            put("defaultModel", config.defaultModel)
            put("isEnabled", config.isEnabled)
            put("timeoutMillis", config.timeoutMillis)
        }
        prefs.edit().putString(KEY_CONFIG_PREFIX + config.id, json.toString()).apply()
    }

    /**
     * Loads all saved provider configurations, merging them over [defaults] without overwriting
     * saved values with empty defaults.
     */
    fun loadAllConfigs(defaults: List<ProviderConfig>): List<ProviderConfig> {
        val result = mutableListOf<ProviderConfig>()
        val savedIds = mutableSetOf<String>()

        for (defaultConfig in defaults) {
            val key = KEY_CONFIG_PREFIX + defaultConfig.id
            val savedJsonStr = prefs.getString(key, null)
            if (!savedJsonStr.isNullOrBlank()) {
                try {
                    val json = JSONObject(savedJsonStr)
                    val merged = ProviderConfig(
                        id = defaultConfig.id,
                        type = try {
                            ModelProviderType.valueOf(json.optString("type", defaultConfig.type.name))
                        } catch (_: Exception) {
                            defaultConfig.type
                        },
                        displayName = json.optString("displayName", defaultConfig.displayName).ifBlank { defaultConfig.displayName },
                        endpointUrl = json.optString("endpointUrl", defaultConfig.endpointUrl),
                        apiKey = json.optString("apiKey", defaultConfig.apiKey),
                        defaultModel = json.optString("defaultModel", defaultConfig.defaultModel).ifBlank { defaultConfig.defaultModel },
                        isEnabled = json.optBoolean("isEnabled", defaultConfig.isEnabled),
                        timeoutMillis = json.optLong("timeoutMillis", defaultConfig.timeoutMillis)
                    )
                    result.add(merged)
                    savedIds.add(defaultConfig.id)
                } catch (_: Exception) {
                    result.add(defaultConfig)
                    savedIds.add(defaultConfig.id)
                }
            } else {
                result.add(defaultConfig)
                savedIds.add(defaultConfig.id)
            }
        }

        // Also check if any additional custom provider configs were stored that are not in defaults
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.startsWith(KEY_CONFIG_PREFIX) && value is String && value.isNotBlank()) {
                val providerId = key.removePrefix(KEY_CONFIG_PREFIX)
                if (providerId !in savedIds) {
                    try {
                        val json = JSONObject(value)
                        val config = ProviderConfig(
                            id = json.optString("id", providerId),
                            type = try {
                                ModelProviderType.valueOf(json.optString("type", ModelProviderType.CUSTOM_ENDPOINT.name))
                            } catch (_: Exception) {
                                ModelProviderType.CUSTOM_ENDPOINT
                            },
                            displayName = json.optString("displayName", "Custom Provider"),
                            endpointUrl = json.optString("endpointUrl", ""),
                            apiKey = json.optString("apiKey", ""),
                            defaultModel = json.optString("defaultModel", "default"),
                            isEnabled = json.optBoolean("isEnabled", true),
                            timeoutMillis = json.optLong("timeoutMillis", 30_000L)
                        )
                        result.add(config)
                    } catch (_: Exception) {
                        // Ignore malformed entries
                    }
                }
            }
        }

        return result
    }

    /**
     * Clears all saved provider settings. Primarily for testing.
     */
    @androidx.annotation.VisibleForTesting
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "aura_model_providers_pref"
        const val KEY_ACTIVE_PROVIDER = "aura_active_provider_id"
        const val KEY_CONFIG_PREFIX = "aura_provider_config_"
    }
}

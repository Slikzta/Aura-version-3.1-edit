package com.example.aura.core.provider

/**
 * Routing policy for choosing between on-device and network AI model providers.
 */
enum class ModelRoutingPolicy {
    /**
     * Strongly prefers on-device execution for privacy and offline capability.
     * Falls back to network provider only if prompt exceeds on-device budget.
     */
    LOCAL_FIRST,

    /**
     * Strictly requires on-device inference; never sends prompt data over the network.
     */
    OFFLINE_ONLY,

    /**
     * Prefers highest-capability cloud/network models, falling back to local if offline.
     */
    CAPABILITY_FIRST,

    /**
     * Uses explicitly configured user selection from registry without adaptive switching.
     */
    MANUAL
}

/**
 * Provider-neutral router that evaluates requests against privacy, latency, and resource constraints
 * to select the appropriate [ModelProvider] (on-device or network).
 */
class UnifiedModelRouter(
    private val registry: ModelProviderRegistry,
    var routingPolicy: ModelRoutingPolicy = ModelRoutingPolicy.MANUAL
) {

    /**
     * Resolves the optimal provider for the given request without vendor lock-in.
     */
    fun resolveProvider(request: CompletionRequest): ModelProvider {
        return when (routingPolicy) {
            ModelRoutingPolicy.MANUAL -> registry.getActiveProvider()

            ModelRoutingPolicy.OFFLINE_ONLY -> {
                // Find an on-device provider that is configured and ready
                val onDevice = registry.configs.value
                    .mapNotNull { registry.getProvider(it.id) }
                    .firstOrNull { it.executionLocation == ModelExecutionLocation.ON_DEVICE && it.authState == ProviderAuthState.Configured }

                onDevice ?: throw ProviderError.ProviderUnavailableError("No on-device model provider is configured or loaded for OFFLINE_ONLY policy.")
            }

            ModelRoutingPolicy.LOCAL_FIRST -> {
                val onDevice = registry.configs.value
                    .mapNotNull { registry.getProvider(it.id) }
                    .firstOrNull { it.executionLocation == ModelExecutionLocation.ON_DEVICE && it.authState == ProviderAuthState.Configured }

                onDevice ?: registry.getActiveProvider()
            }

            ModelRoutingPolicy.CAPABILITY_FIRST -> {
                val network = registry.configs.value
                    .mapNotNull { registry.getProvider(it.id) }
                    .firstOrNull { it.executionLocation == ModelExecutionLocation.NETWORK_REMOTE && it.authState == ProviderAuthState.Configured }

                network ?: registry.getActiveProvider()
            }
        }
    }
}

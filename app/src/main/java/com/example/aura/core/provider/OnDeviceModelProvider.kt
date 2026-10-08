package com.example.aura.core.provider

/**
 * Execution venue for model inference.
 * Defines whether a model executes locally on the Android device or via a remote network endpoint.
 */
enum class ModelExecutionLocation {
    /**
     * Executes entirely on-device (e.g. MediaPipe GenAI, ONNX Runtime, Llama.cpp, mobile NPU/GPU).
     * Zero network egress, maximum privacy, functional offline.
     */
    ON_DEVICE,

    /**
     * Executes remotely over the network (e.g. Cloud API, custom local-network Ollama server).
     */
    NETWORK_REMOTE,

    /**
     * Hybrid/adaptive routing: attempts on-device first, falling back to network if capabilities exceed local budget.
     */
    HYBRID_ADAPTIVE
}

/**
 * Hardware acceleration preference for on-device models.
 */
enum class HardwareAcceleration {
    CPU,
    GPU,
    NPU,
    AUTO
}

/**
 * On-device runtime resource budget and status.
 */
data class OnDeviceResourceProfile(
    val maxMemoryMb: Int,
    val contextWindowTokens: Int,
    val acceleration: HardwareAcceleration = HardwareAcceleration.AUTO,
    val isWeightsLoaded: Boolean = false,
    val modelFilePath: String? = null
)

/**
 * Dedicated contract for On-Device Model Providers.
 * Extends the universal [ModelProvider] contract with on-device specific lifecycle hooks:
 * weight loading, hardware acceleration, memory budget management, and thermal considerations.
 */
interface OnDeviceModelProvider : ModelProvider {

    override val executionLocation: ModelExecutionLocation
        get() = ModelExecutionLocation.ON_DEVICE

    /**
     * Resource profile indicating on-device constraints.
     */
    val resourceProfile: OnDeviceResourceProfile

    /**
     * Pre-loads model weights into memory/accelerator ahead of inference.
     */
    suspend fun loadModel(modelPath: String, acceleration: HardwareAcceleration = HardwareAcceleration.AUTO): Result<Unit>

    /**
     * Unloads model weights from memory to reclaim system RAM when idle.
     */
    suspend fun unloadModel()

    /**
     * Current memory footprint in megabytes utilized by loaded weights.
     */
    fun getLoadedMemoryFootprintMb(): Int
}

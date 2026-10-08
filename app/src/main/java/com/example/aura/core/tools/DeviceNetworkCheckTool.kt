package com.example.aura.core.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.aura.core.security.SecurityLevel

/**
 * Checks current active network connectivity.
 * Security: LOW_RISK (telemetry only).
 */
class DeviceNetworkCheckTool : AuraTool {
    override val id: String = "network_check"
    override val name: String = "Network Connectivity"
    override val description: String = "Checks whether internet access is available and identifies network transport type (Wi-Fi, Cellular, Ethernet)."
    override val category: ToolCategory = ToolCategory.SYSTEM
    override val securityLevel: SecurityLevel = SecurityLevel.LOW_RISK

    override val inputSchema: ToolSchema = ToolSchema(parameters = emptyList())

    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult {
        return try {
            val cm = context.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val capabilities = cm?.getNetworkCapabilities(network)

            val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

            val type = when {
                isWifi -> "Wi-Fi"
                isCellular -> "Cellular"
                else -> "Other / Unknown"
            }

            val summary = if (isConnected) {
                "Connected to internet via $type (validated: $isValidated)"
            } else {
                "No active internet connection detected"
            }

            ToolExecutionResult.Success(
                output = summary,
                structuredData = mapOf(
                    "connected" to isConnected,
                    "validated" to isValidated,
                    "transport" to type
                )
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Network check failed: ${e.message}", e)
        }
    }
}

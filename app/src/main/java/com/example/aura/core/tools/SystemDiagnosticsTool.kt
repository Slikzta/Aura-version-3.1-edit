package com.example.aura.core.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.example.aura.core.security.SecurityLevel

/**
 * Diagnostic tool allowing Aura to inspect device runtime state safely.
 * Security: SAFE (read-only system telemetry).
 */
class SystemDiagnosticsTool : AuraTool {
    override val id: String = "system_diagnostics"
    override val name: String = "System Diagnostics"
    override val description: String = "Inspects Android OS version, device hardware model, battery level, and available memory."
    override val category: ToolCategory = ToolCategory.SYSTEM
    override val securityLevel: SecurityLevel = SecurityLevel.SAFE

    override val inputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "include_memory",
                type = "boolean",
                description = "Whether to include JVM heap memory allocation stats",
                required = false
            )
        )
    )

    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult {
        return try {
            val batteryStatus = getBatteryLevel(context.appContext)
            val runtime = Runtime.getRuntime()
            val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
            val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)
            val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
            val uptimeMinutes = (SystemClock.elapsedRealtime() / 1000) / 60

            val resultText = buildString {
                appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Battery Level: $batteryStatus%")
                appendLine("System Uptime: $uptimeMinutes minutes")
                appendLine("App Heap Memory: Free ${freeMemoryMb}MB / Total ${totalMemoryMb}MB (Max: ${maxMemoryMb}MB)")
            }

            ToolExecutionResult.Success(
                output = resultText.trim(),
                structuredData = mapOf(
                    "os_version" to Build.VERSION.RELEASE,
                    "api_level" to Build.VERSION.SDK_INT,
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "battery_pct" to batteryStatus,
                    "uptime_min" to uptimeMinutes
                )
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Failed to gather system telemetry: ${e.message}", e)
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            100 // fallback
        }
    }
}

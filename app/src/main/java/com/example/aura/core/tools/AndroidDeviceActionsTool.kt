package com.example.aura.core.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.example.aura.core.security.SecurityLevel

/**
 * Executes safe Android device actions: clipboard operations, haptic feedback,
 * and system settings routing.
 */
class AndroidDeviceActionsTool : AuraTool {

    override val id: String = "device_actions"
    override val name: String = "Android Device Actions"
    override val description: String = "Performs on-device system actions: copy to clipboard, trigger haptic feedback, or navigate to settings."
    override val category: ToolCategory = ToolCategory.ANDROID_OS
    override val securityLevel: SecurityLevel = SecurityLevel.LOW_RISK

    override val inputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "action",
                type = "string",
                description = "Action: 'copy_to_clipboard', 'vibrate', or 'open_settings'",
                required = true,
                enumValues = listOf("copy_to_clipboard", "vibrate", "open_settings")
            ),
            ToolParameter(
                name = "text",
                type = "string",
                description = "Text payload when copying to clipboard",
                required = false
            ),
            ToolParameter(
                name = "duration_ms",
                type = "integer",
                description = "Vibration duration in milliseconds (default: 200ms)",
                required = false
            )
        )
    )

    override val outputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter("action_executed", "string", "Name of executed action")
        )
    )

    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult {
        val action = arguments["action"]?.toString()?.lowercase()
            ?: return ToolExecutionResult.Failure("Missing 'action' parameter.")

        val appContext = context.appContext

        return try {
            when (action) {
                "copy_to_clipboard" -> {
                    val text = arguments["text"]?.toString() ?: ""
                    val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        ?: return ToolExecutionResult.Failure("ClipboardManager unavailable")
                    val clip = ClipData.newPlainText("Aura Copy", text)
                    clipboard.setPrimaryClip(clip)
                    ToolExecutionResult.Success(
                        output = "Copied ${text.length} characters to system clipboard.",
                        structuredData = mapOf("action_executed" to "copy_to_clipboard", "length" to text.length)
                    )
                }
                "vibrate" -> {
                    val duration = (arguments["duration_ms"] as? Number)?.toLong() ?: 200L
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                        manager?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(duration)
                    }

                    ToolExecutionResult.Success(
                        output = "Vibrated device for ${duration}ms.",
                        structuredData = mapOf("action_executed" to "vibrate", "duration_ms" to duration)
                    )
                }
                "open_settings" -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    appContext.startActivity(intent)
                    ToolExecutionResult.Success(
                        output = "Launched Android system settings activity.",
                        structuredData = mapOf("action_executed" to "open_settings")
                    )
                }
                else -> ToolExecutionResult.Failure("Unsupported device action: '$action'")
            }
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Failed device action '$action': ${e.message}", e)
        }
    }
}

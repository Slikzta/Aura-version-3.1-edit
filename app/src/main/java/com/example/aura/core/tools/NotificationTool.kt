package com.example.aura.core.tools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.aura.core.security.SecurityLevel

/**
 * Real Android Notification dispatch tool.
 * Requires user approval (MEDIUM_RISK) and POST_NOTIFICATIONS runtime permission on Android 13+.
 */
class NotificationTool : AuraTool {

    override val id: String = "notifications"
    override val name: String = "Device Notification Dispatcher"
    override val description: String = "Posts a notification banner on the Android device status bar."
    override val category: ToolCategory = ToolCategory.COMMUNICATION
    override val securityLevel: SecurityLevel = SecurityLevel.MEDIUM_RISK

    override val permissionRequirements: Set<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setOf(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptySet()
    }

    override val inputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "title",
                type = "string",
                description = "Notification headline",
                required = true
            ),
            ToolParameter(
                name = "message",
                type = "string",
                description = "Notification message body",
                required = true
            )
        )
    )

    override val outputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter("posted", "boolean", "Whether notification was successfully shown")
        )
    )

    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult {
        val title = arguments["title"]?.toString() ?: return ToolExecutionResult.Failure("Missing 'title'")
        val message = arguments["message"]?.toString() ?: return ToolExecutionResult.Failure("Missing 'message'")

        val appContext = context.appContext
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return ToolExecutionResult.Failure("NotificationManager is unavailable on device.")

        val channelId = "aura_agent_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Aura Agent Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications and status updates from the Aura autonomous agent."
            }
            notificationManager.createNotificationChannel(channel)
        }

        try {
            val notification = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            val notificationId = (System.currentTimeMillis() % 10000).toInt()
            notificationManager.notify(notificationId, notification)

            return ToolExecutionResult.Success(
                output = "Notification posted successfully: [$title] $message",
                structuredData = mapOf("posted" to true, "notification_id" to notificationId)
            )
        } catch (e: SecurityException) {
            return ToolExecutionResult.Failure("Missing POST_NOTIFICATIONS runtime permission: ${e.message}", e)
        } catch (e: Exception) {
            return ToolExecutionResult.Failure("Failed to post notification: ${e.message}", e)
        }
    }
}

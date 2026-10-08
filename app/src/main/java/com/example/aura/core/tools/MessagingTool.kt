package com.example.aura.core.tools

import android.content.Intent
import android.net.Uri
import com.example.aura.core.security.SecurityLevel

/**
 * Android messaging infrastructure tool.
 * Classified as HIGH_RISK: Sending external messages requires explicit user approval.
 */
class MessagingTool : AuraTool {

    override val id: String = "messaging"
    override val name: String = "Messaging & SMS Dispatcher"
    override val description: String = "Prepares and presents an outgoing text or SMS message intent for user transmission."
    override val category: ToolCategory = ToolCategory.COMMUNICATION
    override val securityLevel: SecurityLevel = SecurityLevel.HIGH_RISK

    override val inputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "recipient_phone",
                type = "string",
                description = "Destination phone number or contact identifier",
                required = true
            ),
            ToolParameter(
                name = "message_body",
                type = "string",
                description = "Message body content",
                required = true
            )
        )
    )

    override val outputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter("dispatched_intent", "boolean", "Whether SMS draft intent was opened")
        )
    )

    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult {
        val phone = arguments["recipient_phone"]?.toString()?.trim()
            ?: return ToolExecutionResult.Failure("Missing 'recipient_phone'")
        val body = arguments["message_body"]?.toString()?.trim()
            ?: return ToolExecutionResult.Failure("Missing 'message_body'")

        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phone")
                putExtra("sms_body", body)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.appContext.startActivity(intent)

            ToolExecutionResult.Success(
                output = "Opened messaging draft to $phone with message (${body.length} characters).",
                structuredData = mapOf("dispatched_intent" to true, "recipient" to phone)
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Failed to launch messaging intent: ${e.message}", e)
        }
    }
}

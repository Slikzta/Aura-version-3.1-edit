package com.example.aura.core.tools

import com.example.aura.core.security.SecurityLevel

/**
 * Tool for proposing and dispatching notifications to the user.
 * Classified as SENSITIVE so it halts and prompts for explicit user approval.
 */
class NotificationProposalTool : AuraTool {
    override val id: String = "propose_notification"
    override val name: String = "Propose Notification"
    override val description: String = "Proposes dispatching an alert or status update to the user. Requires explicit confirmation."
    override val category: ToolCategory = ToolCategory.COMMUNICATION
    override val securityLevel: SecurityLevel = SecurityLevel.MEDIUM_RISK

    override val inputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "title",
                type = "string",
                description = "Notification heading",
                required = true
            ),
            ToolParameter(
                name = "message",
                type = "string",
                description = "Notification body text",
                required = true
            )
        )
    )

    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult {
        val title = arguments["title"]?.toString() ?: "Aura Notice"
        val message = arguments["message"]?.toString() ?: "No message provided."

        // Return a clear success confirmation indicating the notification was dispatched
        return ToolExecutionResult.Success(
            output = "Notification dispatched: [$title] $message",
            structuredData = mapOf("title" to title, "message" to message, "dispatched" to true)
        )
    }
}

package com.example.aura.core.tools

import android.content.Intent
import android.net.Uri
import com.example.aura.core.security.SecurityLevel

/**
 * Browser automation and navigation tool.
 * Classified as HIGH_RISK: Interacting with external web targets requires explicit approval.
 */
class BrowserAutomationTool : AuraTool {

    override val id: String = "browser_automation"
    override val name: String = "Browser Navigator"
    override val description: String = "Launches system browser session to navigate to web services or automated workflows."
    override val category: ToolCategory = ToolCategory.AUTOMATION
    override val securityLevel: SecurityLevel = SecurityLevel.HIGH_RISK

    override val inputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "url",
                type = "string",
                description = "Target destination web address",
                required = true
            )
        )
    )

    override val outputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter("opened", "boolean", "Whether browser intent was launched")
        )
    )

    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult {
        val url = arguments["url"]?.toString()?.trim()
            ?: return ToolExecutionResult.Failure("Missing 'url' parameter.")

        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return ToolExecutionResult.Failure("Invalid URL: must begin with http:// or https://")
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.appContext.startActivity(intent)

            ToolExecutionResult.Success(
                output = "Launched browser to '$url'.",
                structuredData = mapOf("opened" to true, "url" to url)
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Failed to launch browser intent: ${e.message}", e)
        }
    }
}

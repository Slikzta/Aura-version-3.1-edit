package com.example.aura.core.tools

import com.example.aura.core.security.SecurityLevel

/**
 * Architectural specification blueprint for future Skill modules (Stage 2+).
 * Documents parameter schemas, security tiers, and contracts without fake mock executions.
 */
object FutureToolBlueprints {

    val WebResearchDefinition = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "query",
                type = "string",
                description = "Search query or URL to investigate",
                required = true
            ),
            ToolParameter(
                name = "max_sources",
                type = "integer",
                description = "Maximum citations to inspect",
                required = false
            )
        )
    )

    val CalendarIntegrationDefinition = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "action",
                type = "string",
                description = "Action: 'query_events', 'create_event', 'reschedule'",
                required = true,
                enumValues = listOf("query_events", "create_event", "reschedule")
            ),
            ToolParameter(
                name = "start_iso",
                type = "string",
                description = "ISO-8601 timestamp for start time",
                required = false
            ),
            ToolParameter(
                name = "summary",
                type = "string",
                description = "Event summary/title",
                required = false
            )
        )
    )

    val BrowserAutomationDefinition = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "target_url",
                type = "string",
                description = "Web address to automate interaction on",
                required = true
            ),
            ToolParameter(
                name = "action_plan",
                type = "string",
                description = "Structured JSON step instructions",
                required = true
            )
        )
    )
}

/**
 * Stub tool representing planned skills to verify discovery in UI and registry.
 * Clearly informs the user and agent core that this capability is scheduled for Stage 2+.
 */
class Stage2PlannedTool(
    override val id: String,
    override val name: String,
    override val description: String,
    override val category: ToolCategory,
    override val securityLevel: SecurityLevel,
    override val inputSchema: ToolSchema
) : AuraTool {
    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult {
        return ToolExecutionResult.Failure(
            "Capability '$name' is architecturally registered but scheduled for Stage 2 integration."
        )
    }
}

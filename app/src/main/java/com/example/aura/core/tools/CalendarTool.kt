package com.example.aura.core.tools

import android.content.ContentUris
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.aura.core.security.SecurityLevel
import java.util.Date

/**
 * Real Android Calendar integration infrastructure tool.
 * Requires user approval (MEDIUM_RISK) and READ_CALENDAR runtime permission.
 */
class CalendarTool : AuraTool {

    override val id: String = "calendar"
    override val name: String = "Device Calendar Manager"
    override val description: String = "Inspects and queries events from the user's Android calendar."
    override val category: ToolCategory = ToolCategory.PRODUCTIVITY
    override val securityLevel: SecurityLevel = SecurityLevel.MEDIUM_RISK

    override val permissionRequirements: Set<String> = setOf(
        android.Manifest.permission.READ_CALENDAR
    )

    override val inputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "action",
                type = "string",
                description = "Action: 'query_upcoming'",
                required = true,
                enumValues = listOf("query_upcoming")
            ),
            ToolParameter(
                name = "days_ahead",
                type = "integer",
                description = "Number of days ahead to scan (default: 7)",
                required = false
            )
        )
    )

    override val outputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter("event_count", "integer", "Number of calendar events found"),
            ToolParameter("events", "string", "Formatted list of upcoming events")
        )
    )

    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult {
        val appContext = context.appContext

        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return ToolExecutionResult.Failure(
                "Calendar access requires READ_CALENDAR permission. Grant permission in system settings."
            )
        }

        val daysAhead = (arguments["days_ahead"] as? Number)?.toInt() ?: 7
        val nowMillis = System.currentTimeMillis()
        val endMillis = nowMillis + (daysAhead * 24 * 60 * 60 * 1000L)

        return try {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, nowMillis)
            ContentUris.appendId(builder, endMillis)

            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END
            )

            val cursor = appContext.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use {
                val results = mutableListOf<String>()
                val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)

                while (it.moveToNext()) {
                    val title = if (titleIdx >= 0) it.getString(titleIdx) ?: "Untitled Event" else "Untitled"
                    val begin = if (beginIdx >= 0) it.getLong(beginIdx) else 0L
                    results.add("• $title at ${Date(begin)}")
                }

                val summary = if (results.isEmpty()) {
                    "No calendar events found in the next $daysAhead days."
                } else {
                    "Found ${results.size} calendar events:\n" + results.joinToString("\n")
                }

                ToolExecutionResult.Success(
                    output = summary,
                    structuredData = mapOf("event_count" to results.size)
                )
            } ?: ToolExecutionResult.Success("No calendar instances provider available on device.")
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Failed to query calendar: ${e.message}", e)
        }
    }
}

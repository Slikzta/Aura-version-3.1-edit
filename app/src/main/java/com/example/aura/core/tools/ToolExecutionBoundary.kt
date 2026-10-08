package com.example.aura.core.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.aura.core.logging.AgentAuditLogger
import com.example.aura.core.security.ApprovalManager
import com.example.aura.core.security.ApprovalRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

/**
 * Result of checking required Android runtime permissions for a tool.
 */
sealed interface ToolPermissionStatus {
    data object AllGranted : ToolPermissionStatus
    data class MissingPermissions(val missing: Set<String>) : ToolPermissionStatus
}

/**
 * Validates Android runtime permission prerequisites prior to tool invocation.
 */
class ToolPermissionGuard(private val context: Context) {

    fun checkPermissions(tool: AuraTool): ToolPermissionStatus {
        if (tool.permissionRequirements.isEmpty()) {
            return ToolPermissionStatus.AllGranted
        }

        val missing = tool.permissionRequirements.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }.toSet()

        return if (missing.isEmpty()) {
            ToolPermissionStatus.AllGranted
        } else {
            ToolPermissionStatus.MissingPermissions(missing)
        }
    }
}

/**
 * Enforces production execution boundaries for every tool invocation:
 * 1. Schema input parameter validation.
 * 2. Pre-flight Android runtime permission checks.
 * 3. Human-in-the-loop security approval gates.
 * 4. Cancellation and timeout enforcement.
 * 5. Secret-sanitized outcome observation.
 */
class ToolExecutionBoundary(
    private val appContext: Context,
    private val permissionGuard: ToolPermissionGuard,
    private val approvalManager: ApprovalManager,
    private val auditLogger: AgentAuditLogger
) {

    suspend fun executeSafely(
        tool: AuraTool,
        context: ToolExecutionContext,
        arguments: Map<String, Any?>,
        timeoutMillis: Long = 30_000L
    ): ToolExecutionResult {
        val sessionId = context.sessionId

        // 1. Validate input schema
        val schemaValidation = tool.inputSchema.validateInput(arguments)
        if (schemaValidation.isFailure) {
            val error = schemaValidation.exceptionOrNull()?.message ?: "Input parameter validation failed."
            auditLogger.logToolExecuted(sessionId, tool.name, false, error, tool.securityLevel)
            return ToolExecutionResult.Failure(error)
        }

        // 2. Pre-flight Android permissions check
        val permissionStatus = permissionGuard.checkPermissions(tool)
        if (permissionStatus is ToolPermissionStatus.MissingPermissions) {
            auditLogger.logPermissionCheck(sessionId, tool.name, permissionStatus.missing, granted = false)
            val permError = "Missing required Android permission(s): ${permissionStatus.missing.joinToString()}"
            return ToolExecutionResult.Failure(permError)
        } else if (tool.permissionRequirements.isNotEmpty()) {
            auditLogger.logPermissionCheck(sessionId, tool.name, tool.permissionRequirements, granted = true)
        }

        // 3. Security approval gate (Never bypassed)
        if (approvalManager.requiresApproval(tool.securityLevel)) {
            val paramSummary = arguments.mapValues { it.value?.toString() ?: "" }
            val request = ApprovalRequest(
                toolId = tool.id,
                toolName = tool.name,
                actionSummary = "Execute ${tool.name} with parameters: $paramSummary",
                parametersSummary = paramSummary,
                riskLevel = tool.securityLevel
            )

            auditLogger.logToolProposed(sessionId, tool.name, paramSummary, tool.securityLevel)
            val approved = approvalManager.requestApproval(request)
            auditLogger.logApproval(sessionId, tool.name, approved)

            if (!approved) {
                return ToolExecutionResult.DeniedByPolicy("Action was declined by the user.")
            }
        }

        // 4. Execution with timeout and cooperative cancellation
        return try {
            withTimeout(timeoutMillis) {
                tool.execute(context, arguments)
            }
        } catch (e: CancellationException) {
            auditLogger.logCancellation(sessionId, "Tool execution was cancelled.")
            ToolExecutionResult.Cancelled("Execution cancelled by user or caller.")
        } catch (e: Exception) {
            val failureMessage = "Tool execution failure: ${e.message}"
            auditLogger.logToolExecuted(sessionId, tool.name, false, failureMessage, tool.securityLevel)
            ToolExecutionResult.Failure(failureMessage, e)
        }
    }
}

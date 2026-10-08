package com.example.aura.core.logging

import com.example.aura.core.security.SecretSanitizer
import com.example.aura.core.security.SecurityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Audit logging engine for Aura.
 * Allows the user to inspect the agent's internal decisions, tool calls, and security checks.
 *
 * Guarantees that credentials, API keys, and authorization headers are never written to logs.
 */
class AgentAuditLogger(
    private val onPersistEntry: (suspend (AuditLogEntry) -> Unit)? = null
) {
    private val _logs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val logs: StateFlow<List<AuditLogEntry>> = _logs.asStateFlow()

    private val maxInMemoryLogs = 500

    fun log(entry: AuditLogEntry) {
        // Enforce secret sanitization before writing to in-memory state or persistent storage
        val sanitizedEntry = entry.copy(
            message = SecretSanitizer.sanitize(entry.message),
            metadata = SecretSanitizer.sanitizeMap(entry.metadata)
        )

        _logs.update { current ->
            (listOf(sanitizedEntry) + current).take(maxInMemoryLogs)
        }
    }

    fun logUserRequest(sessionId: String?, input: String) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = AuditEventType.USER_REQUEST,
                source = "User",
                message = "User input received: $input",
                securityLevel = SecurityLevel.SAFE
            )
        )
    }

    fun logModelRequest(sessionId: String?, providerName: String, promptPreview: String, messageCount: Int) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = AuditEventType.MODEL_REQUEST,
                source = providerName,
                message = "Dispatching request ($messageCount messages) to $providerName",
                metadata = mapOf("preview" to promptPreview.take(120)),
                securityLevel = SecurityLevel.SAFE
            )
        )
    }

    fun logModelResponse(sessionId: String?, providerName: String, finishReason: String?, tokenCount: Int) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = AuditEventType.MODEL_RESPONSE,
                source = providerName,
                message = "Model completed response (finish: $finishReason, tokens: $tokenCount)",
                metadata = mapOf("finish_reason" to (finishReason ?: "unknown")),
                securityLevel = SecurityLevel.SAFE
            )
        )
    }

    fun logStreamingState(sessionId: String?, stateDescription: String) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = AuditEventType.STREAMING_STATE,
                source = "StreamingEngine",
                message = stateDescription,
                securityLevel = SecurityLevel.SAFE
            )
        )
    }

    fun logReasoning(sessionId: String?, source: String, thought: String) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = AuditEventType.REASONING_STEP,
                source = source,
                message = thought,
                securityLevel = SecurityLevel.SAFE
            )
        )
    }

    fun logToolSelection(sessionId: String?, toolId: String, toolName: String) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = AuditEventType.TOOL_SELECTED,
                source = "ToolDispatcher",
                message = "Selected tool: $toolName ($toolId)",
                securityLevel = SecurityLevel.SAFE
            )
        )
    }

    fun logPermissionCheck(sessionId: String?, toolName: String, requiredPermissions: Set<String>, granted: Boolean) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = AuditEventType.PERMISSION_CHECK,
                source = "PermissionGuard",
                message = if (granted) "Permissions granted for $toolName" else "Missing permissions for $toolName: ${requiredPermissions.joinToString()}",
                metadata = mapOf("permissions" to requiredPermissions.joinToString(), "granted" to granted.toString()),
                securityLevel = SecurityLevel.SAFE
            )
        )
    }

    fun logToolProposed(sessionId: String?, toolName: String, params: Map<String, String>, level: SecurityLevel) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = AuditEventType.TOOL_PROPOSED,
                source = "ToolDispatcher",
                message = "Agent proposed tool: $toolName",
                metadata = params,
                securityLevel = level
            )
        )
    }

    fun logToolExecuted(sessionId: String?, toolName: String, success: Boolean, details: String, level: SecurityLevel) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = if (success) AuditEventType.TOOL_EXECUTED else AuditEventType.TOOL_FAILED,
                source = toolName,
                message = if (success) "Executed successfully: $details" else "Execution failed: $details",
                securityLevel = level
            )
        )
    }

    fun logApproval(sessionId: String?, toolName: String, approved: Boolean) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = if (approved) AuditEventType.APPROVAL_GRANTED else AuditEventType.APPROVAL_REJECTED,
                source = "ApprovalManager",
                message = if (approved) "User granted approval for $toolName" else "User rejected $toolName",
                securityLevel = SecurityLevel.MEDIUM_RISK
            )
        )
    }

    fun logCancellation(sessionId: String?, reason: String) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = AuditEventType.CANCELLED,
                source = "SessionManager",
                message = "Operation cancelled: $reason",
                securityLevel = SecurityLevel.SAFE
            )
        )
    }

    fun logInterruption(sessionId: String?, reason: String) {
        log(
            AuditLogEntry(
                sessionId = sessionId,
                type = AuditEventType.SESSION_INTERRUPTED,
                source = "SessionManager",
                message = "Agent operation interrupted: $reason",
                securityLevel = SecurityLevel.SAFE
            )
        )
    }

    fun clear() {
        _logs.value = emptyList()
    }
}

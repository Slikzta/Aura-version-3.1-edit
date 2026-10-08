package com.example.aura.core.logging

import com.example.aura.core.security.SecurityLevel
import java.util.UUID

/**
 * Categorization of agent activities for inspection.
 */
enum class AuditEventType {
    USER_REQUEST,
    MODEL_REQUEST,
    MODEL_RESPONSE,
    STREAMING_STATE,
    REASONING_STEP,
    TOOL_PROPOSED,
    TOOL_SELECTED,
    PERMISSION_CHECK,
    APPROVAL_REQUESTED,
    APPROVAL_GRANTED,
    APPROVAL_REJECTED,
    TOOL_EXECUTED,
    TOOL_FAILED,
    MEMORY_STORED,
    MEMORY_ACCESSED,
    SESSION_STARTED,
    SESSION_INTERRUPTED,
    SESSION_COMPLETED,
    PROVIDER_CALL,
    PROVIDER_ERROR,
    CANCELLED,
    SYSTEM_DIAGNOSTIC
}

/**
 * Structured audit record of an agent thought, action, or state change.
 */
data class AuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val sessionId: String? = null,
    val type: AuditEventType,
    val source: String,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
    val securityLevel: SecurityLevel = SecurityLevel.SAFE
)

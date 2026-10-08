package com.example.aura.core.security

import java.util.UUID

/**
 * Lifecycle status of an approval request.
 */
enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED,
    CANCELLED
}

/**
 * User-configurable security policy governing agent autonomy.
 */
enum class ApprovalPolicy {
    /** Prompt user for SENSITIVE and CRITICAL actions. SAFE and LOW_RISK are auto-approved. */
    STANDARD,
    /** Prompt user for all actions except SAFE. */
    STRICT,
    /** Zero-trust: Prompt user for every single tool execution, even SAFE ones. */
    ZERO_TRUST
}

/**
 * Encapsulates an action proposal requiring human-in-the-loop permission.
 */
data class ApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val toolId: String,
    val toolName: String,
    val actionSummary: String,
    val parametersSummary: Map<String, String>,
    val riskLevel: SecurityLevel,
    val timestampMillis: Long = System.currentTimeMillis(),
    val status: ApprovalStatus = ApprovalStatus.PENDING
)

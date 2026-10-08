package com.example.aura.core.security

/**
 * Security risk classification for agent actions and tool invocations.
 * Governs whether an action can execute autonomously or must halt for user approval.
 */
enum class SecurityLevel {
    /**
     * Read-only, harmless local queries (e.g., querying system time, reading battery level).
     * Reading authorized local application data, normal conversation operations.
     */
    SAFE,

    /**
     * Low-risk operations with limited side effects (e.g., temporary cache reads, local search).
     */
    LOW_RISK,

    /**
     * Operations that modify local data, create files, send notifications, or modify user data.
     * Always halts execution and awaits explicit user confirmation in the UI.
     */
    MEDIUM_RISK,

    /**
     * Backwards-compatible Stage 1 alias for MEDIUM_RISK operations.
     */
    SENSITIVE,

    /**
     * High-impact operations (e.g., sending messages, making purchases, deleting data,
     * changing system settings, performing consequential external actions).
     */
    HIGH_RISK,

    /**
     * Backwards-compatible Stage 1 alias for HIGH_RISK operations.
     */
    CRITICAL;

    val isHighImpact: Boolean
        get() = this == HIGH_RISK || this == CRITICAL

    val isMediumImpact: Boolean
        get() = this == MEDIUM_RISK || this == SENSITIVE

    val isLowImpact: Boolean
        get() = this == LOW_RISK || this == SAFE
}

package com.example.aura.core.agent

import com.example.aura.core.security.SecurityLevel
import java.util.UUID

/**
 * High-level state of the Aura autonomous agent runtime.
 */
enum class AgentState {
    IDLE,
    THINKING,
    PLANNING,
    EXECUTING_TOOL,
    AWAITING_APPROVAL,
    INTERRUPTED,
    ERROR
}

/**
 * A discrete step in an autonomous multi-step execution plan.
 */
data class AgentStep(
    val id: String = UUID.randomUUID().toString(),
    val stepIndex: Int,
    val description: String,
    val toolId: String? = null,
    val status: StepStatus = StepStatus.PENDING,
    val resultSummary: String? = null
)

enum class StepStatus {
    PENDING,
    EXECUTING,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Multi-step plan created by the agent to achieve an autonomous objective.
 */
data class AgentPlan(
    val id: String = UUID.randomUUID().toString(),
    val goalTitle: String,
    val steps: List<AgentStep>,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Sealed event stream emitted by the Aura Agent runtime.
 */
sealed interface AgentEvent {
    data class StateChanged(val state: AgentState) : AgentEvent
    data class Reasoning(val thought: String) : AgentEvent
    data class TextStreamChunk(val text: String) : AgentEvent
    data class ToolCallRequested(val toolName: String, val level: SecurityLevel) : AgentEvent
    data class ToolExecutionCompleted(val toolName: String, val success: Boolean, val output: String) : AgentEvent
    data class ApprovalRequired(val requestId: String, val toolName: String) : AgentEvent
    data class PlanUpdated(val plan: AgentPlan) : AgentEvent
    data class Interrupted(val reason: String) : AgentEvent
    data class ErrorOccurred(val message: String, val cause: Throwable? = null) : AgentEvent
}

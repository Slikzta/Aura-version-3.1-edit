package com.example.aura.core.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Standard phases of Aura's autonomous execution loop:
 * UNDERSTAND → PLAN → SELECT_TOOL → CHECK_PERMISSION → REQUEST_APPROVAL_IF_REQUIRED
 * → EXECUTE → OBSERVE_RESULT → UPDATE_STATE → CONTINUE_OR_RESPOND.
 */
enum class OrchestrationPhase {
    IDLE,
    UNDERSTAND,
    PLAN,
    SELECT_TOOL,
    CHECK_PERMISSION,
    REQUEST_APPROVAL_IF_REQUIRED,
    EXECUTE,
    OBSERVE_RESULT,
    UPDATE_STATE,
    CONTINUE_OR_RESPOND,
    INTERRUPTED,
    ERROR
}

/**
 * Audit record of an individual phase transition during execution.
 */
data class OrchestrationStepRecord(
    val phase: OrchestrationPhase,
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String,
    val toolId: String? = null
)

/**
 * Real-time observable state of the agent orchestration engine.
 */
data class OrchestrationState(
    val currentPhase: OrchestrationPhase = OrchestrationPhase.IDLE,
    val activeToolName: String? = null,
    val isApprovalPending: Boolean = false,
    val lastObservation: String? = null,
    val history: List<OrchestrationStepRecord> = emptyList()
)

/**
 * Manages the state machine and phase progression of Aura's autonomous loop.
 */
class AuraOrchestrator {

    private val _state = MutableStateFlow(OrchestrationState())
    val state: StateFlow<OrchestrationState> = _state.asStateFlow()

    fun transitionTo(phase: OrchestrationPhase, summary: String, toolId: String? = null) {
        val record = OrchestrationStepRecord(
            phase = phase,
            summary = summary,
            toolId = toolId
        )
        _state.update { current ->
            current.copy(
                currentPhase = phase,
                activeToolName = toolId ?: current.activeToolName,
                isApprovalPending = phase == OrchestrationPhase.REQUEST_APPROVAL_IF_REQUIRED,
                history = (current.history + record).takeLast(50)
            )
        }
    }

    fun recordObservation(observation: String) {
        _state.update { it.copy(lastObservation = observation) }
    }

    fun reset() {
        _state.value = OrchestrationState()
    }
}

package com.example.aura.core.security

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates user approval for sensitive agent actions.
 * Supports suspending tool execution until the user explicitly grants or denies permission.
 */
class ApprovalManager {

    private val _pendingRequests = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val pendingRequests: StateFlow<List<ApprovalRequest>> = _pendingRequests.asStateFlow()

    private val _currentPolicy = MutableStateFlow(ApprovalPolicy.STANDARD)
    val currentPolicy: StateFlow<ApprovalPolicy> = _currentPolicy.asStateFlow()

    // Map of active CompletableDeferred completions indexed by request ID
    private val deferredMap = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /**
     * Determines whether the given risk level requires explicit user approval under the current policy.
     */
    fun requiresApproval(level: SecurityLevel): Boolean {
        return when (_currentPolicy.value) {
            ApprovalPolicy.STANDARD -> level.isMediumImpact || level.isHighImpact
            ApprovalPolicy.STRICT -> !level.isLowImpact
            ApprovalPolicy.ZERO_TRUST -> true
        }
    }

    /**
     * Requests user approval. If approval is required by policy, this function suspends
     * until [resolveRequest] is called from the UI.
     */
    suspend fun requestApproval(request: ApprovalRequest): Boolean {
        if (!requiresApproval(request.riskLevel)) {
            return true
        }

        val deferred = CompletableDeferred<Boolean>()
        deferredMap[request.id] = deferred

        _pendingRequests.update { it + request }

        return try {
            deferred.await()
        } finally {
            deferredMap.remove(request.id)
            _pendingRequests.update { list -> list.filterNot { it.id == request.id } }
        }
    }

    /**
     * Resolves a pending approval request when the user acts in the UI.
     */
    fun resolveRequest(requestId: String, approved: Boolean) {
        val deferred = deferredMap[requestId]
        deferred?.complete(approved)
        _pendingRequests.update { list -> list.filterNot { it.id == requestId } }
    }

    /**
     * Cancels all pending requests, e.g. when an agent session is interrupted by the user.
     */
    fun cancelAllPending() {
        deferredMap.forEach { (_, deferred) ->
            deferred.complete(false)
        }
        deferredMap.clear()
        _pendingRequests.value = emptyList()
    }

    fun setPolicy(policy: ApprovalPolicy) {
        _currentPolicy.value = policy
    }
}

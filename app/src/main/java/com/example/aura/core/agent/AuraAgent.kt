package com.example.aura.core.agent

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.aura.core.ModelProvider
import com.example.aura.core.logging.AgentAuditLogger
import com.example.aura.core.provider.ChatMessage
import com.example.aura.core.provider.CompletionRequest
import com.example.aura.core.provider.MessageRole
import com.example.aura.core.provider.ModelProviderRegistry
import com.example.aura.core.provider.StreamChunk
import com.example.aura.core.provider.StreamingEngine
import com.example.aura.core.security.ApprovalManager
import com.example.aura.core.security.ApprovalRequest
import com.example.aura.core.session.ConversationSession
import com.example.aura.core.session.SessionManager
import com.example.aura.core.session.SessionState
import com.example.aura.core.tools.ToolExecutionContext
import com.example.aura.core.tools.ToolExecutionResult
import com.example.aura.core.tools.ToolRegistry
import com.example.aura.data.entities.MessageEntity
import com.example.aura.data.entities.SessionEntity
import com.example.aura.data.repository.AuraRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Aura Autonomous Agent Core Engine.
 *
 * Operates strictly decoupled from the Android UI layer and specific AI vendors.
 * Depends directly on the provider-neutral [ModelProvider] interface.
 * Coordinates the full orchestration loop:
 * UNDERSTAND → PLAN → SELECT TOOL → CHECK PERMISSION → REQUEST APPROVAL IF REQUIRED
 * → EXECUTE → OBSERVE RESULT → UPDATE STATE → CONTINUE OR RESPOND.
 */
class AuraAgent(
    private val appContext: Context,
    private val repository: AuraRepository,
    var modelProvider: ModelProvider,
    val toolRegistry: ToolRegistry,
    val approvalManager: ApprovalManager,
    private val auditLogger: AgentAuditLogger,
    val sessionManager: SessionManager,
    val streamingEngine: StreamingEngine = StreamingEngine(),
    val orchestrator: AuraOrchestrator = AuraOrchestrator(),
    private val providerRegistry: ModelProviderRegistry? = null
) {

    // Secondary constructor preserving existing registry-based instantiation
    constructor(
        appContext: Context,
        repository: AuraRepository,
        providerRegistry: ModelProviderRegistry,
        toolRegistry: ToolRegistry,
        approvalManager: ApprovalManager,
        auditLogger: AgentAuditLogger,
        sessionManager: SessionManager,
        streamingEngine: StreamingEngine = StreamingEngine(),
        orchestrator: AuraOrchestrator = AuraOrchestrator()
    ) : this(
        appContext = appContext,
        repository = repository,
        modelProvider = providerRegistry.getActiveProvider(),
        toolRegistry = toolRegistry,
        approvalManager = approvalManager,
        auditLogger = auditLogger,
        sessionManager = sessionManager,
        streamingEngine = streamingEngine,
        orchestrator = orchestrator,
        providerRegistry = providerRegistry
    )

    fun updateModelProvider(newProvider: ModelProvider) {
        this.modelProvider = newProvider
    }

    private val agentScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _activePlan = MutableStateFlow<AgentPlan?>(null)
    val activePlan: StateFlow<AgentPlan?> = _activePlan.asStateFlow()

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val sandboxDir: File by lazy {
        File(appContext.filesDir, "aura_sandbox").apply { if (!exists()) mkdirs() }
    }

    private var lastUserPrompt: String? = null

    /**
     * Ingests a user message and runs the autonomous agent interaction cycle.
     */
    fun processUserInput(sessionId: String, userText: String) {
        lastUserPrompt = userText
        val session = sessionManager.getSession(sessionId) ?: sessionManager.activeSession.value

        val job = agentScope.launch {
            try {
                _agentState.value = AgentState.THINKING
                session.setState(SessionState.PROCESSING)
                _events.tryEmit(AgentEvent.StateChanged(AgentState.THINKING))

                // Phase 1: UNDERSTAND
                orchestrator.transitionTo(
                    OrchestrationPhase.UNDERSTAND,
                    "Parsing user intent and contextual memory: '${userText.take(60)}'"
                )
                auditLogger.logUserRequest(session.id, userText)

                // 1. Persist user message to Room
                val userMsgEntity = MessageEntity(
                    sessionId = session.id,
                    role = MessageRole.USER.name,
                    content = userText
                )
                repository.saveMessage(userMsgEntity)
                repository.saveSession(
                    SessionEntity(
                        id = session.id,
                        title = userText.take(30),
                        mode = session.mode.value.name,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                // Phase 2: PLAN & RETRIEVE CONTEXT
                orchestrator.transitionTo(
                    OrchestrationPhase.PLAN,
                    "Querying long-term memory vault and assembling model prompt."
                )

                val memories = repository.getAllMemoriesSync()
                val memoryContext = if (memories.isNotEmpty()) {
                    "Long-term User Memory & Preferences:\n" +
                            memories.joinToString("\n") { "• [${it.category}] ${it.key}: ${it.content}" }
                } else {
                    "No long-term memories stored yet."
                }

                val systemPrompt = buildString {
                    appendLine("You are Aura, an autonomous personal AI agent running on Android.")
                    appendLine("Your role is to assist the user proactively, execute tasks, and protect user security.")
                    appendLine(memoryContext)
                }

                val pastMessages = repository.getMessagesForSessionSync(session.id).map {
                    ChatMessage(
                        role = MessageRole.valueOf(it.role),
                        content = it.content
                    )
                }

                val availableTools = toolRegistry.toToolDefinitions()
                val request = CompletionRequest(
                    messages = pastMessages,
                    systemPrompt = systemPrompt,
                    availableTools = availableTools
                )

                val provider: ModelProvider = providerRegistry?.getActiveProvider() ?: modelProvider
                auditLogger.logModelRequest(
                    sessionId = session.id,
                    providerName = provider.name,
                    promptPreview = userText,
                    messageCount = pastMessages.size
                )

                session.setState(SessionState.STREAMING)
                session.clearStreamingText()

                val accumulatedContent = StringBuilder()
                val requestedToolCalls = mutableListOf<com.example.aura.core.provider.ToolCallRequest>()

                // Use the production StreamingEngine
                streamingEngine.stream(request, provider).collect { chunk ->
                    when (chunk) {
                        is StreamChunk.ReasoningChunk -> {
                            _events.tryEmit(AgentEvent.Reasoning(chunk.thought))
                            auditLogger.logReasoning(session.id, provider.name, chunk.thought)
                        }
                        is StreamChunk.TextChunk -> {
                            accumulatedContent.append(chunk.text)
                            session.appendStreamingToken(chunk.text)
                            _events.tryEmit(AgentEvent.TextStreamChunk(chunk.text))
                        }
                        is StreamChunk.ToolCallChunk -> {
                            requestedToolCalls.add(chunk.toolCall)
                        }
                        is StreamChunk.DoneChunk -> {
                            auditLogger.logModelResponse(session.id, provider.name, chunk.finishReason, accumulatedContent.length)
                        }
                        is StreamChunk.ErrorChunk -> {
                            throw chunk.throwable
                        }
                        else -> Unit
                    }
                }

                val assistantText = accumulatedContent.toString()

                if (assistantText.isNotBlank()) {
                    repository.saveMessage(
                        MessageEntity(
                            sessionId = session.id,
                            role = MessageRole.ASSISTANT.name,
                            content = assistantText
                        )
                    )
                }

                // Handle Tool Calls through the complete Orchestration Cycle
                if (requestedToolCalls.isNotEmpty()) {
                    for (toolCall in requestedToolCalls) {
                        handleToolCall(session, toolCall)
                    }
                }

                orchestrator.transitionTo(
                    OrchestrationPhase.CONTINUE_OR_RESPOND,
                    "Execution loop complete. Agent waiting for next prompt."
                )

                _agentState.value = AgentState.IDLE
                session.setState(SessionState.IDLE)
                _events.tryEmit(AgentEvent.StateChanged(AgentState.IDLE))

            } catch (e: CancellationException) {
                _agentState.value = AgentState.INTERRUPTED
                session.setState(SessionState.INTERRUPTED)
                orchestrator.transitionTo(OrchestrationPhase.INTERRUPTED, "User interrupted agent session.")
                auditLogger.logCancellation(session.id, "Session execution cancelled by user")
                _events.tryEmit(AgentEvent.Interrupted("Execution stopped"))
            } catch (e: Exception) {
                _agentState.value = AgentState.ERROR
                session.setState(SessionState.ERROR)
                orchestrator.transitionTo(OrchestrationPhase.ERROR, "Agent error: ${e.message}")
                auditLogger.log(
                    com.example.aura.core.logging.AuditLogEntry(
                        sessionId = session.id,
                        type = com.example.aura.core.logging.AuditEventType.PROVIDER_ERROR,
                        source = "AuraAgent",
                        message = "Error in agent loop: ${e.message}",
                        securityLevel = com.example.aura.core.security.SecurityLevel.SAFE
                    )
                )
                _events.tryEmit(AgentEvent.ErrorOccurred("Agent error: ${e.message}", e))
            }
        }

        session.setActiveJob(job)
    }

    private suspend fun handleToolCall(
        session: ConversationSession,
        toolCall: com.example.aura.core.provider.ToolCallRequest
    ) {
        // Phase 3: SELECT TOOL
        orchestrator.transitionTo(
            OrchestrationPhase.SELECT_TOOL,
            "Model selected tool: '${toolCall.name}'",
            toolId = toolCall.name
        )
        auditLogger.logToolSelection(session.id, toolCall.name, toolCall.name)

        val tool = toolRegistry.getTool(toolCall.name)
        if (tool == null) {
            auditLogger.logToolExecuted(session.id, toolCall.name, false, "Unknown tool", com.example.aura.core.security.SecurityLevel.SAFE)
            return
        }

        // Parse arguments & validate input against schema
        val args = try {
            val json = JSONObject(toolCall.argumentsJson)
            val map = mutableMapOf<String, Any?>()
            json.keys().forEach { key -> map[key] = json.opt(key) }
            map
        } catch (_: Exception) {
            emptyMap()
        }

        val validation = tool.inputSchema.validateInput(args)
        if (validation.isFailure) {
            val errMsg = validation.exceptionOrNull()?.message ?: "Invalid arguments"
            auditLogger.logToolExecuted(session.id, tool.name, false, errMsg, tool.securityLevel)
            return
        }

        val paramSummary = args.mapValues { it.value?.toString() ?: "" }
        auditLogger.logToolProposed(session.id, tool.name, paramSummary, tool.securityLevel)

        // Phase 4: CHECK PERMISSION (Android runtime permissions)
        orchestrator.transitionTo(
            OrchestrationPhase.CHECK_PERMISSION,
            "Validating system permissions for tool '${tool.name}'",
            toolId = tool.id
        )

        val missingPermissions = tool.permissionRequirements.filter { perm ->
            ContextCompat.checkSelfPermission(appContext, perm) != PackageManager.PERMISSION_GRANTED
        }.toSet()

        if (missingPermissions.isNotEmpty()) {
            auditLogger.logPermissionCheck(session.id, tool.name, missingPermissions, granted = false)
            val permDeniedMsg = "Tool '${tool.name}' requires Android permission(s): ${missingPermissions.joinToString()}"
            repository.saveMessage(
                MessageEntity(
                    sessionId = session.id,
                    role = MessageRole.TOOL.name,
                    content = permDeniedMsg
                )
            )
            return
        } else if (tool.permissionRequirements.isNotEmpty()) {
            auditLogger.logPermissionCheck(session.id, tool.name, tool.permissionRequirements, granted = true)
        }

        // Phase 5: REQUEST APPROVAL IF REQUIRED (Never bypassed)
        val requiresApproval = approvalManager.requiresApproval(tool.securityLevel)
        if (requiresApproval) {
            _agentState.value = AgentState.AWAITING_APPROVAL
            session.setState(SessionState.AWAITING_APPROVAL)
            orchestrator.transitionTo(
                OrchestrationPhase.REQUEST_APPROVAL_IF_REQUIRED,
                "Halting for human approval (Risk Level: ${tool.securityLevel.name})",
                toolId = tool.id
            )
            _events.tryEmit(AgentEvent.StateChanged(AgentState.AWAITING_APPROVAL))

            val approvalRequest = ApprovalRequest(
                toolId = tool.id,
                toolName = tool.name,
                actionSummary = "Execute '${tool.name}' with arguments: $paramSummary",
                parametersSummary = paramSummary,
                riskLevel = tool.securityLevel
            )

            _events.tryEmit(AgentEvent.ApprovalRequired(approvalRequest.id, tool.name))
            val approved = approvalManager.requestApproval(approvalRequest)
            auditLogger.logApproval(session.id, tool.name, approved)

            if (!approved) {
                val denialMsg = "Tool '${tool.name}' was declined by user."
                repository.saveMessage(
                    MessageEntity(
                        sessionId = session.id,
                        role = MessageRole.TOOL.name,
                        content = denialMsg
                    )
                )
                return
            }
        }

        // Phase 6: EXECUTE
        _agentState.value = AgentState.EXECUTING_TOOL
        orchestrator.transitionTo(
            OrchestrationPhase.EXECUTE,
            "Executing '${tool.name}'",
            toolId = tool.id
        )

        val context = ToolExecutionContext(
            appContext = appContext,
            sandboxDirectory = sandboxDir,
            sessionId = session.id
        )

        val result = tool.execute(context, args)
        val (success, output) = when (result) {
            is ToolExecutionResult.Success -> Pair(true, result.output)
            is ToolExecutionResult.Failure -> Pair(false, "Failed: ${result.errorMessage}")
            is ToolExecutionResult.DeniedByPolicy -> Pair(false, "Denied: ${result.reason}")
            is ToolExecutionResult.Cancelled -> Pair(false, "Cancelled: ${result.reason}")
        }

        // Phase 7: OBSERVE RESULT
        orchestrator.transitionTo(
            OrchestrationPhase.OBSERVE_RESULT,
            "Observed outcome: ${output.take(80)}",
            toolId = tool.id
        )
        orchestrator.recordObservation(output)
        auditLogger.logToolExecuted(session.id, tool.name, success, output, tool.securityLevel)
        _events.tryEmit(AgentEvent.ToolExecutionCompleted(tool.name, success, output))

        // Phase 8: UPDATE STATE
        orchestrator.transitionTo(
            OrchestrationPhase.UPDATE_STATE,
            "Updating conversation session records with tool outcome.",
            toolId = tool.id
        )

        repository.saveMessage(
            MessageEntity(
                sessionId = session.id,
                role = MessageRole.TOOL.name,
                content = output,
                toolResultJson = output
            )
        )
    }

    /**
     * Retries the last user message.
     */
    fun retryLastMessage(sessionId: String) {
        lastUserPrompt?.let { processUserInput(sessionId, it) }
    }

    fun interrupt() {
        sessionManager.interruptActiveSession("User tap on Interrupt button")
        approvalManager.cancelAllPending()
        _agentState.value = AgentState.INTERRUPTED
        orchestrator.transitionTo(OrchestrationPhase.INTERRUPTED, "Interrupted by user action.")
    }

    fun createAutonomousPlan(goalTitle: String, steps: List<String>) {
        val planSteps = steps.mapIndexed { index, desc ->
            AgentStep(
                stepIndex = index + 1,
                description = desc,
                status = StepStatus.PENDING
            )
        }
        val plan = AgentPlan(goalTitle = goalTitle, steps = planSteps)
        _activePlan.value = plan
        _events.tryEmit(AgentEvent.PlanUpdated(plan))
        auditLogger.logReasoning(null, "Planner", "Generated autonomous plan for '$goalTitle' with ${steps.size} steps.")
    }
}

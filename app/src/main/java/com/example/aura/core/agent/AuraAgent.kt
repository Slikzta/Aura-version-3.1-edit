package com.example.aura.core.agent

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.aura.core.logging.AgentAuditLogger
import com.example.aura.core.provider.ChatMessage
import com.example.aura.core.provider.CompletionRequest
import com.example.aura.core.provider.MessageRole
import com.example.aura.core.provider.ModelProvider
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Aura Autonomous Agent Core Engine.
 *
 * Operates strictly decoupled from the Android UI layer and specific AI vendors.
 * Coordinates the full orchestration loop:
 * UNDERSTAND → PLAN → SELECT TOOL → CHECK PERMISSION → REQUEST APPROVAL IF REQUIRED
 * → EXECUTE → OBSERVE RESULT → UPDATE STATE → CONTINUE OR RESPOND.
 */
class AuraAgent(
    private val appContext: Context,
    private val repository: AuraRepository,
    private val providerRegistry: ModelProviderRegistry,
    val toolRegistry: ToolRegistry,
    val approvalManager: ApprovalManager,
    private val auditLogger: AgentAuditLogger,
    val sessionManager: SessionManager,
    val streamingEngine: StreamingEngine = StreamingEngine(),
    val orchestrator: AuraOrchestrator = AuraOrchestrator(),
    val voiceManager: com.example.aura.core.voice.VoiceInteractionManager? = null,
    private val coroutineDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main.immediate
) {

    private var customModelProvider: ModelProvider? = null

    var modelProvider: ModelProvider
        get() = customModelProvider ?: providerRegistry.getActiveProvider()
        private set(value) {
            customModelProvider = value
        }

    constructor(
        appContext: Context,
        repository: AuraRepository,
        modelProvider: ModelProvider,
        toolRegistry: ToolRegistry,
        approvalManager: ApprovalManager,
        auditLogger: AgentAuditLogger,
        sessionManager: SessionManager,
        streamingEngine: StreamingEngine = StreamingEngine(),
        orchestrator: AuraOrchestrator = AuraOrchestrator(),
        voiceManager: com.example.aura.core.voice.VoiceInteractionManager? = null,
        coroutineDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main.immediate
    ) : this(
        appContext = appContext,
        repository = repository,
        providerRegistry = ModelProviderRegistry().apply {
            registerProvider(modelProvider)
            setActiveProvider(modelProvider.id)
        },
        toolRegistry = toolRegistry,
        approvalManager = approvalManager,
        auditLogger = auditLogger,
        sessionManager = sessionManager,
        streamingEngine = streamingEngine,
        orchestrator = orchestrator,
        voiceManager = voiceManager,
        coroutineDispatcher = coroutineDispatcher
    ) {
        this.customModelProvider = modelProvider
    }

    fun updateModelProvider(newProvider: ModelProvider) {
        this.customModelProvider = newProvider
    }

    private val agentScope = CoroutineScope(coroutineDispatcher + SupervisorJob())
    var activeJob: kotlinx.coroutines.Job? = null
        private set
    var lastJob: kotlinx.coroutines.Job? = null
        private set

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
    fun processUserInput(sessionId: String, userText: String): kotlinx.coroutines.Job {
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

                val availableTools = toolRegistry.toToolDefinitions()

                var currentIteration = 0
                val maxIterations = 5
                val currentRequestedToolCalls = mutableListOf<com.example.aura.core.provider.ToolCallRequest>()
                var latestAssistantText = ""

                do {
                    currentIteration++
                    currentRequestedToolCalls.clear()

                    val pastMessages = repository.getMessagesForSessionSync(session.id).map { msgEntity ->
                        var toolCallId: String? = null
                        var toolName: String? = null
                        if (msgEntity.role == MessageRole.TOOL.name) {
                            try {
                                msgEntity.toolResultJson?.let {
                                    val json = JSONObject(it)
                                    toolCallId = json.optString("tool_call_id").takeIf { id -> id.isNotBlank() }
                                    toolName = json.optString("tool_name").takeIf { name -> name.isNotBlank() }
                                }
                            } catch (_: Exception) {
                                null
                            }
                        }

                        val toolCalls = if (msgEntity.role == MessageRole.ASSISTANT.name) {
                            parseToolCallsJson(msgEntity.toolCallsJson)
                        } else emptyList()

                        ChatMessage(
                            role = MessageRole.valueOf(msgEntity.role),
                            content = msgEntity.content,
                            name = toolName,
                            toolCalls = toolCalls,
                            toolCallId = toolCallId
                        )
                    }

                    val request = CompletionRequest(
                        messages = pastMessages,
                        systemPrompt = systemPrompt,
                        availableTools = availableTools
                    )

                    val provider = modelProvider
                    auditLogger.logModelRequest(
                        sessionId = session.id,
                        providerName = provider.name,
                        promptPreview = if (currentIteration == 1) userText else "Tool result follow-up",
                        messageCount = pastMessages.size
                    )

                    session.setState(SessionState.STREAMING)
                    session.clearStreamingText()

                    val accumulatedContent = StringBuilder()

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
                                currentRequestedToolCalls.add(chunk.toolCall)
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

                    latestAssistantText = accumulatedContent.toString()

                    val toolCallsJson = if (currentRequestedToolCalls.isNotEmpty()) {
                        serializeToolCalls(currentRequestedToolCalls)
                    } else null

                    if (latestAssistantText.isNotBlank() || currentRequestedToolCalls.isNotEmpty()) {
                        repository.saveMessage(
                            MessageEntity(
                                sessionId = session.id,
                                role = MessageRole.ASSISTANT.name,
                                content = latestAssistantText,
                                toolCallsJson = toolCallsJson
                            )
                        )
                    }

                    // Handle Tool Calls through the complete Orchestration Cycle
                    if (currentRequestedToolCalls.isNotEmpty()) {
                        for (toolCall in currentRequestedToolCalls) {
                            handleToolCall(session, toolCall)
                        }
                        // Next loop iteration will return tool results to the model
                    }

                } while (currentRequestedToolCalls.isNotEmpty() && currentIteration < maxIterations)

                // Loop complete: model has produced its final response
                if (latestAssistantText.isNotBlank()) {
                    // Route response back through Aura's voice output system when in voice mode or continuous conversation
                    if (session.mode.value == com.example.aura.core.session.SessionMode.VOICE_STREAM ||
                        voiceManager?.mode?.value == com.example.aura.core.voice.VoiceMode.CONTINUOUS_CONVERSATION) {
                        voiceManager?.speakResponse(latestAssistantText)
                    }
                } else if (voiceManager?.mode?.value == com.example.aura.core.voice.VoiceMode.CONTINUOUS_CONVERSATION) {
                    voiceManager?.resumeContinuousListening()
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
                val errorMessage = when (e) {
                    is com.example.aura.core.provider.ProviderError.AuthenticationError -> "Model authentication error: ${e.message}"
                    is com.example.aura.core.provider.ProviderError.NetworkTimeoutError -> "Network timeout while communicating with model: ${e.message}"
                    is com.example.aura.core.provider.ProviderError.RateLimitError -> "Rate limit reached for model provider: ${e.message}"
                    is com.example.aura.core.provider.ProviderError.InvalidRequestError -> "Invalid model request: ${e.message}"
                    is com.example.aura.core.provider.ProviderError.ProviderUnavailableError -> "Model provider unavailable: ${e.message}"
                    else -> "Agent error: ${e.message}"
                }
                orchestrator.transitionTo(OrchestrationPhase.ERROR, errorMessage)
                auditLogger.log(
                    com.example.aura.core.logging.AuditLogEntry(
                        sessionId = session.id,
                        type = com.example.aura.core.logging.AuditEventType.PROVIDER_ERROR,
                        source = "AuraAgent",
                        message = errorMessage,
                        securityLevel = com.example.aura.core.security.SecurityLevel.SAFE
                    )
                )
                repository.saveMessage(
                    MessageEntity(
                        sessionId = session.id,
                        role = MessageRole.ASSISTANT.name,
                        content = errorMessage
                    )
                )
                _events.tryEmit(AgentEvent.ErrorOccurred(errorMessage, e))
                if (session.mode.value == com.example.aura.core.session.SessionMode.VOICE_STREAM ||
                    voiceManager?.mode?.value == com.example.aura.core.voice.VoiceMode.CONTINUOUS_CONVERSATION) {
                    voiceManager?.speakResponse(errorMessage)
                } else if (voiceManager?.mode?.value == com.example.aura.core.voice.VoiceMode.CONTINUOUS_CONVERSATION) {
                    voiceManager?.resumeContinuousListening()
                }
            } finally {
                activeJob = null
            }
        }
        activeJob = job
        lastJob = job
        return job
    }

    private suspend fun handleToolCall(
        session: ConversationSession,
        toolCall: com.example.aura.core.provider.ToolCallRequest
    ) {
        val toolName = toolCall.name.trim()
        if (toolName.isBlank()) {
            val errorMsg = "Model requested tool execution without a tool name."
            auditLogger.logToolExecuted(session.id, "unknown", false, errorMsg, com.example.aura.core.security.SecurityLevel.SAFE)
            repository.saveMessage(
                MessageEntity(
                    sessionId = session.id,
                    role = MessageRole.TOOL.name,
                    content = errorMsg,
                    toolResultJson = JSONObject().apply {
                        put("tool_call_id", toolCall.id)
                        put("tool_name", "unknown")
                        put("error", errorMsg)
                    }.toString()
                )
            )
            return
        }

        // Phase 3: SELECT TOOL
        orchestrator.transitionTo(
            OrchestrationPhase.SELECT_TOOL,
            "Model selected tool: '$toolName'",
            toolId = toolName
        )
        auditLogger.logToolSelection(session.id, toolName, toolName)

        val tool = toolRegistry.getTool(toolName)
        if (tool == null) {
            val errorMsg = "Tool '$toolName' is not recognized or available."
            auditLogger.logToolExecuted(session.id, toolName, false, errorMsg, com.example.aura.core.security.SecurityLevel.SAFE)
            repository.saveMessage(
                MessageEntity(
                    sessionId = session.id,
                    role = MessageRole.TOOL.name,
                    content = errorMsg,
                    toolResultJson = JSONObject().apply {
                        put("tool_call_id", toolCall.id)
                        put("tool_name", toolName)
                        put("error", errorMsg)
                    }.toString()
                )
            )
            return
        }

        // Parse arguments & validate input against schema
        var jsonParseError: String? = null
        val args = if (toolCall.argumentsJson.isNotBlank() && toolCall.argumentsJson.trim() != "{}") {
            try {
                val json = JSONObject(toolCall.argumentsJson)
                val map = mutableMapOf<String, Any?>()
                json.keys().forEach { key -> map[key] = json.opt(key) }
                map
            } catch (e: Exception) {
                jsonParseError = e.message ?: "Invalid JSON syntax"
                emptyMap()
            }
        } else {
            emptyMap()
        }

        if (jsonParseError != null) {
            val malformedMsg = "Malformed arguments JSON for tool '${tool.name}': $jsonParseError"
            auditLogger.logToolExecuted(session.id, tool.name, false, malformedMsg, tool.securityLevel)
            repository.saveMessage(
                MessageEntity(
                    sessionId = session.id,
                    role = MessageRole.TOOL.name,
                    content = malformedMsg,
                    toolResultJson = JSONObject().apply {
                        put("tool_call_id", toolCall.id)
                        put("tool_name", tool.name)
                        put("error", malformedMsg)
                    }.toString()
                )
            )
            return
        }

        val validation = tool.inputSchema.validateInput(args)
        if (validation.isFailure) {
            val errMsg = "Invalid arguments for tool '${tool.name}': ${validation.exceptionOrNull()?.message ?: "Malformed parameters"}"
            auditLogger.logToolExecuted(session.id, tool.name, false, errMsg, tool.securityLevel)
            repository.saveMessage(
                MessageEntity(
                    sessionId = session.id,
                    role = MessageRole.TOOL.name,
                    content = errMsg,
                    toolResultJson = JSONObject().apply {
                        put("tool_call_id", toolCall.id)
                        put("tool_name", tool.name)
                        put("error", errMsg)
                    }.toString()
                )
            )
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
                    content = permDeniedMsg,
                    toolResultJson = JSONObject().apply {
                        put("tool_call_id", toolCall.id)
                        put("tool_name", tool.name)
                        put("error", permDeniedMsg)
                    }.toString()
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
                        content = denialMsg,
                        toolResultJson = JSONObject().apply {
                            put("tool_call_id", toolCall.id)
                            put("tool_name", tool.name)
                            put("error", denialMsg)
                        }.toString()
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

        val (success, output) = try {
            when (val result = tool.execute(context, args)) {
                is ToolExecutionResult.Success -> Pair(true, result.output)
                is ToolExecutionResult.Failure -> Pair(false, "Failed: ${result.errorMessage}")
                is ToolExecutionResult.DeniedByPolicy -> Pair(false, "Denied: ${result.reason}")
                is ToolExecutionResult.Cancelled -> Pair(false, "Cancelled: ${result.reason}")
            }
        } catch (e: Exception) {
            Pair(false, "Tool execution exception: ${e.message}")
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
                toolResultJson = JSONObject().apply {
                    put("tool_call_id", toolCall.id)
                    put("tool_name", tool.name)
                    put("output", output)
                    put("success", success)
                }.toString()
            )
        )
    }

    private fun parseToolCallsJson(json: String?): List<com.example.aura.core.provider.ToolCallRequest> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<com.example.aura.core.provider.ToolCallRequest>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    com.example.aura.core.provider.ToolCallRequest(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        argumentsJson = obj.optString("argumentsJson", "{}")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeToolCalls(calls: List<com.example.aura.core.provider.ToolCallRequest>): String {
        val arr = JSONArray()
        calls.forEach { tc ->
            arr.put(JSONObject().apply {
                put("id", tc.id)
                put("name", tc.name)
                put("argumentsJson", tc.argumentsJson)
            })
        }
        return arr.toString()
    }

    /**
     * Retries the last user message.
     */
    fun retryLastMessage(sessionId: String) {
        lastUserPrompt?.let { processUserInput(sessionId, it) }
    }

    fun interrupt() {
        val job = activeJob
        activeJob = null
        job?.cancel()
        voiceManager?.stopVoiceInteraction()
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

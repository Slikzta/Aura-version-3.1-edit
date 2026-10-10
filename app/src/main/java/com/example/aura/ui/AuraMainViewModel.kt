package com.example.aura.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aura.core.agent.AgentPlan
import com.example.aura.core.agent.AgentState
import com.example.aura.core.agent.OrchestrationState
import com.example.aura.core.logging.AuditLogEntry
import com.example.aura.core.provider.ProviderConfig
import com.example.aura.core.provider.StreamingState
import com.example.aura.core.security.ApprovalPolicy
import com.example.aura.core.security.ApprovalRequest
import com.example.aura.core.session.ConversationSession
import com.example.aura.core.session.SessionMode
import com.example.aura.core.tools.AuraTool
import com.example.aura.core.tools.ToolExecutionContext
import com.example.aura.core.tools.ToolExecutionResult
import com.example.aura.core.voice.VoiceEngineState
import com.example.aura.core.voice.VoiceMode
import com.example.aura.data.entities.MemoryEntity
import com.example.aura.data.entities.MessageEntity
import com.example.aura.data.entities.ProjectEntity
import com.example.aura.data.entities.TaskEntity
import com.example.aura.di.AuraContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AuraMainViewModel(
    val container: AuraContainer
) : ViewModel() {

    val activeSession: StateFlow<ConversationSession> = container.sessionManager.activeSession
    val agentState: StateFlow<AgentState> = container.agent.agentState
    val activePlan: StateFlow<AgentPlan?> = container.agent.activePlan
    val pendingApprovals: StateFlow<List<ApprovalRequest>> = container.approvalManager.pendingRequests
    val approvalPolicy: StateFlow<ApprovalPolicy> = container.approvalManager.currentPolicy

    // Streaming and Orchestration observability
    val streamingState: StateFlow<StreamingState> = container.agent.streamingEngine.state
    val orchestrationState: StateFlow<OrchestrationState> = container.agent.orchestrator.state

    // Audio & Voice Engine
    val voiceEngineState: StateFlow<VoiceEngineState> = container.voiceManager.engineState
    val voiceMode: StateFlow<VoiceMode> = container.voiceManager.mode

    // Tools and Skills
    val tools: StateFlow<List<AuraTool>> = container.toolRegistry.toolsList
    val enabledToolIds: StateFlow<Set<String>> = container.toolRegistry.enabledToolIds

    // Model Providers
    val providers: StateFlow<List<ProviderConfig>> = container.providerRegistry.configs
    val activeProviderId: StateFlow<String> = container.providerRegistry.activeProviderId

    // Device Assistant Role
    val isDefaultAssistant: StateFlow<Boolean> = container.assistantManager.isDefaultAssistant

    fun refreshAssistantRoleStatus(): Boolean = container.assistantManager.refreshStatus()
    fun createRequestAssistantRoleIntent(): android.content.Intent = container.assistantManager.createRequestRoleIntent()
    fun createManageAssistantSettingsIntent(): android.content.Intent = container.assistantManager.createManageSettingsIntent()

    // Room Persistence
    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionMessages: StateFlow<List<MessageEntity>> = activeSession
        .flatMapLatest { session ->
            container.repository.getMessagesForSession(session.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = container.repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = container.repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = container.repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activityLogs: StateFlow<List<AuditLogEntry>> = container.auditLogger.logs

    // Transient UI state for direct tool testing
    private val _toolExecutionOutput = MutableStateFlow<String?>(null)
    val toolExecutionOutput: StateFlow<String?> = _toolExecutionOutput.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val session = activeSession.value
        container.agent.processUserInput(session.id, text.trim())
    }

    fun retryLastMessage() {
        val session = activeSession.value
        container.agent.retryLastMessage(session.id)
    }

    fun interruptAgent() {
        container.agent.interrupt()
        container.voiceManager.stopVoiceInteraction()
    }

    fun setSessionMode(mode: SessionMode) {
        activeSession.value.setMode(mode)
        if (mode == SessionMode.VOICE_STREAM) {
            if (container.voiceManager.mode.value == VoiceMode.CONTINUOUS_CONVERSATION) {
                container.voiceManager.startContinuousConversation()
            } else {
                container.voiceManager.startPushToTalk()
            }
        } else {
            container.voiceManager.stopVoiceInteraction()
        }
    }

    // Voice Engine Controls
    fun setVoiceMode(mode: VoiceMode) {
        if (mode == VoiceMode.CONTINUOUS_CONVERSATION) {
            activeSession.value.setMode(SessionMode.VOICE_STREAM)
        }
        container.voiceManager.setMode(mode)
        if (mode == VoiceMode.CONTINUOUS_CONVERSATION && hasRecordPermission()) {
            container.voiceManager.startContinuousConversation()
        }
    }

    fun startPushToTalk() {
        container.voiceManager.startPushToTalk()
    }

    fun stopPushToTalk() {
        container.voiceManager.stopPushToTalk()
    }

    fun startContinuousConversation() {
        activeSession.value.setMode(SessionMode.VOICE_STREAM)
        container.voiceManager.setMode(VoiceMode.CONTINUOUS_CONVERSATION)
        container.voiceManager.startContinuousConversation()
    }

    fun stopVoiceInteraction() {
        container.voiceManager.stopVoiceInteraction()
    }

    fun hasRecordPermission(): Boolean {
        return container.voiceManager.audioCaptureManager.hasRecordPermission()
    }

    // Approvals
    fun approveRequest(requestId: String) {
        container.approvalManager.resolveRequest(requestId, approved = true)
    }

    fun rejectRequest(requestId: String) {
        container.approvalManager.resolveRequest(requestId, approved = false)
    }

    fun setSecurityPolicy(policy: ApprovalPolicy) {
        container.approvalManager.setPolicy(policy)
    }

    fun toggleToolEnabled(toolId: String, enabled: Boolean) {
        container.toolRegistry.setToolEnabled(toolId, enabled)
    }

    fun selectProvider(providerId: String) {
        container.providerRegistry.setActiveProvider(providerId)
        container.agent.updateModelProvider(container.providerRegistry.getActiveProvider())
    }

    fun updateProviderConfig(config: ProviderConfig): Result<Unit> {
        return try {
            if (config.type != com.example.aura.core.provider.ModelProviderType.DIAGNOSTIC_OFFLINE && config.endpointUrl.isBlank()) {
                return Result.failure(IllegalArgumentException("Endpoint URL cannot be empty"))
            }
            if (config.defaultModel.isBlank()) {
                return Result.failure(IllegalArgumentException("Model identifier cannot be empty"))
            }
            container.providerRegistry.updateConfig(config)
            container.agent.updateModelProvider(container.providerRegistry.getActiveProvider())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun addMemory(key: String, content: String, category: String) {
        viewModelScope.launch {
            container.repository.saveMemory(
                MemoryEntity(
                    key = key.trim(),
                    content = content.trim(),
                    category = category.uppercase()
                )
            )
            container.auditLogger.logReasoning(
                sessionId = activeSession.value.id,
                source = "MemoryVault",
                thought = "Stored long-term memory entity: [$category] $key"
            )
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            container.repository.deleteMemory(id)
        }
    }

    fun createProject(title: String, description: String) {
        viewModelScope.launch {
            val project = ProjectEntity(
                title = title.trim(),
                description = description.trim(),
                status = "PLANNING"
            )
            container.repository.saveProject(project)

            container.repository.saveTask(
                TaskEntity(
                    projectId = project.id,
                    title = "Analyze project scope and prerequisites",
                    description = "Initial breakdown by Aura autonomous core",
                    status = "IN_PROGRESS",
                    orderIndex = 1
                )
            )
            container.repository.saveTask(
                TaskEntity(
                    projectId = project.id,
                    title = "Formulate tool invocation strategy",
                    description = "Assess required permissions and skills",
                    status = "PENDING",
                    orderIndex = 2
                )
            )
        }
    }

    fun createAutonomousPlan(goalTitle: String, steps: List<String>) {
        container.agent.createAutonomousPlan(goalTitle, steps)
    }

    fun executeToolDirectly(toolId: String, args: Map<String, Any?>) {
        viewModelScope.launch {
            val tool = container.toolRegistry.getTool(toolId) ?: return@launch
            val sandbox = File(container.context.filesDir, "aura_sandbox").apply { if (!exists()) mkdirs() }
            val context = ToolExecutionContext(
                appContext = container.context,
                sandboxDirectory = sandbox,
                sessionId = activeSession.value.id
            )

            _toolExecutionOutput.value = "Executing ${tool.name}..."
            val result = tool.execute(context, args)
            val output = when (result) {
                is ToolExecutionResult.Success -> "Success:\n${result.output}"
                is ToolExecutionResult.Failure -> "Failed:\n${result.errorMessage}"
                is ToolExecutionResult.DeniedByPolicy -> "Denied:\n${result.reason}"
                is ToolExecutionResult.Cancelled -> "Cancelled:\n${result.reason}"
            }
            _toolExecutionOutput.value = output
            container.auditLogger.logToolExecuted(activeSession.value.id, tool.name, result is ToolExecutionResult.Success, output, tool.securityLevel)
        }
    }

    fun clearToolExecutionOutput() {
        _toolExecutionOutput.value = null
    }

    fun clearAuditLogs() {
        container.auditLogger.clear()
        viewModelScope.launch {
            container.repository.clearLogs()
        }
    }

    override fun onCleared() {
        super.onCleared()
        container.voiceManager.destroy()
    }
}

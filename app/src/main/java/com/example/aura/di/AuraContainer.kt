package com.example.aura.di

import android.content.Context
import com.example.aura.core.agent.AuraAgent
import com.example.aura.core.logging.AgentAuditLogger
import com.example.aura.core.provider.ModelProviderRegistry
import com.example.aura.core.security.ApprovalManager
import com.example.aura.core.session.SessionManager
import com.example.aura.core.tools.AndroidDeviceActionsTool
import com.example.aura.core.tools.BrowserAutomationTool
import com.example.aura.core.tools.CalendarTool
import com.example.aura.core.tools.DeviceNetworkCheckTool
import com.example.aura.core.tools.MessagingTool
import com.example.aura.core.tools.NotificationProposalTool
import com.example.aura.core.tools.NotificationTool
import com.example.aura.core.tools.SandboxedFileTool
import com.example.aura.core.tools.SystemDiagnosticsTool
import com.example.aura.core.tools.ToolRegistry
import com.example.aura.core.tools.WebAccessTool
import com.example.aura.core.voice.VoiceInteractionManager
import com.example.aura.data.database.AuraDatabase
import com.example.aura.data.repository.AuraRepository

/**
 * Service container providing clean dependency boundaries for Aura.
 * Allows independent instantiation for testing, background services, and UI ViewModels.
 */
class AuraContainer(val context: Context) {

    val database: AuraDatabase by lazy {
        AuraDatabase.getInstance(context)
    }

    val repository: AuraRepository by lazy {
        AuraRepository(database)
    }

    val providerRegistry: ModelProviderRegistry by lazy {
        ModelProviderRegistry()
    }

    val approvalManager: ApprovalManager by lazy {
        ApprovalManager()
    }

    val auditLogger: AgentAuditLogger by lazy {
        AgentAuditLogger { entry ->
            repository.persistAuditLog(entry)
        }
    }

    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry().apply {
            // Register Stage 1 and Stage 2 tools with real execution boundaries
            registerTool(SystemDiagnosticsTool(), enabledByDefault = true)
            registerTool(DeviceNetworkCheckTool(), enabledByDefault = true)
            registerTool(SandboxedFileTool(), enabledByDefault = true)
            registerTool(WebAccessTool(), enabledByDefault = true)
            registerTool(NotificationTool(), enabledByDefault = true)
            registerTool(NotificationProposalTool(), enabledByDefault = true)
            registerTool(AndroidDeviceActionsTool(), enabledByDefault = true)
            registerTool(CalendarTool(), enabledByDefault = true)
            registerTool(MessagingTool(), enabledByDefault = true)
            registerTool(BrowserAutomationTool(), enabledByDefault = true)
        }
    }

    val sessionManager: SessionManager by lazy {
        SessionManager()
    }

    val vectorStore: com.example.aura.core.memory.VectorMemoryStore by lazy {
        com.example.aura.core.memory.LocalCosineVectorStore()
    }

    val permissionGuard: com.example.aura.core.tools.ToolPermissionGuard by lazy {
        com.example.aura.core.tools.ToolPermissionGuard(context.applicationContext)
    }

    val toolExecutionBoundary: com.example.aura.core.tools.ToolExecutionBoundary by lazy {
        com.example.aura.core.tools.ToolExecutionBoundary(
            appContext = context.applicationContext,
            permissionGuard = permissionGuard,
            approvalManager = approvalManager,
            auditLogger = auditLogger
        )
    }

    val modelRouter: com.example.aura.core.provider.UnifiedModelRouter by lazy {
        com.example.aura.core.provider.UnifiedModelRouter(providerRegistry)
    }

    val streamingConversationEngine: com.example.aura.core.session.StreamingConversationEngine by lazy {
        com.example.aura.core.session.StreamingConversationEngine()
    }

    val voiceManager: VoiceInteractionManager by lazy {
        VoiceInteractionManager(context.applicationContext).apply {
            setCallbacks(
                onUserInput = { text ->
                    val activeSession = sessionManager.activeSession.value
                    agent.processUserInput(activeSession.id, text)
                },
                onBargeIn = {
                    agent.interrupt()
                }
            )
        }
    }

    val assistantManager: com.example.aura.assistant.AuraAssistantManager by lazy {
        com.example.aura.assistant.AuraAssistantManager(context.applicationContext)
    }

    val agent: AuraAgent by lazy {
        AuraAgent(
            appContext = context.applicationContext,
            repository = repository,
            providerRegistry = providerRegistry,
            toolRegistry = toolRegistry,
            approvalManager = approvalManager,
            auditLogger = auditLogger,
            sessionManager = sessionManager
        )
    }
}

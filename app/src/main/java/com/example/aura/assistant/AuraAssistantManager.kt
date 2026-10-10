package com.example.aura.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.example.aura.core.session.ConversationSession
import com.example.aura.core.session.SessionMode
import com.example.aura.core.session.SessionState
import com.example.aura.core.voice.VoiceMode
import com.example.aura.di.AuraContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages detection, system role selection, and runtime invocation dispatching
 * for Aura as the device's default assistant.
 * Compatible with minSdk 24 up through targetSdk 36.
 */
class AuraAssistantManager(private val context: Context) {

    private val _isDefaultAssistant = MutableStateFlow(checkIfDefaultAssistant())
    val isDefaultAssistant: StateFlow<Boolean> = _isDefaultAssistant.asStateFlow()

    private var lastInvocationTime = 0L
    private var lastQuery: String? = null

    val lastInvocationJob: kotlinx.coroutines.Job?
        get() = AuraContainer.getInstance(context).agent.lastJob

    fun refreshStatus(): Boolean {
        val current = checkIfDefaultAssistant()
        _isDefaultAssistant.value = current
        return current
    }

    /**
     * Determines whether Aura currently holds the default assistant role on this device.
     */
    fun checkIfDefaultAssistant(): Boolean {
        // On Android 10+ (API 29+), use RoleManager if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            }
        }

        // Fallback / verification via Secure Settings
        return try {
            val voiceService = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
            val assistantService = Settings.Secure.getString(context.contentResolver, "assistant")
            val pkg = context.packageName
            (voiceService != null && voiceService.contains(pkg)) ||
                    (assistantService != null && assistantService.contains(pkg))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if the system RoleManager supports requesting the assistant role.
     */
    fun isRoleRequestAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
        } else {
            false
        }
    }

    /**
     * Creates the Intent to launch the official system role selection dialog to set Aura as the default assistant.
     */
    fun createRequestRoleIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
            }
        }
        // Fallback: direct to voice input settings
        return createManageSettingsIntent()
    }

    /**
     * Creates the Intent to open system Settings where the user can view, change, or disable the default assistant.
     */
    fun createManageSettingsIntent(): Intent {
        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        return if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                if (resolveActivity(context.packageManager) == null) {
                    action = Settings.ACTION_SETTINGS
                }
            }
        }
    }

    /**
     * Processes an assistant invocation from VoiceInteractionSession or Activity assist intent.
     * Reuses the existing AuraAgent pipeline, SessionManager, and VoiceInteractionManager.
     */
    fun processAssistantInvocation(
        source: String,
        args: Bundle? = null,
        intent: Intent? = null
    ): ConversationSession {
        val container = AuraContainer.getInstance(context)
        val query = extractQuery(args, intent)

        // Deduplication to prevent double-firing when VoiceInteractionSession launches MainActivity
        val now = System.currentTimeMillis()
        if (now - lastInvocationTime < 1200L && query == lastQuery && query != null) {
            return container.sessionManager.activeSession.value
        }
        lastInvocationTime = now
        lastQuery = query

        // 1. Obtain or create appropriate conversation session
        val current = container.sessionManager.activeSession.value
        val session = if (current.state.value == SessionState.ERROR ||
            current.state.value == SessionState.INTERRUPTED
        ) {
            container.sessionManager.startNewSession(SessionMode.VOICE_STREAM)
        } else {
            current
        }
        session.setMode(SessionMode.VOICE_STREAM)

        // 2. Route input to agent pipeline or activate voice input
        if (!query.isNullOrBlank()) {
            container.agent.processUserInput(session.id, query)
        } else {
            // Trigger voice input on existing VoiceInteractionManager
            container.voiceManager.setMode(VoiceMode.PUSH_TO_TALK)
            container.voiceManager.startPushToTalk()
        }

        return session
    }

    /**
     * Safely cancels/interrupts an ongoing assistant turn without breaking the session.
     */
    fun handleAssistantInterruption(reason: String = "Assistant dismissed") {
        val container = AuraContainer.getInstance(context)
        container.agent.interrupt()
        container.voiceManager.stopVoiceInteraction()
    }

    /**
     * Extracts user query text from assist bundle or intent extras.
     */
    fun extractQuery(args: Bundle?, intent: Intent?): String? {
        // Try args bundle first
        args?.getString("query")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        args?.getString("android.intent.extra.ASSIST_INPUT_HINT")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        args?.getString(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        args?.getString("android.intent.extra.TEXT")?.takeIf { it.isNotBlank() }?.let { return it.trim() }

        // Try intent extras
        intent?.getStringExtra("query")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        intent?.getStringExtra("android.intent.extra.ASSIST_INPUT_HINT")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        intent?.getStringExtra("android.intent.extra.TEXT")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        intent?.data?.getQueryParameter("q")?.takeIf { it.isNotBlank() }?.let { return it.trim() }

        return null
    }
}

package com.example.aura.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages detection and system role selection for Aura as the device's default assistant.
 * Compatible with minSdk 24 up through targetSdk 36.
 */
class AuraAssistantManager(private val context: Context) {

    private val _isDefaultAssistant = MutableStateFlow(checkIfDefaultAssistant())
    val isDefaultAssistant: StateFlow<Boolean> = _isDefaultAssistant.asStateFlow()

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
}

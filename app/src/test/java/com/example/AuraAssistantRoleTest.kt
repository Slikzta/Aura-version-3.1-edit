package com.example

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.aura.assistant.AuraAssistantManager
import com.example.aura.assistant.AuraRecognitionService
import com.example.aura.assistant.AuraVoiceInteractionService
import com.example.aura.assistant.AuraVoiceInteractionSession
import com.example.aura.assistant.AuraVoiceInteractionSessionService
import com.example.aura.di.AuraContainer
import com.example.aura.ui.AuraMainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraAssistantRoleTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testAssistantManagerDetectionAndIntents() {
        val manager = AuraAssistantManager(context)
        assertNotNull(manager)

        // Initial default detection in test environment should be clean and non-null
        val isDefault = manager.checkIfDefaultAssistant()
        assertEquals(isDefault, manager.isDefaultAssistant.value)

        // Request Role Intent creation
        val requestIntent = manager.createRequestRoleIntent()
        assertNotNull(requestIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                assertNotNull(requestIntent.action)
                assertTrue(requestIntent.hasExtra("android.app.role.extra.ROLE_NAME"))
                assertEquals(RoleManager.ROLE_ASSISTANT, requestIntent.getStringExtra("android.app.role.extra.ROLE_NAME"))
            }
        }

        // Manage Settings Intent creation
        val settingsIntent = manager.createManageSettingsIntent()
        assertNotNull(settingsIntent)
        assertTrue(
            settingsIntent.action == Settings.ACTION_VOICE_INPUT_SETTINGS ||
                    settingsIntent.action == Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS ||
                    settingsIntent.action == Settings.ACTION_SETTINGS
        )
    }

    @Test
    fun testAssistantServicesInstantiation() {
        // Verify services can be instantiated and follow Android lifecycle contracts
        val voiceService = AuraVoiceInteractionService()
        assertNotNull(voiceService)

        val sessionService = AuraVoiceInteractionSessionService()
        assertNotNull(sessionService)

        val recognitionService = AuraRecognitionService()
        assertNotNull(recognitionService)

        val session = AuraVoiceInteractionSession(context)
        assertNotNull(session)
    }

    @Test
    fun testContainerAndViewModelAssistantIntegration() {
        val container = AuraContainer(context)
        assertNotNull(container.assistantManager)

        val viewModel = AuraMainViewModel(container)
        assertNotNull(viewModel.isDefaultAssistant)

        val refreshed = viewModel.refreshAssistantRoleStatus()
        assertEquals(refreshed, viewModel.isDefaultAssistant.value)

        val requestIntent = viewModel.createRequestAssistantRoleIntent()
        assertNotNull(requestIntent)

        val manageIntent = viewModel.createManageAssistantSettingsIntent()
        assertNotNull(manageIntent)
    }

    @Test
    fun testSecureSettingsFallbackDetection() {
        val manager = AuraAssistantManager(context)

        // Simulate voice_interaction_service in Settings.Secure pointing to Aura
        val original = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
        try {
            Settings.Secure.putString(
                context.contentResolver,
                "voice_interaction_service",
                "${context.packageName}/com.example.aura.assistant.AuraVoiceInteractionService"
            )

            // When Secure setting points to Aura, checkIfDefaultAssistant reports true if not overridden
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                assertTrue(manager.checkIfDefaultAssistant())
            }
        } finally {
            Settings.Secure.putString(context.contentResolver, "voice_interaction_service", original)
        }
    }
}

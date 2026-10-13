package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aura.core.provider.ProviderConfig
import com.example.aura.core.provider.ModelProviderType
import com.example.aura.core.security.ApprovalPolicy
import com.example.aura.core.voice.VoiceMode
import com.example.aura.di.AuraContainer
import com.example.aura.ui.AuraMainViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraSettingsAndVoiceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var container: AuraContainer
    private lateinit var viewModel: AuraMainViewModel

    @Before
    fun setUp() {
        AuraContainer.setInstance(null)
        container = AuraContainer(context)
        AuraContainer.setInstance(container)
        viewModel = AuraMainViewModel(container)
    }

    @After
    fun tearDown() {
        AuraContainer.setInstance(null)
    }

    @Test
    fun testVoicePreferencesPersistenceAndControls() = runTest {
        // 1. Verify and update speech rate
        viewModel.setSpeechRate(1.25f)
        assertEquals(1.25f, viewModel.speechRate.value, 0.01f)
        assertEquals(1.25f, container.preferencesRepository.speechRate.value, 0.01f)
        assertEquals(1.25f, container.voiceManager.speechRate, 0.01f)

        // 2. Verify and update speech pitch
        viewModel.setSpeechPitch(0.9f)
        assertEquals(0.9f, viewModel.speechPitch.value, 0.01f)
        assertEquals(0.9f, container.preferencesRepository.speechPitch.value, 0.01f)
        assertEquals(0.9f, container.voiceManager.speechPitch, 0.01f)

        // 3. Verify and update voice name
        viewModel.setSelectedVoice("en-us-x-sfg#female_1-local")
        assertEquals("en-us-x-sfg#female_1-local", viewModel.selectedVoiceName.value)
        assertEquals("en-us-x-sfg#female_1-local", container.voiceManager.selectedVoice)

        // 4. Verify mute toggle
        assertFalse(viewModel.isMuted.value)
        viewModel.setMuted(true)
        assertTrue(viewModel.isMuted.value)
        assertTrue(container.voiceManager.isMuted.value)
        viewModel.setMuted(false)
        assertFalse(viewModel.isMuted.value)

        // 5. Verify pause & resume
        assertFalse(viewModel.isPaused.value)
        viewModel.pauseVoice()
        assertTrue(viewModel.isPaused.value)
        viewModel.resumeVoice()
        assertFalse(viewModel.isPaused.value)
    }

    @Test
    fun testProviderSwitchingAndSecurePersistence() = runTest {
        // 1. Select OpenAI provider
        viewModel.selectProvider("openai_provider")
        assertEquals("openai_provider", viewModel.activeProviderId.value)
        assertEquals("openai_provider", container.agent.modelProvider.id)

        // 2. Switch to Gemini provider
        viewModel.selectProvider("gemini_provider")
        assertEquals("gemini_provider", viewModel.activeProviderId.value)
        assertEquals("gemini_provider", container.agent.modelProvider.id)

        // 3. Update Custom Endpoint config
        val customConfig = ProviderConfig(
            id = "custom_endpoint",
            type = ModelProviderType.CUSTOM_ENDPOINT,
            displayName = "Private LLM Gateway",
            endpointUrl = "https://ai.example.com/v1",
            apiKey = "sk-custom-secret-key-12345",
            defaultModel = "qwen-2.5-72b",
            isEnabled = true
        )
        val updateResult = viewModel.updateProviderConfig(customConfig)
        assertTrue("Provider config update should succeed", updateResult.isSuccess)

        // Verify config persisted in registry and storage
        val stored = container.providerRegistry.configs.value.find { it.id == "custom_endpoint" }
        assertNotNull(stored)
        assertEquals("Private LLM Gateway", stored!!.displayName)
        assertEquals("https://ai.example.com/v1", stored.endpointUrl)
        assertEquals("sk-custom-secret-key-12345", stored.apiKey)
        assertEquals("qwen-2.5-72b", stored.defaultModel)
    }

    @Test
    fun testContinuousHandsFreeModeSwitching() = runTest {
        // Initial mode should be push-to-talk
        assertEquals(VoiceMode.PUSH_TO_TALK, viewModel.voiceMode.value)

        // Switch to continuous hands-free mode
        viewModel.setVoiceMode(VoiceMode.CONTINUOUS_CONVERSATION)
        assertEquals(VoiceMode.CONTINUOUS_CONVERSATION, viewModel.voiceMode.value)

        // Stop voice interaction
        viewModel.stopVoiceInteraction()
        assertFalse(container.voiceManager.ttsEngine.isSpeaking.value)
    }

    @Test
    fun testSecurityPolicySwitching() = runTest {
        viewModel.setSecurityPolicy(ApprovalPolicy.ZERO_TRUST)
        assertEquals(ApprovalPolicy.ZERO_TRUST, viewModel.approvalPolicy.value)
        assertEquals(ApprovalPolicy.ZERO_TRUST, container.approvalManager.currentPolicy.value)

        viewModel.setSecurityPolicy(ApprovalPolicy.STANDARD)
        assertEquals(ApprovalPolicy.STANDARD, viewModel.approvalPolicy.value)
    }

    @Test
    fun testToolDirectExecutionAndInspection() = runTest {
        // Test network check tool execution directly
        viewModel.testExecuteTool("network_check", emptyMap())
        val output = viewModel.toolExecutionOutput.value
        assertNotNull(output)
        assertTrue(output!!.contains("Success:") || output.contains("Network"))
    }
}

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aura.core.provider.ModelProviderRegistry
import com.example.aura.core.provider.ModelProviderStorage
import com.example.aura.core.provider.ModelProviderType
import com.example.aura.core.provider.ProviderAuthState
import com.example.aura.core.security.SecretSanitizer
import com.example.aura.di.AuraContainer
import com.example.aura.ui.AuraMainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraModelProviderPersistenceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var storage: ModelProviderStorage

    @Before
    fun setUp() {
        AuraContainer.setInstance(null)
        storage = ModelProviderStorage(context)
        storage.clearAll()
    }

    @After
    fun tearDown() {
        storage.clearAll()
        AuraContainer.setInstance(null)
    }

    @Test
    fun testGeminiProviderConfigPersistsAcrossSimulatedAppRestart() {
        // --- Session 1: User configures and selects Google Gemini ---
        val container1 = AuraContainer(context)
        AuraContainer.setInstance(container1)
        val viewModel1 = AuraMainViewModel(container1)

        val initialGemini = container1.providerRegistry.configs.value.find { it.id == "gemini_provider" }
        assertNotNull("Gemini provider config must exist in registry", initialGemini)

        val updatedGemini = initialGemini!!.copy(
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta",
            apiKey = "AIzaSyTestSecretGeminiKey12345",
            defaultModel = "gemini-1.5-flash-002"
        )

        // Save provider config via ViewModel (as done by the UI Save button)
        val saveResult = viewModel1.updateProviderConfig(updatedGemini)
        assertTrue("Save provider config must succeed", saveResult.isSuccess)

        // Select Gemini as the active provider
        viewModel1.selectProvider("gemini_provider")

        assertEquals("gemini_provider", container1.providerRegistry.activeProviderId.value)
        assertEquals("gemini_provider", container1.providerRegistry.getActiveProvider().id)
        assertEquals(ProviderAuthState.Configured, container1.providerRegistry.getActiveProvider().authState)

        // --- Simulate App Restart (Swiped closed and reopened) ---
        // Null out existing container instance to simulate process termination
        AuraContainer.setInstance(null)

        // New application instance initializes
        val container2 = AuraContainer(context)
        AuraContainer.setInstance(container2)
        val viewModel2 = AuraMainViewModel(container2)

        // 1. Verify selected active provider survived restart and is NOT reset to diagnostic_offline
        val activeProviderId = container2.providerRegistry.activeProviderId.value
        assertEquals("Active provider must survive restart and remain gemini_provider", "gemini_provider", activeProviderId)
        assertNotEquals("Active provider must NOT reset to diagnostic_offline", "diagnostic_offline", activeProviderId)

        // 2. Verify getActiveProvider() returns the configured Gemini provider
        val activeProvider = container2.providerRegistry.getActiveProvider()
        assertEquals("gemini_provider", activeProvider.id)
        assertEquals(ProviderAuthState.Configured, activeProvider.authState)

        // 3. Verify Gemini configuration fields (endpoint, apiKey, model) were preserved
        val geminiAfterRestart = container2.providerRegistry.configs.value.find { it.id == "gemini_provider" }
        assertNotNull(geminiAfterRestart)
        assertEquals("https://generativelanguage.googleapis.com/v1beta", geminiAfterRestart!!.endpointUrl)
        assertEquals("AIzaSyTestSecretGeminiKey12345", geminiAfterRestart.apiKey)
        assertEquals("gemini-1.5-flash-002", geminiAfterRestart.defaultModel)

        // 4. Verify agent is actively pointing to the persisted provider
        assertEquals("gemini_provider", container2.agent.modelProvider.id)
    }

    @Test
    fun testActiveProviderPersistedSeparatelyFromConfigs() {
        val container = AuraContainer(context)
        val viewModel = AuraMainViewModel(container)

        // Select anthropic_provider as active
        viewModel.selectProvider("anthropic_provider")
        assertEquals("anthropic_provider", storage.loadActiveProviderId())

        // Modify config of a completely different provider (e.g. ollama_local)
        val ollamaConfig = container.providerRegistry.configs.value.find { it.id == "ollama_local" }!!
        val updatedOllama = ollamaConfig.copy(endpointUrl = "http://192.168.1.100:11434/v1")
        viewModel.updateProviderConfig(updatedOllama)

        // Active provider should still be anthropic_provider in storage
        assertEquals("anthropic_provider", storage.loadActiveProviderId())

        // Now switch active to ollama_local
        viewModel.selectProvider("ollama_local")
        assertEquals("ollama_local", storage.loadActiveProviderId())

        // Simulate app restart
        AuraContainer.setInstance(null)
        val restartedContainer = AuraContainer(context)
        assertEquals("ollama_local", restartedContainer.providerRegistry.activeProviderId.value)
        assertEquals("ollama_local", restartedContainer.providerRegistry.getActiveProvider().id)
    }

    @Test
    fun testNeverOverwriteSavedConfigurationsWithEmptyDefaultsDuringStartup() {
        // Save custom settings for Gemini
        val customGemini = ModelProviderRegistry.defaultConfigs().find { it.id == "gemini_provider" }!!.copy(
            endpointUrl = "https://custom-proxy.example.com/v1beta",
            apiKey = "AIzaSyCustomKey9999",
            defaultModel = "gemini-1.5-pro"
        )
        storage.saveConfig(customGemini)
        storage.saveActiveProviderId("gemini_provider")

        // Load configs using storage
        val loadedConfigs = storage.loadAllConfigs(ModelProviderRegistry.defaultConfigs())
        val loadedGemini = loadedConfigs.find { it.id == "gemini_provider" }!!

        assertEquals("https://custom-proxy.example.com/v1beta", loadedGemini.endpointUrl)
        assertEquals("AIzaSyCustomKey9999", loadedGemini.apiKey)
        assertEquals("gemini-1.5-pro", loadedGemini.defaultModel)

        // Verify container initialization respects saved configs
        val container = AuraContainer(context)
        val containerGemini = container.providerRegistry.configs.value.find { it.id == "gemini_provider" }!!
        assertEquals("https://custom-proxy.example.com/v1beta", containerGemini.endpointUrl)
        assertEquals("AIzaSyCustomKey9999", containerGemini.apiKey)
        assertEquals("gemini-1.5-pro", containerGemini.defaultModel)
    }

    @Test
    fun testDefaultOfflineDiagnosticSelectedOnlyWhenAppropriate() {
        // 1. Fresh installation with no saved config or active provider
        storage.clearAll()
        val freshContainer = AuraContainer(context)
        val expectedInitial = ModelProviderRegistry.determineDefaultActiveProviderId(
            freshContainer.providerRegistry.configs.value
        )
        assertEquals(expectedInitial, freshContainer.providerRegistry.activeProviderId.value)

        // 2. User explicitly selects diagnostic_offline
        freshContainer.providerRegistry.setActiveProvider("diagnostic_offline")
        assertEquals("diagnostic_offline", storage.loadActiveProviderId())

        // Simulated restart
        AuraContainer.setInstance(null)
        val restarted1 = AuraContainer(context)
        assertEquals("diagnostic_offline", restarted1.providerRegistry.activeProviderId.value)

        // 3. User switches to Gemini
        restarted1.providerRegistry.setActiveProvider("gemini_provider")
        assertEquals("gemini_provider", storage.loadActiveProviderId())

        // Simulated restart
        AuraContainer.setInstance(null)
        val restarted2 = AuraContainer(context)
        assertEquals("gemini_provider", restarted2.providerRegistry.activeProviderId.value)
        assertNotEquals("diagnostic_offline", restarted2.providerRegistry.activeProviderId.value)
    }

    @Test
    fun testPreserveAllProviderOptions() {
        val container = AuraContainer(context)
        val configs = container.providerRegistry.configs.value
        val providerTypes = configs.map { it.type }.toSet()

        assertTrue("Must preserve GEMINI provider", providerTypes.contains(ModelProviderType.GEMINI))
        assertTrue("Must preserve OPENAI provider", providerTypes.contains(ModelProviderType.OPENAI))
        assertTrue("Must preserve ANTHROPIC provider", providerTypes.contains(ModelProviderType.ANTHROPIC))
        assertTrue("Must preserve OLLAMA_LOCAL provider", providerTypes.contains(ModelProviderType.OLLAMA_LOCAL))
        assertTrue("Must preserve CUSTOM_ENDPOINT provider", providerTypes.contains(ModelProviderType.CUSTOM_ENDPOINT))
        assertTrue("Must preserve DIAGNOSTIC_OFFLINE provider", providerTypes.contains(ModelProviderType.DIAGNOSTIC_OFFLINE))

        val providerIds = configs.map { it.id }.toSet()
        assertTrue(providerIds.contains("gemini_provider"))
        assertTrue(providerIds.contains("openai_provider"))
        assertTrue(providerIds.contains("anthropic_provider"))
        assertTrue(providerIds.contains("ollama_local"))
        assertTrue(providerIds.contains("custom_endpoint"))
        assertTrue(providerIds.contains("diagnostic_offline"))
    }

    @Test
    fun testSaveProviderConfigValidationAndFeedback() {
        val container = AuraContainer(context)
        val viewModel = AuraMainViewModel(container)

        val geminiConfig = container.providerRegistry.configs.value.find { it.id == "gemini_provider" }!!

        // Empty endpoint should fail validation
        val invalidEndpoint = geminiConfig.copy(endpointUrl = "   ")
        val failResult1 = viewModel.updateProviderConfig(invalidEndpoint)
        assertTrue(failResult1.isFailure)
        assertEquals("Endpoint URL cannot be empty", failResult1.exceptionOrNull()?.message)

        // Empty model identifier should fail validation
        val invalidModel = geminiConfig.copy(endpointUrl = "https://api.example.com", defaultModel = "")
        val failResult2 = viewModel.updateProviderConfig(invalidModel)
        assertTrue(failResult2.isFailure)
        assertEquals("Model identifier cannot be empty", failResult2.exceptionOrNull()?.message)

        // Valid configuration should succeed and persist
        val validConfig = geminiConfig.copy(
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta",
            apiKey = "AIzaSyValidKey123",
            defaultModel = "gemini-1.5-flash"
        )
        val successResult = viewModel.updateProviderConfig(validConfig)
        assertTrue(successResult.isSuccess)

        // Check storage received the update
        val saved = storage.loadAllConfigs(ModelProviderRegistry.defaultConfigs()).find { it.id == "gemini_provider" }
        assertNotNull(saved)
        assertEquals("AIzaSyValidKey123", saved!!.apiKey)
    }

    @Test
    fun testApiKeysHandledSecurely() {
        val googleKey = "AIza" + "B".repeat(35)
        assertEquals(39, googleKey.length)
        val sanitizedGoogle = SecretSanitizer.sanitize("Model authorization key: $googleKey")
        assertFalse("Google API key must not be exposed in raw text", sanitizedGoogle.contains(googleKey))
        assertTrue("Sanitized text must contain redacted token", sanitizedGoogle.contains("[REDACTED_API_KEY]"))

        val openAiKey = "sk-123456789012345678901234567890"
        val sanitizedOpenAi = SecretSanitizer.sanitize("Bearer $openAiKey")
        assertFalse("OpenAI API key must not be exposed", sanitizedOpenAi.contains(openAiKey))

        val map = mapOf("api_key" to googleKey, "normal" to "hello")
        val sanitizedMap = SecretSanitizer.sanitizeMap(map)
        assertEquals("[REDACTED]", sanitizedMap["api_key"])
        assertEquals("hello", sanitizedMap["normal"])
    }
}

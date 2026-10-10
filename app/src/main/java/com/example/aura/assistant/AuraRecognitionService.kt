package com.example.aura.assistant

import android.content.Intent
import android.speech.RecognitionService
import com.example.aura.di.AuraContainer

/**
 * Android RecognitionService implementation for voice input routing into Aura.
 */
class AuraRecognitionService : RecognitionService() {

    private val container: AuraContainer by lazy {
        AuraContainer.getInstance(this)
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        container.assistantManager.processAssistantInvocation(
            source = "RecognitionService",
            intent = recognizerIntent
        )
    }

    override fun onCancel(listener: Callback?) {
        container.assistantManager.handleAssistantInterruption("RecognitionService cancelled")
    }

    override fun onStopListening(listener: Callback?) {
        container.voiceManager.stopPushToTalk()
    }

    override fun onDestroy() {
        super.onDestroy()
        container.assistantManager.handleAssistantInterruption("RecognitionService destroyed")
    }
}

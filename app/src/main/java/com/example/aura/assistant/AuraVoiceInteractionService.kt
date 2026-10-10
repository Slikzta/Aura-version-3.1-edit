package com.example.aura.assistant

import android.service.voice.VoiceInteractionService
import com.example.aura.di.AuraContainer

/**
 * Android system service qualifying Aura as a VoiceInteractionService and default Assistant.
 */
class AuraVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        // Initialize shared container and refresh assistant status
        AuraContainer.getInstance(this).assistantManager.refreshStatus()
    }

    override fun onShutdown() {
        super.onShutdown()
        AuraContainer.getInstance(this).assistantManager.handleAssistantInterruption("VoiceInteractionService shutdown")
    }
}

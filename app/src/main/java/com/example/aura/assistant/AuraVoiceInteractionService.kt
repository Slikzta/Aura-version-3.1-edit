package com.example.aura.assistant

import android.service.voice.VoiceInteractionService

/**
 * Android system service qualifying Aura as a VoiceInteractionService and default Assistant.
 */
class AuraVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
    }

    override fun onShutdown() {
        super.onShutdown()
    }
}

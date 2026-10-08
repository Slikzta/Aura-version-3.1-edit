package com.example.aura.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Factory service producing [AuraVoiceInteractionSession] instances when the system triggers assist.
 */
class AuraVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return AuraVoiceInteractionSession(this)
    }
}

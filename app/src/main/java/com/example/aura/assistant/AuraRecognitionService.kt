package com.example.aura.assistant

import android.content.Intent
import android.speech.RecognitionService

/**
 * Android RecognitionService implementation for voice input routing.
 */
class AuraRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
    }

    override fun onCancel(listener: Callback?) {
    }

    override fun onStopListening(listener: Callback?) {
    }
}

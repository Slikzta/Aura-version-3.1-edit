package com.example.aura.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.example.MainActivity
import com.example.aura.core.agent.AgentState
import com.example.aura.di.AuraContainer

/**
 * Handles individual assist invocation sessions triggered by the Android OS
 * (e.g. home button long press, navigation bar swipe, or assist button).
 * Directly connects to the existing AuraAgent pipeline.
 */
class AuraVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val container: AuraContainer by lazy {
        AuraContainer.getInstance(context)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        // Route recognized/user input into existing Aura agent pipeline
        container.assistantManager.processAssistantInvocation(
            source = "VoiceInteractionSession",
            args = args
        )

        // Bring Aura's UI forward to display the ongoing session and streaming response
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_ASSIST
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            args?.let { putExtras(it) }
        }
        context.startActivity(intent)
        hide()
    }

    @Deprecated("Deprecated in Java")
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
        if (data != null && !data.isEmpty) {
            val query = container.assistantManager.extractQuery(data, null)
            if (!query.isNullOrBlank()) {
                container.assistantManager.processAssistantInvocation(
                    source = "HandleAssistData",
                    args = data
                )
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        container.assistantManager.handleAssistantInterruption("VoiceInteractionSession back pressed")
        hide()
    }

    override fun onHide() {
        super.onHide()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safely interrupt if an active session or audio capture was running
        if (container.agent.agentState.value != AgentState.IDLE) {
            container.assistantManager.handleAssistantInterruption("VoiceInteractionSession destroyed")
        }
    }
}

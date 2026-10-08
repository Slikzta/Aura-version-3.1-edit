package com.example.aura.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates conversation and autonomous sessions for Aura.
 */
class SessionManager {

    private val sessionsMap = ConcurrentHashMap<String, ConversationSession>()

    private val _activeSession = MutableStateFlow(createDefaultSession())
    val activeSession: StateFlow<ConversationSession> = _activeSession.asStateFlow()

    private fun createDefaultSession(): ConversationSession {
        val session = ConversationSession()
        sessionsMap[session.id] = session
        return session
    }

    fun startNewSession(mode: SessionMode = SessionMode.INTERACTIVE_TEXT): ConversationSession {
        // Interrupt previous session if active
        _activeSession.value.interrupt("Switching to new session")

        val newSession = ConversationSession(initialMode = mode)
        sessionsMap[newSession.id] = newSession
        _activeSession.value = newSession
        return newSession
    }

    fun switchSession(sessionId: String): ConversationSession? {
        val target = sessionsMap[sessionId] ?: return null
        _activeSession.value.interrupt("Switching session")
        _activeSession.value = target
        return target
    }

    fun getSession(sessionId: String): ConversationSession? = sessionsMap[sessionId]

    /**
     * Triggers an immediate global interruption on the currently active session.
     */
    fun interruptActiveSession(reason: String = "User barge-in / Interrupt tapped") {
        _activeSession.value.interrupt(reason)
    }
}

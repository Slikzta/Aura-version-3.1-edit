package com.example.aura.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages user preferences for Aura, including voice parameters, autonomous mode options,
 * and UI preferences. Persisted privately via SharedPreferences.
 */
class AuraPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // Voice & Speech Preferences
    private val _speechRate = MutableStateFlow(prefs.getFloat(KEY_SPEECH_RATE, 1.0f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechPitch = MutableStateFlow(prefs.getFloat(KEY_SPEECH_PITCH, 1.0f))
    val speechPitch: StateFlow<Float> = _speechPitch.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow(prefs.getString(KEY_VOICE_NAME, "") ?: "")
    val selectedVoiceName: StateFlow<String> = _selectedVoiceName.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(prefs.getString(KEY_LANGUAGE, Locale.getDefault().language) ?: "en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    // Autonomous Mode Settings
    private val _continuousHandsFreeEnabled = MutableStateFlow(prefs.getBoolean(KEY_CONTINUOUS_HANDS_FREE, false))
    val continuousHandsFreeEnabled: StateFlow<Boolean> = _continuousHandsFreeEnabled.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    private val _autoExecuteAutonomousPlans = MutableStateFlow(prefs.getBoolean(KEY_AUTO_EXECUTE_PLANS, true))
    val autoExecuteAutonomousPlans: StateFlow<Boolean> = _autoExecuteAutonomousPlans.asStateFlow()

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = clamped
        prefs.edit().putFloat(KEY_SPEECH_RATE, clamped).apply()
    }

    fun setSpeechPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _speechPitch.value = clamped
        prefs.edit().putFloat(KEY_SPEECH_PITCH, clamped).apply()
    }

    fun setSelectedVoiceName(voiceName: String) {
        _selectedVoiceName.value = voiceName
        prefs.edit().putString(KEY_VOICE_NAME, voiceName).apply()
    }

    fun setSelectedLanguage(languageCode: String) {
        _selectedLanguage.value = languageCode
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    fun setContinuousHandsFreeEnabled(enabled: Boolean) {
        _continuousHandsFreeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CONTINUOUS_HANDS_FREE, enabled).apply()
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        _hapticFeedbackEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    fun setAutoExecuteAutonomousPlans(enabled: Boolean) {
        _autoExecuteAutonomousPlans.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_EXECUTE_PLANS, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "aura_user_preferences"
        private const val KEY_SPEECH_RATE = "pref_speech_rate"
        private const val KEY_SPEECH_PITCH = "pref_speech_pitch"
        private const val KEY_VOICE_NAME = "pref_voice_name"
        private const val KEY_LANGUAGE = "pref_language"
        private const val KEY_CONTINUOUS_HANDS_FREE = "pref_continuous_hands_free"
        private const val KEY_HAPTIC_FEEDBACK = "pref_haptic_feedback"
        private const val KEY_AUTO_EXECUTE_PLANS = "pref_auto_execute_plans"
    }
}

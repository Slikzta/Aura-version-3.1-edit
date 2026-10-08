package com.example.aura.core.voice

import kotlin.math.sqrt

/**
 * Interface for Voice Activity Detection (VAD).
 * Evaluates audio frames in real time to distinguish human speech from background silence.
 */
interface VoiceActivityDetector {
    /**
     * Processes an audio frame (PCM 16-bit).
     * @return true if speech is detected above the threshold.
     */
    fun processFrame(pcmBuffer: ShortArray, readSize: Int): Boolean

    /**
     * Returns the normalized RMS energy level (0.0 to 1.0) of the last processed frame.
     */
    val currentEnergyLevel: Float

    fun reset()
}

/**
 * Energy-threshold based Voice Activity Detector for low-latency on-device processing.
 */
class EnergyThresholdVAD(
    private val speechEnergyThreshold: Float = 0.045f,
    private val speechConsecutiveFramesRequired: Int = 3,
    private val silenceConsecutiveFramesRequired: Int = 15
) : VoiceActivityDetector {

    private var speechFrameCounter = 0
    private var silenceFrameCounter = 0
    private var isSpeechActive = false
    private var _currentEnergy = 0f

    override val currentEnergyLevel: Float
        get() = _currentEnergy

    override fun processFrame(pcmBuffer: ShortArray, readSize: Int): Boolean {
        if (readSize <= 0) return isSpeechActive

        var sumSquares = 0.0
        for (i in 0 until readSize) {
            val sample = pcmBuffer[i].toDouble() / 32768.0
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / readSize).toFloat()
        _currentEnergy = rms

        if (rms >= speechEnergyThreshold) {
            speechFrameCounter++
            silenceFrameCounter = 0
            if (speechFrameCounter >= speechConsecutiveFramesRequired) {
                isSpeechActive = true
            }
        } else {
            silenceFrameCounter++
            speechFrameCounter = 0
            if (silenceFrameCounter >= silenceConsecutiveFramesRequired) {
                isSpeechActive = false
            }
        }

        return isSpeechActive
    }

    override fun reset() {
        speechFrameCounter = 0
        silenceFrameCounter = 0
        isSpeechActive = false
        _currentEnergy = 0f
    }
}

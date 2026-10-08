package com.example.aura.core.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages low-latency audio capture from the device microphone using [AudioRecord].
 */
class AudioCaptureManager(
    private val context: Context,
    val sampleRate: Int = 16000
) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioChunks = MutableSharedFlow<AudioChunk>(extraBufferCapacity = 64)
    val audioChunks: SharedFlow<AudioChunk> = _audioChunks.asSharedFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope, vad: VoiceActivityDetector? = null, onSpeechDetected: (() -> Unit)? = null): Boolean {
        if (!hasRecordPermission()) {
            return false
        }
        if (_isRecording.value) return true

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteArray(bufferSize)

                while (isActive && _isRecording.value) {
                    val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (readCount > 0) {
                        // Convert to byte array
                        for (i in 0 until readCount) {
                            val v = shortBuffer[i].toInt()
                            byteBuffer[i * 2] = (v and 0x00FF).toByte()
                            byteBuffer[i * 2 + 1] = ((v shr 8) and 0x00FF).toByte()
                        }

                        val chunkBytes = byteBuffer.copyOf(readCount * 2)
                        _audioChunks.tryEmit(AudioChunk(chunkBytes, sampleRate = sampleRate))

                        // Run VAD if supplied
                        vad?.let {
                            val isSpeech = it.processFrame(shortBuffer, readCount)
                            _amplitude.value = it.currentEnergyLevel
                            if (isSpeech) {
                                onSpeechDetected?.invoke()
                            }
                        }
                    }
                }
            }
            return true
        } catch (_: Exception) {
            stopCapture()
            return false
        }
    }

    fun stopCapture() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        _amplitude.value = 0f
    }
}

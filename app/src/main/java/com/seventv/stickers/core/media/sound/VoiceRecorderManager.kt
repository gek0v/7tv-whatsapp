package com.seventv.stickers.core.media.sound

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class VoiceRecordingState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val amplitudeNormalized: Float = 0f,
    val recordedFile: File? = null,
    val error: String? = null
)

interface VoiceRecorderController {
    val state: StateFlow<VoiceRecordingState>
    fun startRecording(maxDurationMs: Long = SoundStickerSpecs.MAX_STATIC_CLIP_DURATION_MS)
    fun stopRecording(): File?
    fun cancelRecording()
    fun release()
}

/**
 * Manages live microphone AAC voice recording into cache for Sound Sticker creation.
 * Polls `MediaRecorder.maxAmplitude` purely while active to drive real-time VU waveform feedback
 * and auto-stops cleanly once [maxDurationMs] is reached.
 */
class AndroidVoiceRecorderManager(
    private val context: Context
) : VoiceRecorderController {

    private val _state = MutableStateFlow(VoiceRecordingState())
    override val state: StateFlow<VoiceRecordingState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var activeFile: File? = null
    private var meterJob: Job? = null
    private var startTimeMs: Long = 0L

    override fun startRecording(maxDurationMs: Long) {
        if (_state.value.isRecording) return
        cleanupTempRecorder()

        val outputDir = File(context.cacheDir, "sound_studio").apply { mkdirs() }
        val outputFile = File(outputDir, "voice_${UUID.randomUUID()}.m4a")
        activeFile = outputFile

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(SoundStickerSpecs.AUDIO_BITRATE)
            mr.setAudioSamplingRate(SoundStickerSpecs.AUDIO_SAMPLE_RATE)
            mr.setAudioChannels(1)
            mr.setMaxDuration(maxDurationMs.toInt())
            mr.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecording()
                }
            }
            mr.setOutputFile(outputFile.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            startTimeMs = System.currentTimeMillis()
            _state.value = VoiceRecordingState(
                isRecording = true,
                elapsedMs = 0L,
                amplitudeNormalized = 0f,
                recordedFile = null,
                error = null
            )

            meterJob = scope.launch {
                while (isActive && _state.value.isRecording) {
                    val elapsed = (System.currentTimeMillis() - startTimeMs).coerceAtMost(maxDurationMs)
                    val amp = try {
                        (recorder?.maxAmplitude ?: 0) / 32767f
                    } catch (_: Exception) {
                        0f
                    }.coerceIn(0f, 1f)

                    _state.update {
                        it.copy(elapsedMs = elapsed, amplitudeNormalized = amp)
                    }
                    if (elapsed >= maxDurationMs) {
                        stopRecording()
                        break
                    }
                    delay(60L)
                }
            }
        } catch (e: Exception) {
            try {
                mr.release()
            } catch (_: Exception) {
            }
            outputFile.delete()
            activeFile = null
            _state.value = VoiceRecordingState(
                isRecording = false,
                error = "No se pudo iniciar la grabación: ${e.message ?: "error de micrófono"}"
            )
        }
    }

    override fun stopRecording(): File? {
        meterJob?.cancel()
        meterJob = null
        val currentRecorder = recorder
        val file = activeFile
        recorder = null

        var valid = false
        if (currentRecorder != null) {
            try {
                currentRecorder.stop()
                valid = file != null && file.exists() && file.length() > 0L
            } catch (_: Exception) {
                file?.delete()
            } finally {
                try {
                    currentRecorder.release()
                } catch (_: Exception) {
                }
            }
        }

        _state.update {
            it.copy(
                isRecording = false,
                amplitudeNormalized = 0f,
                recordedFile = if (valid) file else null,
                error = if (!valid && it.error == null) "Grabación demasiado corta" else it.error
            )
        }
        return if (valid) file else null
    }

    override fun cancelRecording() {
        meterJob?.cancel()
        meterJob = null
        cleanupTempRecorder()
        activeFile?.delete()
        activeFile = null
        _state.value = VoiceRecordingState()
    }

    override fun release() {
        meterJob?.cancel()
        cleanupTempRecorder()
        scope.cancel()
    }

    private fun cleanupTempRecorder() {
        recorder?.let { mr ->
            try {
                mr.stop()
            } catch (_: Exception) {
            }
            try {
                mr.release()
            } catch (_: Exception) {
            }
        }
        recorder = null
    }
}

package com.seventv.stickers.core.media.sound

import android.net.Uri
import kotlin.math.roundToInt

/**
 * Technical specifications and pure media math for generating WhatsApp-compatible
 * Sound Sticker MP4 videos (512x512 H.264 + AAC-LC).
 */
object SoundStickerSpecs {
    const val VIDEO_WIDTH = 512
    const val VIDEO_HEIGHT = 512
    const val CANVAS_PADDING_PX = 16
    const val VIDEO_FPS = 24
    const val VIDEO_BITRATE = 1_500_000
    const val VIDEO_I_FRAME_INTERVAL_SEC = 1

    const val AUDIO_SAMPLE_RATE = 44100
    const val AUDIO_CHANNEL_COUNT = 2
    const val AUDIO_BITRATE = 128_000

    const val MIN_CLIP_DURATION_MS = 1_000L
    const val MAX_STATIC_CLIP_DURATION_MS = 10_000L
    const val MAX_ANIMATED_CLIP_DURATION_MS = 5_000L

    /**
     * Returns the maximum allowed sound sticker clip duration in milliseconds
     * according to WhatsApp's Sound Stickers guidelines.
     */
    fun maxDurationForVisual(isAnimated: Boolean): Long =
        if (isAnimated) MAX_ANIMATED_CLIP_DURATION_MS else MAX_STATIC_CLIP_DURATION_MS

    /**
     * Clamps [startMs] and [endMs] within `[0, totalDurationMs]` while guaranteeing
     * the clip stays within `[MIN_CLIP_DURATION_MS, maxClipDurationMs]` (unless source audio
     * itself is shorter than `MIN_CLIP_DURATION_MS`).
     */
    fun clampTrimRange(
        startMs: Long,
        endMs: Long,
        totalDurationMs: Long,
        maxClipDurationMs: Long
    ): Pair<Long, Long> {
        if (totalDurationMs <= 0L) return 0L to 0L
        val safeMaxClip = maxClipDurationMs.coerceAtLeast(MIN_CLIP_DURATION_MS)
        val effectiveMinClip = minOf(MIN_CLIP_DURATION_MS, totalDurationMs)

        var s = startMs.coerceIn(0L, (totalDurationMs - effectiveMinClip).coerceAtLeast(0L))
        var e = endMs.coerceIn(s + effectiveMinClip, totalDurationMs)

        if (e - s > safeMaxClip) {
            e = (s + safeMaxClip).coerceAtMost(totalDurationMs)
        }
        if (e - s < effectiveMinClip) {
            s = (e - effectiveMinClip).coerceAtLeast(0L)
        }
        return s to e
    }

    /**
     * Computes which animation frame index should be displayed at [elapsedMs]
     * when looping through [frameDurationsMs].
     */
    fun selectAnimationFrameIndex(elapsedMs: Long, frameDurationsMs: List<Int>): Int {
        if (frameDurationsMs.isEmpty()) return 0
        if (frameDurationsMs.size == 1) return 0

        val normalizedDurations = frameDurationsMs.map { if (it <= 10) 100 else it }
        var totalDuration = 0L
        for (d in normalizedDurations) {
            totalDuration += d
        }
        if (totalDuration <= 0L) return 0

        val loopPos = ((elapsedMs % totalDuration) + totalDuration) % totalDuration
        var accum = 0L
        for (i in normalizedDurations.indices) {
            accum += normalizedDurations[i]
            if (loopPos < accum) {
                return i
            }
        }
        return normalizedDurations.lastIndex
    }

    /**
     * Converts a packed ARGB_8888 pixel array of size [width] x [height] into
     * planar YUV420 buffers (Y of size width*height, U and V of size (width/2)*(height/2))
     * using ITU-R BT.601 studio-swing coefficients.
     */
    fun argbToPlanarYuv420(
        argb: IntArray,
        width: Int,
        height: Int,
        yOut: ByteArray,
        uOut: ByteArray,
        vOut: ByteArray
    ) {
        val frameSize = width * height
        val expectedUvSize = (width / 2) * (height / 2)
        require(argb.size >= frameSize) { "argb buffer too small" }
        require(yOut.size >= frameSize) { "yOut buffer too small" }
        require(uOut.size >= expectedUvSize && vOut.size >= expectedUvSize) { "uvOut buffer too small" }

        var uvIndex = 0
        for (j in 0 until height) {
            val rowOffset = j * width
            val isEvenRow = (j and 1) == 0
            for (i in 0 until width) {
                val pixel = argb[rowOffset + i]
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                // BT.601 limited range: Y in [16..235], U/V in [16..240]
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yOut[rowOffset + i] = y.coerceIn(0, 255).toByte()

                if (isEvenRow && (i and 1) == 0 && uvIndex < expectedUvSize) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    uOut[uvIndex] = u.coerceIn(0, 255).toByte()
                    vOut[uvIndex] = v.coerceIn(0, 255).toByte()
                    uvIndex++
                }
            }
        }
    }

    /**
     * Resamples 16-bit interleaved PCM [input] from [srcSampleRate] / [srcChannels]
     * to [dstSampleRate] / stereo (2 channels) using linear interpolation.
     */
    fun resamplePcmToStereo(
        input: ShortArray,
        inputCount: Int,
        srcSampleRate: Int,
        srcChannels: Int,
        dstSampleRate: Int = AUDIO_SAMPLE_RATE
    ): ShortArray {
        if (inputCount <= 0 || srcSampleRate <= 0 || srcChannels <= 0) return ShortArray(0)
        val srcFrames = inputCount / srcChannels
        if (srcFrames <= 0) return ShortArray(0)

        val dstFrames = ((srcFrames.toLong() * dstSampleRate) / srcSampleRate).toInt().coerceAtLeast(1)
        val out = ShortArray(dstFrames * 2)

        val ratio = srcFrames.toDouble() / dstFrames.toDouble()
        for (df in 0 until dstFrames) {
            val srcPos = df * ratio
            val idx0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val idx1 = (idx0 + 1).coerceIn(0, srcFrames - 1)
            val frac = srcPos - idx0

            val l0 = input[idx0 * srcChannels].toDouble()
            val l1 = input[idx1 * srcChannels].toDouble()
            val left = (l0 + (l1 - l0) * frac).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            val right = if (srcChannels >= 2) {
                val r0 = input[idx0 * srcChannels + 1].toDouble()
                val r1 = input[idx1 * srcChannels + 1].toDouble()
                (r0 + (r1 - r0) * frac).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            } else {
                left
            }

            out[df * 2] = left.toShort()
            out[df * 2 + 1] = right.toShort()
        }
        return out
    }
}

enum class StudioCanvasBackground(val label: String, val argbColor: Int) {
    WHATSAPP_DARK("WA Oscuro", 0xFF0B141A.toInt()),
    WHATSAPP_LIGHT("WA Claro", 0xFFEFEAE2.toInt()),
    NIGHT_PURPLE("Vaporwave", 0xFF0F081D.toInt()),
    PURE_BLACK("Negro", 0xFF000000.toInt()),
    PURE_WHITE("Blanco", 0xFFFFFFFF.toInt())
}

data class AudioTrackInfo(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val mimeType: String,
    val waveformBars: List<Float>
)

data class SoundStickerExportResult(
    val galleryUri: Uri,
    val shareFilePath: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val savedToGallery: Boolean
)

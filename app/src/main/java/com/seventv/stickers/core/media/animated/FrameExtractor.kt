package com.seventv.stickers.core.media.animated

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.model.AnimationFrame

/**
 * Interface for extracting animation frames from raw media byte streams.
 */
interface FrameExtractor {
    /**
     * Decodes and extracts all individual animation frames from [bytes].
     *
     * @param bytes Raw media file bytes.
     * @return [StickerResult.Success] containing the list of [AnimationFrame]s,
     * or [StickerResult.Failure] if the media data is corrupt, empty, or unparseable.
     */
    fun extractFrames(bytes: ByteArray): StickerResult<List<AnimationFrame>>
}

/**
 * Default implementation of [FrameExtractor] utilizing Android graphics decoding APIs.
 *
 * Supports decoding animated GIF sequences using [Movie] fallback, and static/WebP images
 * via [BitmapFactory], ensuring extracted bitmaps conform to ARGB_8888.
 */
class DefaultFrameExtractor : FrameExtractor {

    @Suppress("DEPRECATION")
    override fun extractFrames(bytes: ByteArray): StickerResult<List<AnimationFrame>> {
        if (bytes.isEmpty() || !isValidImageHeader(bytes)) {
            return StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        return try {
            // Attempt decoding as animated GIF using Movie
            val movie = try {
                Movie.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Throwable) {
                null
            }

            if (movie != null && movie.duration() > 0 && movie.width() > 0 && movie.height() > 0) {
                val frames = mutableListOf<AnimationFrame>()
                val duration = movie.duration()
                val frameStepMs = 100 // 10 fps default sampling if frame delays are uniform
                var timeMs = 0
                while (timeMs < duration) {
                    val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    movie.setTime(timeMs)
                    movie.draw(canvas, 0f, 0f)
                    val frameDuration = minOf(frameStepMs, duration - timeMs)
                    timeMs += frameDuration
                    frames.add(AnimationFrame(bitmap = bitmap, durationMs = frameDuration, timestampMs = timeMs))
                }
                if (frames.isNotEmpty()) {
                    return StickerResult.Success(frames)
                }
            }

            // Fallback for static images or single-frame WebP / PNG / JPEG
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (bitmap != null) {
                val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false).also {
                        bitmap.recycle()
                    }
                } else {
                    bitmap
                }
                StickerResult.Success(
                    listOf(
                        AnimationFrame(
                            bitmap = argbBitmap,
                            durationMs = 0,
                            timestampMs = 0
                        )
                    )
                )
            } else {
                StickerResult.Failure(StickerError.CorruptedSourceData)
            }
        } catch (_: Throwable) {
            StickerResult.Failure(StickerError.CorruptedSourceData)
        }
    }

    private fun isValidImageHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false

        // GIF: GIF87a or GIF89a
        if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) {
            return true
        }

        // PNG: 0x89 'P' 'N' 'G'
        if (bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()) {
            return true
        }

        // JPEG: 0xFF 0xD8 0xFF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return true
        }

        // WebP: RIFF....WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return true
        }

        // BMP: 'B' 'M'
        if (bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) {
            return true
        }

        return false
    }
}

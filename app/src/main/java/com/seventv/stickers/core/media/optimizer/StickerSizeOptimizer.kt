package com.seventv.stickers.core.media.optimizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.model.StickerSpecs
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Size optimizer that compresses static stickers and tray icons into compliant WebP files.
 * Uses a progressive quality degradation loop ([90, 80, 70, 60, 50]) and geometric downscaling
 * to guarantee compliance with WhatsApp byte limits.
 */
class StickerSizeOptimizer {

    companion object {
        private val QUALITY_STEPS = intArrayOf(90, 80, 70, 60, 50)
        private const val MAX_DOWNSCALE_ATTEMPTS = 3
        private const val DOWNSCALE_FACTOR = 0.9f
    }

    /**
     * Compresses [bitmap] into a static WebP sticker file at [outputFile], ensuring file size
     * does not exceed [StickerSpecs.STATIC_SAFE_CEILING_BYTES] (95 KB).
     */
    fun compressStaticWebP(bitmap: Bitmap, outputFile: File): StickerResult<Long> {
        return compressWithOptimizer(
            bitmap = bitmap,
            outputFile = outputFile,
            maxCeilingBytes = StickerSpecs.STATIC_SAFE_CEILING_BYTES
        )
    }

    /**
     * Compresses [bitmap] into a tray icon WebP file at [outputFile], ensuring file size
     * does not exceed [StickerSpecs.TRAY_ICON_MAX_BYTES] (50 KB).
     */
    fun compressTrayIcon(bitmap: Bitmap, outputFile: File): StickerResult<Long> {
        return compressWithOptimizer(
            bitmap = bitmap,
            outputFile = outputFile,
            maxCeilingBytes = StickerSpecs.TRAY_ICON_MAX_BYTES
        )
    }

    internal fun compressWithOptimizer(
        bitmap: Bitmap,
        outputFile: File,
        maxCeilingBytes: Long
    ): StickerResult<Long> {
        require(!bitmap.isRecycled) { "Bitmap must not be recycled" }
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap dimensions must be positive" }

        @Suppress("DEPRECATION")
        val compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

        val canvasWidth = bitmap.width
        val canvasHeight = bitmap.height
        var intermediateBitmap: Bitmap? = null
        var lastCompressedSize = 0L

        try {
            for (attempt in 0..MAX_DOWNSCALE_ATTEMPTS) {
                val currentBitmap: Bitmap
                if (attempt == 0) {
                    currentBitmap = bitmap
                } else {
                    val scale = DOWNSCALE_FACTOR.pow(attempt.toFloat())
                    val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                    val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)

                    val scaledBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(scaledBitmap)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

                    val left = (canvasWidth - scaledWidth) / 2f
                    val top = (canvasHeight - scaledHeight) / 2f
                    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                    val dstRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                    canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

                    intermediateBitmap?.recycle()
                    intermediateBitmap = scaledBitmap
                    currentBitmap = scaledBitmap
                }

                for (quality in QUALITY_STEPS) {
                    val outputStream = ByteArrayOutputStream()
                    val success = currentBitmap.compress(compressFormat, quality, outputStream)
                    if (!success) {
                        return StickerResult.Failure(
                            StickerError.NativeEncodingFailed("Bitmap WebP compression returned false")
                        )
                    }

                    val compressedBytes = outputStream.toByteArray()
                    lastCompressedSize = compressedBytes.size.toLong()

                    if (lastCompressedSize <= maxCeilingBytes) {
                        return try {
                            outputFile.parentFile?.mkdirs()
                            outputFile.writeBytes(compressedBytes)
                            StickerResult.Success(outputFile.length())
                        } catch (e: Exception) {
                            StickerResult.Failure(StickerError.StorageIoError(e))
                        }
                    }
                }
            }

            return StickerResult.Failure(
                StickerError.ExceedsSizeLimit(
                    actualBytes = lastCompressedSize,
                    maxBytes = maxCeilingBytes,
                    canFallbackToStatic = false
                )
            )
        } finally {
            intermediateBitmap?.recycle()
        }
    }
}

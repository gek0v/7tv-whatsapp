package com.seventv.stickers.core.media

import android.graphics.Bitmap
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.animated.AnimatedWebPEncoder
import com.seventv.stickers.core.media.animated.DefaultFrameExtractor
import com.seventv.stickers.core.media.animated.FrameExtractor
import com.seventv.stickers.core.media.animated.FrameThrottler
import com.seventv.stickers.core.media.animated.NativeWebPAnimEncoder
import com.seventv.stickers.core.media.model.AnimationFrame
import com.seventv.stickers.core.media.model.ProcessedSticker
import com.seventv.stickers.core.media.model.RawMediaInput
import com.seventv.stickers.core.media.model.StickerSpecs
import com.seventv.stickers.core.media.normalizer.CanvasStickerNormalizer
import com.seventv.stickers.core.media.normalizer.StickerNormalizer
import com.seventv.stickers.core.media.optimizer.StickerSizeOptimizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Top-level media pipeline facade integrating normalization, size optimization,
 * animation frame extraction, duration/framerate throttling, and WebP encoding.
 *
 * Implements strict compliance with WhatsApp sticker technical constraints:
 * - 512x512 canvas with 16px transparent margin (usable 480x480)
 * - Static stickers <= 95 KB safe ceiling
 * - Animated stickers <= 480 KB safe ceiling, <= 3000 ms duration, <= 30 fps
 * - Tray icons 96x96 with 1px transparent margin <= 50 KB
 */
class StickerMediaPipeline(
    private val normalizer: StickerNormalizer = CanvasStickerNormalizer(),
    private val sizeOptimizer: StickerSizeOptimizer = StickerSizeOptimizer(normalizer),
    private val frameExtractor: FrameExtractor = DefaultFrameExtractor(),
    private val frameThrottler: FrameThrottler = FrameThrottler(),
    private val animatedWebPEncoder: AnimatedWebPEncoder = NativeWebPAnimEncoder(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private val ANIMATED_QUALITY_STEPS = floatArrayOf(75f, 65f, 50f, 40f)
        private const val SECONDARY_DECIMATE_FPS = 15
    }

    /**
     * Processes raw media input into a WhatsApp-compliant static or animated sticker.
     *
     * @param input Raw media input bytes and metadata.
     * @param outputDir Destination directory for the generated sticker file.
     * @return [StickerResult.Success] containing the [ProcessedSticker], or [StickerResult.Failure].
     */
    suspend fun processSticker(
        input: RawMediaInput,
        outputDir: File
    ): StickerResult<ProcessedSticker> = withContext(dispatcher) {
        if (input.bytes.isEmpty()) {
            return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
        }
        outputDir.mkdirs()

        if (!input.isAnimated) {
            processStaticInternal(input.bytes, outputDir)
        } else {
            processAnimatedInternal(input.bytes, outputDir)
        }
    }

    /**
     * Processes a source bitmap into a compliant 96x96 tray icon WebP file.
     *
     * @param sourceBitmap Source bitmap to normalize and compress.
     * @param outputFile Target file where the tray icon will be stored.
     * @return [StickerResult.Success] containing the output file, or [StickerResult.Failure].
     */
    suspend fun processTrayIcon(
        sourceBitmap: Bitmap,
        outputFile: File
    ): StickerResult<File> = withContext(dispatcher) {
        if (sourceBitmap.isRecycled || sourceBitmap.width <= 0 || sourceBitmap.height <= 0) {
            return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        val normalized = try {
            normalizer.normalizeToTrayIcon(sourceBitmap)
        } catch (_: Exception) {
            return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        try {
            val compressResult = sizeOptimizer.compressTrayIcon(normalized, outputFile)
            when (compressResult) {
                is StickerResult.Success -> {
                    val actualBytes = outputFile.length()
                    if (actualBytes <= StickerSpecs.TRAY_ICON_MAX_BYTES) {
                        StickerResult.Success(outputFile)
                    } else {
                        outputFile.delete()
                        StickerResult.Failure(
                            StickerError.ExceedsSizeLimit(
                                actualBytes = actualBytes,
                                maxBytes = StickerSpecs.TRAY_ICON_MAX_BYTES,
                                canFallbackToStatic = false
                            )
                        )
                    }
                }
                is StickerResult.Failure -> {
                    outputFile.delete()
                    compressResult
                }
            }
        } finally {
            if (!normalized.isRecycled && normalized !== sourceBitmap) {
                normalized.recycle()
            }
        }
    }

    /**
     * Extracts the initial frame from animated or static media input and processes it
     * into a compliant static sticker (<= 95 KB).
     *
     * @param input Raw media input (typically animated sticker that failed size constraints).
     * @param outputDir Destination directory for the generated fallback static sticker.
     * @return [StickerResult.Success] containing the static [ProcessedSticker], or [StickerResult.Failure].
     */
    suspend fun processAsStaticFallback(
        input: RawMediaInput,
        outputDir: File
    ): StickerResult<ProcessedSticker> = withContext(dispatcher) {
        if (input.bytes.isEmpty()) {
            return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
        }
        outputDir.mkdirs()
        processStaticInternal(input.bytes, outputDir)
    }

    private fun processStaticInternal(
        bytes: ByteArray,
        outputDir: File
    ): StickerResult<ProcessedSticker> {
        val extractResult = frameExtractor.extractFrames(bytes)
        val frames = when (extractResult) {
            is StickerResult.Success -> extractResult.data
            is StickerResult.Failure -> return StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        if (frames.isEmpty()) {
            return StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        val firstFrame = frames.first()
        val sourceBitmap = firstFrame.bitmap

        // Recycle extra frames if sequence has more than one
        for (i in 1 until frames.size) {
            val extraBitmap = frames[i].bitmap
            if (extraBitmap !== sourceBitmap && !extraBitmap.isRecycled) {
                extraBitmap.recycle()
            }
        }

        val targetFile = File(outputDir, "${UUID.randomUUID()}.webp")
        return try {
            val normalized = normalizer.normalizeToStickerCanvas(sourceBitmap)
            try {
                val compressResult = sizeOptimizer.compressStaticWebP(normalized, targetFile)
                when (compressResult) {
                    is StickerResult.Success -> {
                        val byteSize = targetFile.length()
                        if (byteSize <= StickerSpecs.STATIC_SAFE_CEILING_BYTES) {
                            StickerResult.Success(
                                ProcessedSticker(
                                    file = targetFile,
                                    width = StickerSpecs.STICKER_SIZE,
                                    height = StickerSpecs.STICKER_SIZE,
                                    byteSize = byteSize,
                                    isAnimated = false,
                                    durationMs = 0,
                                    frameCount = 1
                                )
                            )
                        } else {
                            targetFile.delete()
                            StickerResult.Failure(
                                StickerError.ExceedsSizeLimit(
                                    actualBytes = byteSize,
                                    maxBytes = StickerSpecs.STATIC_SAFE_CEILING_BYTES,
                                    canFallbackToStatic = false
                                )
                            )
                        }
                    }
                    is StickerResult.Failure -> {
                        targetFile.delete()
                        compressResult
                    }
                }
            } finally {
                if (!normalized.isRecycled && normalized !== sourceBitmap) {
                    normalized.recycle()
                }
            }
        } catch (_: IllegalArgumentException) {
            targetFile.delete()
            StickerResult.Failure(StickerError.CorruptedSourceData)
        } catch (e: Exception) {
            targetFile.delete()
            StickerResult.Failure(StickerError.StorageIoError(e))
        } finally {
            if (!sourceBitmap.isRecycled) {
                sourceBitmap.recycle()
            }
        }
    }

    private fun processAnimatedInternal(
        bytes: ByteArray,
        outputDir: File
    ): StickerResult<ProcessedSticker> {
        val extractResult = frameExtractor.extractFrames(bytes)
        val rawFrames = when (extractResult) {
            is StickerResult.Success -> extractResult.data
            is StickerResult.Failure -> return StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        if (rawFrames.isEmpty()) {
            return StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        // Truncate sequence duration to <= 3000 ms (FrameThrottler recycles dropped frames)
        val truncatedFrames = frameThrottler.truncateDuration(rawFrames, StickerSpecs.ANIMATED_MAX_DURATION_MS)
        if (truncatedFrames.isEmpty()) {
            return StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        // Decimate framerate if source FPS > 30 fps (FrameThrottler recycles dropped frames)
        var currentFrames = frameThrottler.decimateFramerate(truncatedFrames, StickerSpecs.ANIMATED_MAX_FPS)
        if (currentFrames.isEmpty()) {
            return StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        var isFramesRecycled = false
        return try {
            var lastExceededSize = 0L

            // Primary adaptive compression loop at <= 30 fps
            val primaryResult = tryEncodeFrameSequence(currentFrames, outputDir)
            if (primaryResult is TryEncodeResult.Success) {
                recycleFrames(currentFrames)
                isFramesRecycled = true
                return StickerResult.Success(primaryResult.sticker)
            } else if (primaryResult is TryEncodeResult.ExceededLimit) {
                lastExceededSize = primaryResult.lastExceededBytes
            }

            // Secondary decimation step to 15 fps if still exceeding 480 KB and frames can be further reduced
            val decimated15Frames = frameThrottler.decimateFramerate(currentFrames, SECONDARY_DECIMATE_FPS)
            val didDecimateFurther = decimated15Frames.size < currentFrames.size
            currentFrames = decimated15Frames

            if (didDecimateFurther && decimated15Frames.isNotEmpty()) {
                val secondaryResult = tryEncodeFrameSequence(decimated15Frames, outputDir)
                if (secondaryResult is TryEncodeResult.Success) {
                    recycleFrames(decimated15Frames)
                    isFramesRecycled = true
                    return StickerResult.Success(secondaryResult.sticker)
                } else if (secondaryResult is TryEncodeResult.ExceededLimit) {
                    lastExceededSize = secondaryResult.lastExceededBytes
                }
            }

            recycleFrames(currentFrames)
            isFramesRecycled = true

            if (lastExceededSize > 0L) {
                StickerResult.Failure(
                    StickerError.ExceedsSizeLimit(
                        actualBytes = lastExceededSize,
                        maxBytes = StickerSpecs.ANIMATED_MAX_BYTES,
                        canFallbackToStatic = true
                    )
                )
            } else {
                StickerResult.Failure(
                    StickerError.NativeEncodingFailed("Animated WebP encoding failed")
                )
            }
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        } finally {
            if (!isFramesRecycled) {
                recycleFrames(currentFrames)
            }
        }
    }

    private fun tryEncodeFrameSequence(
        frames: List<AnimationFrame>,
        outputDir: File
    ): TryEncodeResult {
        val normalizedBitmaps = mutableListOf<Bitmap>()
        try {
            for (frame in frames) {
                normalizedBitmaps.add(normalizer.normalizeToStickerCanvas(frame.bitmap))
            }

            val bitmapsArray = normalizedBitmaps.toTypedArray()
            val timestampsArray = frames.map { it.timestampMs }.toIntArray()
            val totalDurationMs = frames.lastOrNull()?.timestampMs?.takeIf { it > 0 }
                ?: frames.sumOf { it.durationMs }
            var lastExceededSize = 0L

            val qualitySteps = when {
                frames.size > 25 -> floatArrayOf(55f, 40f)
                frames.size > 12 -> floatArrayOf(65f, 50f, 40f)
                else -> ANIMATED_QUALITY_STEPS
            }

            for (quality in qualitySteps) {
                val tempFile = File.createTempFile("anim_opt_", ".webp", outputDir)
                try {
                    val encoded = animatedWebPEncoder.encode(
                        bitmaps = bitmapsArray,
                        timestampsMs = timestampsArray,
                        quality = quality,
                        outputPath = tempFile.absolutePath
                    )

                    if (encoded && tempFile.exists()) {
                        val currentSize = tempFile.length()
                        if (currentSize in 1..StickerSpecs.ANIMATED_SAFE_CEILING_BYTES) {
                            val targetFile = File(outputDir, "${UUID.randomUUID()}.webp")
                            if (!tempFile.renameTo(targetFile)) {
                                tempFile.copyTo(targetFile, overwrite = true)
                                tempFile.delete()
                            }
                            return TryEncodeResult.Success(
                                ProcessedSticker(
                                    file = targetFile,
                                    width = StickerSpecs.STICKER_SIZE,
                                    height = StickerSpecs.STICKER_SIZE,
                                    byteSize = targetFile.length(),
                                    isAnimated = true,
                                    durationMs = totalDurationMs,
                                    frameCount = frames.size
                                )
                            )
                        } else {
                            lastExceededSize = currentSize
                        }
                    }
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            }

            return if (lastExceededSize > 0L) {
                TryEncodeResult.ExceededLimit(lastExceededSize)
            } else {
                TryEncodeResult.EncodingFailed
            }
        } finally {
            for (bitmap in normalizedBitmaps) {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun recycleFrames(frames: List<AnimationFrame>) {
        for (frame in frames) {
            if (!frame.bitmap.isRecycled) {
                frame.bitmap.recycle()
            }
        }
    }

    private sealed interface TryEncodeResult {
        data class Success(val sticker: ProcessedSticker) : TryEncodeResult
        data class ExceededLimit(val lastExceededBytes: Long) : TryEncodeResult
        data object EncodingFailed : TryEncodeResult
    }
}

typealias DefaultStickerMediaPipeline = StickerMediaPipeline


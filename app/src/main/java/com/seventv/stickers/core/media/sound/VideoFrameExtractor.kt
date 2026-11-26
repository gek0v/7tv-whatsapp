package com.seventv.stickers.core.media.sound

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.model.AnimationFrame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Result of extracting scaled animation frames from a local video URI.
 */
data class ExtractedVideoClip(
    val frames: List<AnimationFrame>,
    val clipDurationMs: Long,
    val totalVideoDurationMs: Long,
    val hasAudioTrack: Boolean
)

/**
 * Single sampling slot computed for bounded video frame extraction.
 */
data class VideoSamplePoint(
    val timestampMs: Long,
    val durationMs: Int,
    val cumulativeTimestampMs: Int
)

/**
 * Extracts scaled animation frames and inspects audio presence from local video URIs
 * (MP4, WebM, etc.) for Sticker Studio sound sticker creation.
 */
interface VideoFrameExtractor {
    suspend fun extractFrames(
        uri: Uri,
        startMs: Long = 0L,
        maxDurationMs: Long = SoundStickerSpecs.MAX_ANIMATED_CLIP_DURATION_MS,
        targetFps: Int = DEFAULT_TARGET_FPS,
        maxDimensionPx: Int = SoundStickerSpecs.VIDEO_WIDTH
    ): StickerResult<ExtractedVideoClip>

    suspend fun extractThumbnail(
        uri: Uri,
        maxDimensionPx: Int = SoundStickerSpecs.VIDEO_WIDTH
    ): StickerResult<Bitmap>

    suspend fun hasAudioTrack(uri: Uri): Boolean

    companion object {
        const val DEFAULT_TARGET_FPS = 15
        const val MIN_TARGET_FPS = 1
        const val MAX_TARGET_FPS = 24
        const val MAX_EXTRACTED_FRAMES = 90
    }
}

internal interface VideoRetrieverClient : AutoCloseable {
    fun setDataSource(context: Context, uri: Uri)
    fun extractMetadata(keyCode: Int): String?
    fun getFrame(timeUs: Long, maxDimensionPx: Int): Bitmap?
}

internal interface VideoExtractorClient : AutoCloseable {
    fun setDataSource(context: Context, uri: Uri)
    val trackCount: Int
    fun getTrackMime(index: Int): String?
}

/**
 * Android implementation backed by [MediaMetadataRetriever] for bounded, scaled frame decoding
 * and [MediaExtractor] for audio track detection.
 */
class AndroidVideoFrameExtractor internal constructor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
    private val retrieverFactory: () -> VideoRetrieverClient,
    private val extractorFactory: () -> VideoExtractorClient
) : VideoFrameExtractor {

    constructor(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        context = context.applicationContext,
        dispatcher = dispatcher,
        retrieverFactory = { PlatformVideoRetrieverClient() },
        extractorFactory = { PlatformVideoExtractorClient() }
    )

    override suspend fun extractFrames(
        uri: Uri,
        startMs: Long,
        maxDurationMs: Long,
        targetFps: Int,
        maxDimensionPx: Int
    ): StickerResult<ExtractedVideoClip> = withContext(dispatcher) {
        val hasAudio = checkHasAudioTrack(uri)
        val frames = mutableListOf<AnimationFrame>()

        try {
            retrieverFactory().use { retriever ->
                retriever.setDataSource(context, uri)
                val totalDurationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L

                if (totalDurationMs <= 0L) {
                    return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
                }

                val safeMaxDurationMs = maxDurationMs.coerceIn(1L, SoundStickerSpecs.MAX_ANIMATED_CLIP_DURATION_MS)
                val safeStartMs = startMs.coerceIn(0L, (totalDurationMs - 1L).coerceAtLeast(0L))
                val availableMs = (totalDurationMs - safeStartMs).coerceAtLeast(1L)
                val clipDurationMs = minOf(availableMs, safeMaxDurationMs)
                val safeMaxDim = maxDimensionPx.coerceIn(1, SoundStickerSpecs.VIDEO_WIDTH)

                val samplePoints = computeSamplingPoints(
                    startMs = safeStartMs,
                    clipDurationMs = clipDurationMs,
                    targetFps = targetFps,
                    maxFrames = VideoFrameExtractor.MAX_EXTRACTED_FRAMES
                )

                var accumTimestamp = 0
                for (point in samplePoints) {
                    val decoded = retriever.getFrame(point.timestampMs * 1000L, safeMaxDim)
                    if (decoded != null) {
                        val normalized = normalizeBitmap(decoded, safeMaxDim)
                        accumTimestamp += point.durationMs
                        frames.add(
                            AnimationFrame(
                                bitmap = normalized,
                                durationMs = point.durationMs,
                                timestampMs = accumTimestamp
                            )
                        )
                    } else if (frames.isNotEmpty()) {
                        // Extend previous frame duration if a single timestamp fails to decode
                        val lastIdx = frames.lastIndex
                        val prev = frames[lastIdx]
                        accumTimestamp += point.durationMs
                        frames[lastIdx] = prev.copy(
                            durationMs = prev.durationMs + point.durationMs,
                            timestampMs = accumTimestamp
                        )
                    }
                }

                if (frames.isEmpty()) {
                    return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
                }

                StickerResult.Success(
                    ExtractedVideoClip(
                        frames = frames,
                        clipDurationMs = clipDurationMs,
                        totalVideoDurationMs = totalDurationMs,
                        hasAudioTrack = hasAudio
                    )
                )
            }
        } catch (e: OutOfMemoryError) {
            recycleFrames(frames)
            StickerResult.Failure(StickerError.NativeEncodingFailed("Memoria insuficiente al extraer fotogramas del vídeo"))
        } catch (e: Exception) {
            recycleFrames(frames)
            StickerResult.Failure(StickerError.CorruptedSourceData)
        }
    }

    override suspend fun extractThumbnail(
        uri: Uri,
        maxDimensionPx: Int
    ): StickerResult<Bitmap> = withContext(dispatcher) {
        val safeMaxDim = maxDimensionPx.coerceIn(1, SoundStickerSpecs.VIDEO_WIDTH)
        try {
            retrieverFactory().use { retriever ->
                retriever.setDataSource(context, uri)
                val raw = retriever.getFrame(0L, safeMaxDim)
                    ?: retriever.getFrame(100_000L, safeMaxDim)
                    ?: return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
                StickerResult.Success(normalizeBitmap(raw, safeMaxDim))
            }
        } catch (e: OutOfMemoryError) {
            StickerResult.Failure(StickerError.NativeEncodingFailed("Memoria insuficiente al leer miniatura del vídeo"))
        } catch (_: Exception) {
            StickerResult.Failure(StickerError.CorruptedSourceData)
        }
    }

    override suspend fun hasAudioTrack(uri: Uri): Boolean = withContext(dispatcher) {
        checkHasAudioTrack(uri)
    }

    private fun checkHasAudioTrack(uri: Uri): Boolean {
        return try {
            extractorFactory().use { extractor ->
                extractor.setDataSource(context, uri)
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackMime(i) ?: continue
                    if (mime.startsWith("audio/", ignoreCase = true)) {
                        return@use true
                    }
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun recycleFrames(frames: List<AnimationFrame>) {
        for (frame in frames) {
            if (!frame.bitmap.isRecycled) {
                frame.bitmap.recycle()
            }
        }
    }

    companion object {
        /**
         * Computes evenly spaced sampling timestamps bounded by [SoundStickerSpecs.MAX_ANIMATED_CLIP_DURATION_MS]
         * and [maxFrames] so memory stays strictly bounded regardless of source video length or FPS.
         */
        fun computeSamplingPoints(
            startMs: Long = 0L,
            clipDurationMs: Long,
            targetFps: Int = VideoFrameExtractor.DEFAULT_TARGET_FPS,
            maxFrames: Int = VideoFrameExtractor.MAX_EXTRACTED_FRAMES
        ): List<VideoSamplePoint> {
            val safeDuration = clipDurationMs.coerceIn(1L, SoundStickerSpecs.MAX_ANIMATED_CLIP_DURATION_MS)
            val safeStart = startMs.coerceAtLeast(0L)
            val clampedFps = targetFps.coerceIn(VideoFrameExtractor.MIN_TARGET_FPS, VideoFrameExtractor.MAX_TARGET_FPS)
            val safeMaxFrames = maxFrames.coerceAtLeast(1)

            val desiredFrames = ((safeDuration * clampedFps) / 1000.0).roundToInt().coerceAtLeast(1)
            val frameCount = minOf(desiredFrames, safeMaxFrames)

            return List(frameCount) { idx ->
                val relStart = (idx * safeDuration) / frameCount
                val relEnd = ((idx + 1) * safeDuration) / frameCount
                val duration = (relEnd - relStart).toInt().coerceAtLeast(1)
                VideoSamplePoint(
                    timestampMs = safeStart + relStart,
                    durationMs = duration,
                    cumulativeTimestampMs = relEnd.toInt()
                )
            }
        }

        /**
         * Computes aspect-preserving target dimensions bounded by [maxDimensionPx].
         * Smaller dimensions are never upscaled.
         */
        fun computeScaledDimensions(
            width: Int,
            height: Int,
            maxDimensionPx: Int = SoundStickerSpecs.VIDEO_WIDTH
        ): Pair<Int, Int> {
            val safeMax = maxDimensionPx.coerceAtLeast(1)
            if (width <= 0 || height <= 0) {
                return safeMax to safeMax
            }
            if (width <= safeMax && height <= safeMax) {
                return width to height
            }
            val scale = safeMax.toDouble() / maxOf(width, height).toDouble()
            val scaledW = (width * scale).roundToInt().coerceIn(1, safeMax)
            val scaledH = (height * scale).roundToInt().coerceIn(1, safeMax)
            return scaledW to scaledH
        }

        /**
         * Ensures [bitmap] fits inside [maxDimensionPx] and uses ARGB_8888 config,
         * recycling any replaced intermediate bitmap.
         */
        fun normalizeBitmap(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
            var current = bitmap
            if (current.width > maxDimensionPx || current.height > maxDimensionPx) {
                val (dstW, dstH) = computeScaledDimensions(current.width, current.height, maxDimensionPx)
                val scaled = Bitmap.createScaledBitmap(current, dstW, dstH, true)
                if (scaled !== current) {
                    current.recycle()
                    current = scaled
                }
            }
            if (current.config != Bitmap.Config.ARGB_8888) {
                val argb = current.copy(Bitmap.Config.ARGB_8888, false)
                if (argb != null) {
                    current.recycle()
                    current = argb
                }
            }
            return current
        }

        /**
         * Synchronous helper for UI preview thumbnails (called on a background dispatcher).
         */
        fun extractThumbnailSync(
            context: Context,
            uri: Uri,
            maxDimensionPx: Int = SoundStickerSpecs.VIDEO_WIDTH
        ): Bitmap? {
            val safeMaxDim = maxDimensionPx.coerceIn(1, SoundStickerSpecs.VIDEO_WIDTH)
            return try {
                PlatformVideoRetrieverClient().use { retriever ->
                    retriever.setDataSource(context, uri)
                    val raw = retriever.getFrame(0L, safeMaxDim)
                        ?: retriever.getFrame(100_000L, safeMaxDim)
                        ?: return null
                    normalizeBitmap(raw, safeMaxDim)
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}

private class PlatformVideoRetrieverClient : VideoRetrieverClient {
    private val retriever = MediaMetadataRetriever()
    private var maxSrcDim: Int? = null

    override fun setDataSource(context: Context, uri: Uri) {
        retriever.setDataSource(context, uri)
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        maxSrcDim = maxOf(w, h).takeIf { it > 0 }
    }

    override fun extractMetadata(keyCode: Int): String? = retriever.extractMetadata(keyCode)

    override fun getFrame(timeUs: Long, maxDimensionPx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val bound = minOf(maxSrcDim ?: maxDimensionPx, maxDimensionPx).coerceAtLeast(1)
            val scaled = try {
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, bound, bound)
            } catch (_: Exception) {
                null
            }
            if (scaled != null) return scaled

            return try {
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, bound, bound)
            } catch (_: Exception) {
                null
            }
        }

        val closest = try {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: Exception) {
            null
        }
        return closest ?: try {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}

private class PlatformVideoExtractorClient : VideoExtractorClient {
    private val extractor = MediaExtractor()

    override fun setDataSource(context: Context, uri: Uri) {
        extractor.setDataSource(context, uri, null)
    }

    override val trackCount: Int
        get() = extractor.trackCount

    override fun getTrackMime(index: Int): String? =
        extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)

    override fun close() {
        try {
            extractor.release()
        } catch (_: Exception) {
        }
    }
}

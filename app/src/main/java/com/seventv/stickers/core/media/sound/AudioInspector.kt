package com.seventv.stickers.core.media.sound

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

interface AudioInspector {
    suspend fun inspect(uri: Uri): StickerResult<AudioTrackInfo>
}

/**
 * Reads audio metadata (duration, sample rate, channel count) and constructs
 * a lightweight normalized waveform preview for any local audio or MP4 file.
 */
class AndroidAudioInspector(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AudioInspector {

    companion object {
        private const val WAVEFORM_BAR_COUNT = 40
    }

    override suspend fun inspect(uri: Uri): StickerResult<AudioTrackInfo> = withContext(dispatcher) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/*"
            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                SoundStickerSpecs.AUDIO_SAMPLE_RATE
            }
            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }

            var durationMs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION) / 1000L
            } else {
                0L
            }

            if (durationMs <= 0L) {
                durationMs = readDurationWithRetriever(uri)
            }

            // Sample encoded packet sizes across buckets to generate responsive waveform bars
            val waveform = sampleWaveformBars(extractor, durationMs)

            if (durationMs <= 0L) {
                // Last-resort duration check after scanning samples
                durationMs = readLastSampleDurationMs(extractor)
            }

            if (durationMs <= 0L) {
                return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
            }

            val displayName = resolveDisplayName(uri)
            StickerResult.Success(
                AudioTrackInfo(
                    uri = uri,
                    displayName = displayName,
                    durationMs = durationMs,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    mimeType = mime,
                    waveformBars = waveform
                )
            )
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun readDurationWithRetriever(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun readLastSampleDurationMs(extractor: MediaExtractor): Long {
        extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        var maxPtsUs = 0L
        var safetyCount = 0
        while (safetyCount < 50_000) {
            val pts = extractor.sampleTime
            if (pts < 0L) break
            if (pts > maxPtsUs) maxPtsUs = pts
            if (!extractor.advance()) break
            safetyCount++
        }
        return (maxPtsUs / 1000L).coerceAtLeast(0L)
    }

    private fun sampleWaveformBars(extractor: MediaExtractor, durationMs: Long): List<Float> {
        val buckets = DoubleArray(WAVEFORM_BAR_COUNT)
        val counts = IntArray(WAVEFORM_BAR_COUNT)

        if (durationMs > 0L) {
            // Seek-based fast sampling: jump across WAVEFORM_BAR_COUNT evenly spaced timestamps
            for (b in 0 until WAVEFORM_BAR_COUNT) {
                val targetUs = ((durationMs * b) / WAVEFORM_BAR_COUNT) * 1000L
                extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                var sum = 0.0
                var read = 0
                while (read < 4) {
                    val sampleSize = extractor.sampleSize
                    if (sampleSize <= 0L) break
                    sum += sampleSize.toDouble()
                    read++
                    if (!extractor.advance()) break
                }
                if (read > 0) {
                    buckets[b] = sqrt(sum / read)
                    counts[b] = read
                }
            }
        }

        val maxVal = buckets.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val hasRealData = counts.any { it > 0 }
        return List(WAVEFORM_BAR_COUNT) { idx ->
            if (!hasRealData) {
                // Subtle rhythmic fallback pattern if container lacks index
                val phase = (idx % 6) / 5f
                0.25f + 0.55f * phase
            } else {
                (buckets[idx] / maxVal).toFloat().coerceIn(0.15f, 1.0f)
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "audio.m4a"
        }
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } ?: (uri.lastPathSegment ?: "Audio importado")
        } catch (_: Exception) {
            uri.lastPathSegment ?: "Audio importado"
        }
    }
}

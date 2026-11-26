package com.seventv.stickers.core.media.sound

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.animated.DefaultFrameExtractor
import com.seventv.stickers.core.media.animated.FrameExtractor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

interface SoundStickerExporter {
    suspend fun exportMp4(
        visualBytes: ByteArray,
        audioUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        background: StudioCanvasBackground,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): StickerResult<SoundStickerExportResult>
}

/**
 * Pure Android MediaCodec + MediaExtractor + MediaMuxer pipeline that produces a square 512x512
 * H.264/AAC MP4 compatible with WhatsApp's native "Create Sticker" gallery import for Sound Stickers.
 */
class DefaultSoundStickerExporter(
    private val context: Context,
    private val frameExtractor: FrameExtractor = DefaultFrameExtractor(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SoundStickerExporter {

    companion object {
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val FILE_PROVIDER_AUTHORITY = "com.seventv.stickers.fileprovider"
    }

    private class YuvFrame(
        val y: ByteArray,
        val u: ByteArray,
        val v: ByteArray
    )

    override suspend fun exportMp4(
        visualBytes: ByteArray,
        audioUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        background: StudioCanvasBackground,
        onProgress: (Float, String) -> Unit
    ): StickerResult<SoundStickerExportResult> = withContext(dispatcher) {
        if (visualBytes.isEmpty()) {
            return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
        }
        val clipDurationMs = trimEndMs - trimStartMs
        if (clipDurationMs < 250L) {
            return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        val studioDir = File(context.cacheDir, "sound_studio").apply { mkdirs() }
        val jobId = UUID.randomUUID().toString().take(8)
        val tempVideo = File(studioDir, "tmp_vid_$jobId.mp4")
        val tempAudio = File(studioDir, "tmp_aud_$jobId.mp4")
        val outputFile = File(studioDir, "7tv_sound_sticker_${System.currentTimeMillis()}.mp4")

        try {
            onProgress(0.05f, "Extrayendo fotogramas...")
            val extractedResult = frameExtractor.extractFrames(visualBytes)
            val rawFrames = when (extractedResult) {
                is StickerResult.Success -> extractedResult.data
                is StickerResult.Failure -> return@withContext extractedResult
            }
            if (rawFrames.isEmpty()) {
                return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
            }

            // Limit precomputed unique frames to keep memory bounded on huge GIFs
            val yuvFrames = mutableListOf<YuvFrame>()
            val frameDurations = mutableListOf<Int>()
            try {
                for (frame in rawFrames) {
                    yuvFrames.add(convertFrameToYuv(frame.bitmap, background.argbColor))
                    frameDurations.add(frame.durationMs)
                }
            } finally {
                for (frame in rawFrames) {
                    if (!frame.bitmap.isRecycled) {
                        frame.bitmap.recycle()
                    }
                }
            }

            onProgress(0.20f, "Codificando vídeo 512x512...")
            encodeVideoTrack(
                yuvFrames = yuvFrames,
                frameDurations = frameDurations,
                durationMs = clipDurationMs,
                outputFile = tempVideo,
                onVideoProgress = { frac ->
                    onProgress(0.20f + frac * 0.40f, "Codificando vídeo 512x512...")
                }
            )

            onProgress(0.62f, "Recortando y codificando audio...")
            encodeTrimmedAudioTrack(
                audioUri = audioUri,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                outputFile = tempAudio
            )

            onProgress(0.85f, "Ensamblando MP4 final...")
            remuxTracks(
                videoFile = tempVideo,
                audioFile = tempAudio,
                outputFile = outputFile
            )

            if (!outputFile.exists() || outputFile.length() <= 0L) {
                return@withContext StickerResult.Failure(StickerError.NativeEncodingFailed("El MP4 generado está vacío"))
            }

            onProgress(0.95f, "Guardando en la galería...")
            val (galleryUri, savedToGallery) = saveToGalleryOrShareUri(outputFile)

            onProgress(1.0f, "¡Listo!")
            StickerResult.Success(
                SoundStickerExportResult(
                    galleryUri = galleryUri,
                    shareFilePath = outputFile.absolutePath,
                    fileSizeBytes = outputFile.length(),
                    durationMs = clipDurationMs,
                    savedToGallery = savedToGallery
                )
            )
        } catch (e: OutOfMemoryError) {
            outputFile.delete()
            StickerResult.Failure(StickerError.NativeEncodingFailed("Memoria insuficiente para procesar el sticker"))
        } catch (e: Exception) {
            outputFile.delete()
            StickerResult.Failure(StickerError.NativeEncodingFailed(e.message ?: "Error generando MP4"))
        } finally {
            tempVideo.delete()
            tempAudio.delete()
        }
    }

    private fun convertFrameToYuv(source: Bitmap, bgArgb: Int): YuvFrame {
        val w = SoundStickerSpecs.VIDEO_WIDTH
        val h = SoundStickerSpecs.VIDEO_HEIGHT
        val pad = SoundStickerSpecs.CANVAS_PADDING_PX
        val normalized = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        try {
            val canvas = Canvas(normalized)
            canvas.drawColor(bgArgb)

            val usableW = (w - 2 * pad).toFloat()
            val usableH = (h - 2 * pad).toFloat()
            val scale = minOf(usableW / source.width.coerceAtLeast(1), usableH / source.height.coerceAtLeast(1))
            val dstW = source.width * scale
            val dstH = source.height * scale
            val left = (w - dstW) / 2f
            val top = (h - dstH) / 2f
            val dstRect = RectF(left, top, left + dstW, top + dstH)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, null, dstRect, paint)

            normalized.getPixels(pixels, 0, w, 0, 0, w, h)
        } finally {
            normalized.recycle()
        }

        val yOut = ByteArray(w * h)
        val uOut = ByteArray((w / 2) * (h / 2))
        val vOut = ByteArray((w / 2) * (h / 2))
        SoundStickerSpecs.argbToPlanarYuv420(pixels, w, h, yOut, uOut, vOut)
        return YuvFrame(yOut, uOut, vOut)
    }

    private fun createAvcEncoder(format: MediaFormat): MediaCodec {
        val primary = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            primary.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            return primary
        } catch (e: Exception) {
            try {
                primary.release()
            } catch (_: Exception) {
            }
            // Fallback to platform software AVC encoder if hardware codec rejects 512x512 flexible YUV
            val codecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            for (info in codecInfos) {
                if (!info.isEncoder) continue
                if (!info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }) continue
                if (info.name.contains("google", ignoreCase = true) || info.name.startsWith("c2.android.", ignoreCase = true)) {
                    val swCodec = MediaCodec.createByCodecName(info.name)
                    try {
                        swCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        return swCodec
                    } catch (_: Exception) {
                        try {
                            swCodec.release()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            throw e
        }
    }

    private fun encodeVideoTrack(
        yuvFrames: List<YuvFrame>,
        frameDurations: List<Int>,
        durationMs: Long,
        outputFile: File,
        onVideoProgress: (Float) -> Unit
    ) {
        val w = SoundStickerSpecs.VIDEO_WIDTH
        val h = SoundStickerSpecs.VIDEO_HEIGHT
        val fps = SoundStickerSpecs.VIDEO_FPS

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, SoundStickerSpecs.VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, SoundStickerSpecs.VIDEO_I_FRAME_INTERVAL_SEC)
        }

        val totalFrames = ((durationMs * fps) / 1000L).toInt().coerceAtLeast(fps)
        val encoder = createAvcEncoder(format)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var trackIndex = -1
        var muxerStarted = false
        var inputDone = false
        var outputDone = false
        var frameIdx = 0
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            encoder.start()
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        if (frameIdx >= totalFrames) {
                            encoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val elapsedMs = (frameIdx * 1000L) / fps
                            val ptsUs = (frameIdx * 1_000_000L) / fps
                            val yuvIdx = SoundStickerSpecs.selectAnimationFrameIndex(elapsedMs, frameDurations)
                            val yuv = yuvFrames[yuvIdx]

                            val image = try {
                                encoder.getInputImage(inputIndex)
                            } catch (_: Exception) {
                                null
                            }

                            val bytesWritten: Int
                            if (image != null) {
                                writeYuvToImage(image, yuv, w, h)
                                bytesWritten = (w * h * 3) / 2
                            } else {
                                val buf = encoder.getInputBuffer(inputIndex)
                                    ?: throw IllegalStateException("MediaCodec returned null input buffer")
                                bytesWritten = writeYuvToBuffer(buf, yuv)
                            }
                            encoder.queueInputBuffer(inputIndex, 0, bytesWritten, ptsUs, 0)
                            frameIdx++
                            if (frameIdx % 6 == 0 || frameIdx == totalFrames) {
                                onVideoProgress(frameIdx.toFloat() / totalFrames.toFloat())
                            }
                        }
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) throw IllegalStateException("Video output format changed twice")
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (!muxerStarted) throw IllegalStateException("Muxer hasn't started yet")
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            try {
                encoder.stop()
            } catch (_: Exception) {
            }
            try {
                encoder.release()
            } catch (_: Exception) {
            }
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }
            try {
                muxer.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeYuvToImage(image: Image, frame: YuvFrame, width: Int, height: Int) {
        val planes = image.planes
        // Plane 0: Y
        copyPlane(frame.y, width, height, planes[0].buffer, planes[0].rowStride, planes[0].pixelStride)
        // Plane 1: U
        copyPlane(frame.u, width / 2, height / 2, planes[1].buffer, planes[1].rowStride, planes[1].pixelStride)
        // Plane 2: V
        copyPlane(frame.v, width / 2, height / 2, planes[2].buffer, planes[2].rowStride, planes[2].pixelStride)
    }

    private fun copyPlane(
        src: ByteArray,
        planeWidth: Int,
        planeHeight: Int,
        dst: ByteBuffer,
        rowStride: Int,
        pixelStride: Int
    ) {
        dst.clear()
        if (pixelStride == 1 && rowStride == planeWidth) {
            dst.put(src, 0, planeWidth * planeHeight)
            return
        }
        for (row in 0 until planeHeight) {
            val srcOffset = row * planeWidth
            if (pixelStride == 1) {
                dst.position(row * rowStride)
                dst.put(src, srcOffset, planeWidth)
            } else {
                for (col in 0 until planeWidth) {
                    val dstIdx = row * rowStride + col * pixelStride
                    dst.put(dstIdx, src[srcOffset + col])
                }
            }
        }
    }

    private fun writeYuvToBuffer(dst: ByteBuffer, frame: YuvFrame): Int {
        dst.clear()
        dst.put(frame.y)
        dst.put(frame.u)
        dst.put(frame.v)
        return frame.y.size + frame.u.size + frame.v.size
    }

    private fun encodeTrimmedAudioTrack(
        audioUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        outputFile: File
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var audioTrackIndex = -1
        var muxerTrackIndex = -1
        var muxerStarted = false

        try {
            extractor.setDataSource(context, audioUri, null)
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || inputFormat == null) {
                throw IllegalStateException("No se encontró ninguna pista de audio válida")
            }

            extractor.selectTrack(audioTrackIndex)
            val srcMime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            var srcSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                SoundStickerSpecs.AUDIO_SAMPLE_RATE
            }
            var srcChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }

            val trimStartUs = trimStartMs * 1000L
            val trimEndUs = trimEndMs * 1000L
            val targetDurationMs = trimEndMs - trimStartMs
            val targetStereoFrames = (targetDurationMs * SoundStickerSpecs.AUDIO_SAMPLE_RATE) / 1000L

            // Pre-roll seek by 250ms so frame-accurate trimming after decoding doesn't miss initial transient
            extractor.seekTo(((trimStartMs - 250L).coerceAtLeast(0L)) * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            decoder = MediaCodec.createDecoderByType(srcMime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            val aacFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                SoundStickerSpecs.AUDIO_SAMPLE_RATE,
                SoundStickerSpecs.AUDIO_CHANNEL_COUNT
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, SoundStickerSpecs.AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            var extractorDone = false
            var decoderDone = false
            var encoderInputDone = false
            var encoderDone = false
            var encodedStereoFramesWritten = 0L

            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            val pendingStereoShorts = ArrayDeque<Short>()

            while (!encoderDone) {
                // 1. Feed compressed packets into Decoder
                if (!extractorDone) {
                    val decInIdx = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (decInIdx >= 0) {
                        val buf = decoder.getInputBuffer(decInIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        val sampleTime = extractor.sampleTime
                        if (sampleSize < 0 || (sampleTime >= 0 && sampleTime > trimEndUs + 250_000L)) {
                            decoder.queueInputBuffer(decInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            decoder.queueInputBuffer(decInIdx, 0, sampleSize, sampleTime.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. Drain PCM shorts from Decoder -> Resample to 44.1kHz stereo -> Queue into pendingStereoShorts
                if (!decoderDone) {
                    val decOutIdx = decoder.dequeueOutputBuffer(decInfo, CODEC_TIMEOUT_US)
                    when {
                        decOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFmt = decoder.outputFormat
                            if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                srcSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                srcChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                        decOutIdx >= 0 -> {
                            val decBuf = decoder.getOutputBuffer(decOutIdx)
                            if (decBuf != null && decInfo.size > 0 && (decInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                decBuf.position(decInfo.offset)
                                decBuf.limit(decInfo.offset + decInfo.size)
                                val shortCount = decInfo.size / 2
                                if (shortCount > 0 && srcChannels > 0 && srcSampleRate > 0) {
                                    val srcFrames = shortCount / srcChannels
                                    val bufDurationUs = (srcFrames * 1_000_000L) / srcSampleRate
                                    val bufStartUs = decInfo.presentationTimeUs
                                    val bufEndUs = bufStartUs + bufDurationUs

                                    if (bufEndUs > trimStartUs && bufStartUs < trimEndUs) {
                                        val allShorts = ShortArray(shortCount)
                                        decBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(allShorts)

                                        val skipFrames = if (bufStartUs < trimStartUs) {
                                            (((trimStartUs - bufStartUs) * srcSampleRate) / 1_000_000L).toInt().coerceIn(0, srcFrames)
                                        } else {
                                            0
                                        }
                                        val keepEndFrames = if (bufEndUs > trimEndUs) {
                                            (((trimEndUs - bufStartUs) * srcSampleRate) / 1_000_000L).toInt().coerceIn(skipFrames, srcFrames)
                                        } else {
                                            srcFrames
                                        }

                                        val startShort = skipFrames * srcChannels
                                        val endShort = keepEndFrames * srcChannels
                                        if (endShort > startShort) {
                                            val slice = allShorts.copyOfRange(startShort, endShort)
                                            val resampled = SoundStickerSpecs.resamplePcmToStereo(
                                                input = slice,
                                                inputCount = slice.size,
                                                srcSampleRate = srcSampleRate,
                                                srcChannels = srcChannels
                                            )
                                            for (s in resampled) {
                                                pendingStereoShorts.addLast(s)
                                            }
                                        }
                                    }
                                }
                            }
                            decoder.releaseOutputBuffer(decOutIdx, false)
                            if ((decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                decoderDone = true
                            }
                        }
                    }
                }

                // 3. Feed resampled PCM shorts into AAC Encoder
                if (!encoderInputDone) {
                    val remainingFrames = (targetStereoFrames - encodedStereoFramesWritten).coerceAtLeast(0L)
                    val shouldFeed = pendingStereoShorts.size >= 2 || decoderDone || remainingFrames == 0L
                    if (shouldFeed) {
                        val encInIdx = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (encInIdx >= 0) {
                            val encBuf = encoder.getInputBuffer(encInIdx)!!
                            encBuf.clear()

                            if ((decoderDone && pendingStereoShorts.size < 2) || remainingFrames == 0L) {
                                encoder.queueInputBuffer(encInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                encoderInputDone = true
                            } else {
                                val maxShortsCapacity = (encBuf.capacity() / 2) and 1.inv() // even short count (stereo)
                                val maxAllowedShorts = (remainingFrames * 2L).coerceAtMost(maxShortsCapacity.toLong()).toInt()
                                val toWriteShorts = minOf(pendingStereoShorts.size and 1.inv(), maxAllowedShorts)
                                encBuf.order(ByteOrder.LITTLE_ENDIAN)
                                val sb = encBuf.asShortBuffer()
                                for (k in 0 until toWriteShorts) {
                                    sb.put(pendingStereoShorts.removeFirst())
                                }
                                val ptsUs = (encodedStereoFramesWritten * 1_000_000L) / SoundStickerSpecs.AUDIO_SAMPLE_RATE
                                encodedStereoFramesWritten += (toWriteShorts / 2)
                                encoder.queueInputBuffer(encInIdx, 0, toWriteShorts * 2, ptsUs, 0)
                            }
                        }
                    }
                }

                // 4. Drain AAC frames from Encoder into Muxer
                val encOutIdx = encoder.dequeueOutputBuffer(encInfo, CODEC_TIMEOUT_US)
                when {
                    encOutIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) throw IllegalStateException("Audio output format changed twice")
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    encOutIdx >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(encOutIdx)
                        if (outBuf != null && encInfo.size > 0 && (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (muxerStarted) {
                                outBuf.position(encInfo.offset)
                                outBuf.limit(encInfo.offset + encInfo.size)
                                muxer.writeSampleData(muxerTrackIndex, outBuf, encInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(encOutIdx, false)
                        if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    }
                }
            }
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
            decoder?.let {
                try {
                    it.stop()
                } catch (_: Exception) {
                }
                try {
                    it.release()
                } catch (_: Exception) {
                }
            }
            encoder?.let {
                try {
                    it.stop()
                } catch (_: Exception) {
                }
                try {
                    it.release()
                } catch (_: Exception) {
                }
            }
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }
            try {
                muxer.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun remuxTracks(videoFile: File, audioFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            var srcVideoTrack = -1
            var videoFmt: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                    srcVideoTrack = i
                    videoFmt = fmt
                    break
                }
            }

            audioExtractor.setDataSource(audioFile.absolutePath)
            var srcAudioTrack = -1
            var audioFmt: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    srcAudioTrack = i
                    audioFmt = fmt
                    break
                }
            }

            if (srcVideoTrack < 0 || videoFmt == null || srcAudioTrack < 0 || audioFmt == null) {
                throw IllegalStateException("Pistas temporales incompletas para ensamblar MP4")
            }

            videoExtractor.selectTrack(srcVideoTrack)
            audioExtractor.selectTrack(srcAudioTrack)

            val outVideoTrack = muxer.addTrack(videoFmt)
            val outAudioTrack = muxer.addTrack(audioFmt)
            muxer.start()
            muxerStarted = true

            val sampleBuf = ByteBuffer.allocate(512 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            copySamples(videoExtractor, muxer, outVideoTrack, sampleBuf, bufferInfo)
            copySamples(audioExtractor, muxer, outAudioTrack, sampleBuf, bufferInfo)
        } finally {
            try {
                videoExtractor.release()
            } catch (_: Exception) {
            }
            try {
                audioExtractor.release()
            } catch (_: Exception) {
            }
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }
            try {
                muxer.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun copySamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackIndex: Int,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo
    ) {
        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
            info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
            buffer.position(0)
            buffer.limit(sampleSize)
            muxer.writeSampleData(trackIndex, buffer, info)
            if (!extractor.advance()) break
        }
    }

    private fun saveToGalleryOrShareUri(mp4File: File): Pair<Uri, Boolean> {
        val fileName = mp4File.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/7TVStickers")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val insertedUri = resolver.insert(collection, values)
                if (insertedUri != null) {
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        mp4File.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(insertedUri, values, null, null)
                    return insertedUri to true
                }
            } catch (_: Exception) {
                // Fall back to FileProvider URI below
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val targetDir = File(moviesDir, "7TVStickers").apply { mkdirs() }
                val destFile = File(targetDir, fileName)
                mp4File.copyTo(destFile, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("video/mp4"), null)
                return Uri.fromFile(destFile) to true
            } catch (_: Exception) {
                // Missing WRITE_EXTERNAL_STORAGE on Android 8-9 -> fall back to FileProvider
            }
        }

        val shareUri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, mp4File)
        return shareUri to false
    }
}

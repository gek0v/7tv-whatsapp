package com.seventv.stickers.core.media.sound

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VideoFrameExtractorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun computeSamplingPointsCapsDurationAt5000MsAndBoundsFrameCount() {
        val points = AndroidVideoFrameExtractor.computeSamplingPoints(
            startMs = 0L,
            clipDurationMs = 12_000L, // > 5000ms must clamp to 5000ms
            targetFps = 15,
            maxFrames = VideoFrameExtractor.MAX_EXTRACTED_FRAMES
        )

        assertEquals(75, points.size)
        assertEquals(SoundStickerSpecs.MAX_ANIMATED_CLIP_DURATION_MS.toInt(), points.sumOf { it.durationMs })
        assertEquals(SoundStickerSpecs.MAX_ANIMATED_CLIP_DURATION_MS.toInt(), points.last().cumulativeTimestampMs)

        // Requesting 60 FPS is clamped to MAX_TARGET_FPS (24) and bounded by maxFrames (90)
        val highFpsPoints = AndroidVideoFrameExtractor.computeSamplingPoints(
            startMs = 500L,
            clipDurationMs = 5_000L,
            targetFps = 60,
            maxFrames = VideoFrameExtractor.MAX_EXTRACTED_FRAMES
        )
        assertEquals(VideoFrameExtractor.MAX_EXTRACTED_FRAMES, highFpsPoints.size)
        assertEquals(500L, highFpsPoints.first().timestampMs)
        assertEquals(5_000, highFpsPoints.sumOf { it.durationMs })
    }

    @Test
    fun computeScaledDimensionsDownscales1080pAnd4kWithoutUpscalingSmallVideos() {
        // 1080p landscape -> 512x288
        assertEquals(512 to 288, AndroidVideoFrameExtractor.computeScaledDimensions(1920, 1080, 512))
        // 4K landscape -> 512x288
        assertEquals(512 to 288, AndroidVideoFrameExtractor.computeScaledDimensions(3840, 2160, 512))
        // Vertical 1080p -> 288x512
        assertEquals(288 to 512, AndroidVideoFrameExtractor.computeScaledDimensions(1080, 1920, 512))
        // Small 320x240 stays 320x240
        assertEquals(320 to 240, AndroidVideoFrameExtractor.computeScaledDimensions(320, 240, 512))
    }

    @Test
    fun extractFramesCapsClipToFiveSecondsScalesOversizedFramesAndDetectsAudio() = runTest(testDispatcher) {
        val fakeRetriever = FakeVideoRetrieverClient(
            durationMs = 12_000L,
            frameWidth = 3840,
            frameHeight = 2160,
            simulateNativePreScaling = true
        )
        val fakeExtractor = FakeVideoExtractorClient(
            tracks = listOf("video/avc", "audio/mp4a-latm")
        )
        val extractor = AndroidVideoFrameExtractor(
            context = context,
            dispatcher = testDispatcher,
            retrieverFactory = { fakeRetriever },
            extractorFactory = { fakeExtractor }
        )

        val res = extractor.extractFrames(Uri.parse("content://media/video/clip.mp4"))
        assertTrue("Expected Success but got $res", res is StickerResult.Success)
        val clip = (res as StickerResult.Success).data

        assertEquals(SoundStickerSpecs.MAX_ANIMATED_CLIP_DURATION_MS, clip.clipDurationMs)
        assertEquals(12_000L, clip.totalVideoDurationMs)
        assertTrue(clip.hasAudioTrack)
        assertEquals(75, clip.frames.size)
        assertTrue(clip.frames.all { it.bitmap.width <= 512 && it.bitmap.height <= 512 })
        assertEquals(512, clip.frames.first().bitmap.width)
        assertEquals(288, clip.frames.first().bitmap.height)
        assertEquals(5_000, clip.frames.sumOf { it.durationMs })
        assertTrue(fakeRetriever.closed)
        assertTrue(fakeExtractor.closed)
    }

    @Test
    fun extractFramesDownscalesUnscaled1080pFramesTo512PxMaxDimension() = runTest(testDispatcher) {
        val fakeRetriever = FakeVideoRetrieverClient(
            durationMs = 1_000L,
            frameWidth = 1920,
            frameHeight = 1080,
            simulateNativePreScaling = false
        )
        val fakeExtractor = FakeVideoExtractorClient(tracks = listOf("video/avc"))
        val extractor = AndroidVideoFrameExtractor(
            context = context,
            dispatcher = testDispatcher,
            retrieverFactory = { fakeRetriever },
            extractorFactory = { fakeExtractor }
        )

        val res = extractor.extractFrames(Uri.parse("content://media/video/unscaled_1080p.mp4"))
        assertTrue("Expected Success but got $res", res is StickerResult.Success)
        val clip = (res as StickerResult.Success).data

        assertEquals(15, clip.frames.size)
        assertEquals(512, clip.frames.first().bitmap.width)
        assertEquals(288, clip.frames.first().bitmap.height)
        assertTrue(fakeRetriever.createdBitmaps.all { it.isRecycled })
    }

    @Test
    fun extractFramesReportsNoAudioForSilentWebmVideo() = runTest(testDispatcher) {
        val fakeRetriever = FakeVideoRetrieverClient(
            durationMs = 2_400L,
            frameWidth = 400,
            frameHeight = 400
        )
        val fakeExtractor = FakeVideoExtractorClient(
            tracks = listOf("video/x-vnd.on2.vp9")
        )
        val extractor = AndroidVideoFrameExtractor(
            context = context,
            dispatcher = testDispatcher,
            retrieverFactory = { fakeRetriever },
            extractorFactory = { fakeExtractor }
        )

        val res = extractor.extractFrames(Uri.parse("content://media/video/silent.webm"))
        assertTrue(res is StickerResult.Success)
        val clip = (res as StickerResult.Success).data

        assertFalse(clip.hasAudioTrack)
        assertEquals(2_400L, clip.clipDurationMs)
        assertEquals(400, clip.frames.first().bitmap.width)
        assertEquals(400, clip.frames.first().bitmap.height)
    }

    @Test
    fun extractFramesFailsGracefullyOnCorruptedVideoDuration() = runTest(testDispatcher) {
        val fakeRetriever = FakeVideoRetrieverClient(durationMs = 0L, frameWidth = 512, frameHeight = 512)
        val fakeExtractor = FakeVideoExtractorClient(tracks = emptyList())
        val extractor = AndroidVideoFrameExtractor(
            context = context,
            dispatcher = testDispatcher,
            retrieverFactory = { fakeRetriever },
            extractorFactory = { fakeExtractor }
        )

        val res = extractor.extractFrames(Uri.parse("content://media/video/broken.mp4"))
        assertTrue(res is StickerResult.Failure)
        assertEquals(StickerError.CorruptedSourceData, (res as StickerResult.Failure).error)
        assertTrue(fakeRetriever.closed)
    }

    private class FakeVideoRetrieverClient(
        private val durationMs: Long,
        private val frameWidth: Int,
        private val frameHeight: Int,
        private val simulateNativePreScaling: Boolean = true
    ) : VideoRetrieverClient {
        var closed = false
        val createdBitmaps = mutableListOf<Bitmap>()

        override fun setDataSource(context: Context, uri: Uri) = Unit

        override fun extractMetadata(keyCode: Int): String? = when (keyCode) {
            MediaMetadataRetriever.METADATA_KEY_DURATION -> durationMs.toString()
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH -> frameWidth.toString()
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT -> frameHeight.toString()
            else -> null
        }

        override fun getFrame(timeUs: Long, maxDimensionPx: Int): Bitmap? {
            val (w, h) = if (simulateNativePreScaling) {
                AndroidVideoFrameExtractor.computeScaledDimensions(frameWidth, frameHeight, maxDimensionPx)
            } else {
                frameWidth to frameHeight
            }
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                createdBitmaps.add(it)
            }
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeVideoExtractorClient(
        private val tracks: List<String>
    ) : VideoExtractorClient {
        var closed = false

        override fun setDataSource(context: Context, uri: Uri) = Unit

        override val trackCount: Int
            get() = tracks.size

        override fun getTrackMime(index: Int): String? = tracks.getOrNull(index)

        override fun close() {
            closed = true
        }
    }
}

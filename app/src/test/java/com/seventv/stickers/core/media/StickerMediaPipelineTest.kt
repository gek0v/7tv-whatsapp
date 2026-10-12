package com.seventv.stickers.core.media

import android.graphics.Bitmap
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.animated.AnimatedWebPEncoder
import com.seventv.stickers.core.media.animated.FrameExtractor
import com.seventv.stickers.core.media.model.AnimationFrame
import com.seventv.stickers.core.media.model.ProcessedSticker
import com.seventv.stickers.core.media.model.RawMediaInput
import com.seventv.stickers.core.media.model.StickerSpecs
import com.seventv.stickers.core.media.normalizer.CanvasStickerNormalizer
import com.seventv.stickers.core.media.optimizer.StickerSizeOptimizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StickerMediaPipelineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        outputDir = tempFolder.newFolder("sticker_out")
    }

    private fun createSamplePngBytes(width: Int = 100, height: Int = 100): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bitmap.recycle()
        return baos.toByteArray()
    }

    class FakeAnimatedWebPEncoder(
        private val outputByteSize: Int = 100 * 1024,
        private val shouldSucceed: Boolean = true,
        private val sizeCalculator: ((bitmaps: Array<Bitmap>, quality: Float) -> Int)? = null
    ) : AnimatedWebPEncoder {
        var lastEncodedBitmaps: Array<Bitmap>? = null
        var lastEncodedTimestamps: IntArray? = null
        var lastEncodedQuality: Float? = null
        var encodeCallCount = 0

        override fun encode(
            bitmaps: Array<Bitmap>,
            timestampsMs: IntArray,
            quality: Float,
            outputPath: String
        ): Boolean {
            encodeCallCount++
            lastEncodedBitmaps = bitmaps
            lastEncodedTimestamps = timestampsMs
            lastEncodedQuality = quality

            if (!shouldSucceed) return false

            val outFile = File(outputPath)
            outFile.parentFile?.mkdirs()
            val effectiveSize = sizeCalculator?.invoke(bitmaps, quality) ?: outputByteSize
            val dummyBytes = ByteArray(effectiveSize)
            outFile.writeBytes(dummyBytes)
            return true
        }
    }

    class FakeFrameExtractor(
        private val framesToReturn: List<AnimationFrame>
    ) : FrameExtractor {
        override fun extractFrames(bytes: ByteArray): StickerResult<List<AnimationFrame>> {
            if (bytes.isEmpty() || bytes.contentEquals(byteArrayOf(0, 1, 2))) {
                return StickerResult.Failure(StickerError.CorruptedSourceData)
            }
            return StickerResult.Success(framesToReturn)
        }
    }

    @Test
    fun defaultConstructorInitializesSuccessfully() {
        val pipeline = StickerMediaPipeline()
        assertNotNull(pipeline)
    }

    @Test
    fun processStickerStaticSucceedsEndToEnd() = runTest(testDispatcher) {
        val normalizer = CanvasStickerNormalizer()
        val sizeOptimizer = StickerSizeOptimizer(normalizer)
        val pipeline = StickerMediaPipeline(
            normalizer = normalizer,
            sizeOptimizer = sizeOptimizer,
            dispatcher = testDispatcher
        )

        val pngBytes = createSamplePngBytes(256, 128)
        val input = RawMediaInput(bytes = pngBytes, isAnimated = false)

        val result = pipeline.processSticker(input, outputDir)

        assertTrue(result is StickerResult.Success<*>)
        val processed = (result as StickerResult.Success<ProcessedSticker>).data
        assertEquals(512, processed.width)
        assertEquals(512, processed.height)
        assertFalse(processed.isAnimated)
        assertEquals(1, processed.frameCount)
        assertEquals(0, processed.durationMs)
        assertTrue(processed.byteSize <= StickerSpecs.STATIC_SAFE_CEILING_BYTES)
        assertTrue(processed.file.exists())
        assertEquals(processed.byteSize.toLong(), processed.file.length())
    }

    @Test
    fun processStickerStaticWithCorruptedDataFails() = runTest(testDispatcher) {
        val pipeline = StickerMediaPipeline(dispatcher = testDispatcher)

        val emptyInput = RawMediaInput(bytes = ByteArray(0), isAnimated = false)
        val emptyResult = pipeline.processSticker(emptyInput, outputDir)
        assertTrue(emptyResult is StickerResult.Failure)
        assertEquals(StickerError.CorruptedSourceData, (emptyResult as StickerResult.Failure).error)

        val invalidInput = RawMediaInput(bytes = byteArrayOf(0, 1, 2), isAnimated = false)
        val invalidResult = pipeline.processSticker(invalidInput, outputDir)
        assertTrue(invalidResult is StickerResult.Failure)
        assertEquals(StickerError.CorruptedSourceData, (invalidResult as StickerResult.Failure).error)
    }

    @Test
    fun processStickerAnimatedSucceedsEndToEnd() = runTest(testDispatcher) {
        val fakeEncoder = FakeAnimatedWebPEncoder(outputByteSize = 200 * 1024)
        val frames = (1..5).map { i ->
            AnimationFrame(
                bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888),
                durationMs = 100,
                timestampMs = i * 100
            )
        }
        val fakeExtractor = FakeFrameExtractor(frames)
        val pipeline = StickerMediaPipeline(
            frameExtractor = fakeExtractor,
            animatedWebPEncoder = fakeEncoder,
            dispatcher = testDispatcher
        )

        val input = RawMediaInput(bytes = byteArrayOf(10, 20, 30), isAnimated = true)
        val result = pipeline.processSticker(input, outputDir)

        assertTrue(result is StickerResult.Success<*>)
        val processed = (result as StickerResult.Success<ProcessedSticker>).data
        assertEquals(512, processed.width)
        assertEquals(512, processed.height)
        assertTrue(processed.isAnimated)
        assertEquals(5, processed.frameCount)
        assertEquals(500, processed.durationMs)
        assertTrue(processed.byteSize <= StickerSpecs.ANIMATED_SAFE_CEILING_BYTES)
        assertTrue(processed.file.exists())
        assertEquals(200 * 1024L, processed.byteSize)
    }

    @Test
    fun processStickerAnimatedExceedsSizeLimitReturnsExceedsSizeWithFallback() = runTest(testDispatcher) {
        // Generates 600 KB files which exceeds 480 KB ceiling
        val fakeEncoder = FakeAnimatedWebPEncoder(outputByteSize = 600 * 1024)
        val frames = (1..10).map { i ->
            AnimationFrame(
                bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
                durationMs = 100,
                timestampMs = i * 100
            )
        }
        val fakeExtractor = FakeFrameExtractor(frames)
        val pipeline = StickerMediaPipeline(
            frameExtractor = fakeExtractor,
            animatedWebPEncoder = fakeEncoder,
            dispatcher = testDispatcher
        )

        val input = RawMediaInput(bytes = byteArrayOf(10, 20, 30), isAnimated = true)
        val result = pipeline.processSticker(input, outputDir)

        assertTrue(result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue(error is StickerError.ExceedsSizeLimit)
        val sizeError = error as StickerError.ExceedsSizeLimit
        assertTrue(sizeError.canFallbackToStatic)
        assertEquals(StickerSpecs.ANIMATED_MAX_BYTES, sizeError.maxBytes)
        assertEquals(600 * 1024L, sizeError.actualBytes)
    }

    @Test
    fun processStickerAnimatedCorruptedInputFails() = runTest(testDispatcher) {
        val pipeline = StickerMediaPipeline(dispatcher = testDispatcher)

        val emptyInput = RawMediaInput(bytes = ByteArray(0), isAnimated = true)
        val result = pipeline.processSticker(emptyInput, outputDir)
        assertTrue(result is StickerResult.Failure)
        assertEquals(StickerError.CorruptedSourceData, (result as StickerResult.Failure).error)
    }

    @Test
    fun processAsStaticFallbackProducesStaticStickerFromAnimatedInput() = runTest(testDispatcher) {
        val frames = (1..3).map { i ->
            AnimationFrame(
                bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888),
                durationMs = 100,
                timestampMs = i * 100
            )
        }
        val fakeExtractor = FakeFrameExtractor(frames)
        val pipeline = StickerMediaPipeline(
            frameExtractor = fakeExtractor,
            dispatcher = testDispatcher
        )

        val input = RawMediaInput(bytes = byteArrayOf(5, 6, 7), isAnimated = true)
        val result = pipeline.processAsStaticFallback(input, outputDir)

        assertTrue(result is StickerResult.Success<*>)
        val processed = (result as StickerResult.Success<ProcessedSticker>).data
        assertEquals(512, processed.width)
        assertEquals(512, processed.height)
        assertFalse(processed.isAnimated)
        assertEquals(1, processed.frameCount)
        assertEquals(0, processed.durationMs)
        assertTrue(processed.byteSize <= StickerSpecs.STATIC_SAFE_CEILING_BYTES)
        assertTrue(processed.file.exists())
    }

    @Test
    fun processTrayIconProducesCompliantWebPUnder50KB() = runTest(testDispatcher) {
        val pipeline = StickerMediaPipeline(dispatcher = testDispatcher)
        val sourceBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val trayFile = File(outputDir, "tray_icon.webp")

        val result = pipeline.processTrayIcon(sourceBitmap, trayFile)

        assertTrue(result is StickerResult.Success<*>)
        val file = (result as StickerResult.Success<File>).data
        assertEquals(trayFile.absolutePath, file.absolutePath)
        assertTrue(file.exists())
        assertTrue(file.length() <= StickerSpecs.TRAY_ICON_MAX_BYTES)
        assertTrue(file.length() > 0)
        sourceBitmap.recycle()
    }

    @Test
    fun processTrayIconWithRecycledBitmapFails() = runTest(testDispatcher) {
        val pipeline = StickerMediaPipeline(dispatcher = testDispatcher)
        val sourceBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { recycle() }
        val trayFile = File(outputDir, "tray_icon_recycled.webp")

        val result = pipeline.processTrayIcon(sourceBitmap, trayFile)

        assertTrue(result is StickerResult.Failure)
        assertEquals(StickerError.CorruptedSourceData, (result as StickerResult.Failure).error)
    }

    @Test
    fun processStickerAnimatedEnforcesDurationTruncationAndFramerateDecimation() = runTest(testDispatcher) {
        val fakeEncoder = FakeAnimatedWebPEncoder(outputByteSize = 100 * 1024)
        // 60 frames of 100ms = 6000ms duration (exceeds 3000ms)
        val frames = (1..60).map { i ->
            AnimationFrame(
                bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
                durationMs = 100,
                timestampMs = i * 100
            )
        }
        val fakeExtractor = FakeFrameExtractor(frames)
        val pipeline = StickerMediaPipeline(
            frameExtractor = fakeExtractor,
            animatedWebPEncoder = fakeEncoder,
            dispatcher = testDispatcher
        )

        val input = RawMediaInput(bytes = byteArrayOf(1, 2, 3, 4), isAnimated = true)
        val result = pipeline.processSticker(input, outputDir)

        assertTrue(result is StickerResult.Success<*>)
        val processed = (result as StickerResult.Success<ProcessedSticker>).data
        assertTrue(processed.durationMs <= StickerSpecs.ANIMATED_MAX_DURATION_MS)
        assertEquals(3000, processed.durationMs)
        assertEquals(30, processed.frameCount)
    }

    @Test
    fun processStickerAnimatedProgressiveDecimationSucceedsAtTier2() = runTest(testDispatcher) {
        // Source is 30 fps (30 frames, 1000ms total). Exceeds limit if > 15 frames,
        // but succeeds when decimated to 15 fps (15 frames).
        val fakeEncoder = FakeAnimatedWebPEncoder(
            sizeCalculator = { bitmaps, _ ->
                if (bitmaps.size > 15) 600 * 1024 else 350 * 1024
            }
        )
        val frames = (1..30).map { i ->
            AnimationFrame(
                bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
                durationMs = 33,
                timestampMs = i * 33
            )
        }
        val fakeExtractor = FakeFrameExtractor(frames)
        val pipeline = StickerMediaPipeline(
            frameExtractor = fakeExtractor,
            animatedWebPEncoder = fakeEncoder,
            dispatcher = testDispatcher
        )

        val input = RawMediaInput(bytes = byteArrayOf(1, 2, 3), isAnimated = true)
        val result = pipeline.processSticker(input, outputDir)

        assertTrue("Expected Success at Tier 2 decimation", result is StickerResult.Success<*>)
        val processed = (result as StickerResult.Success<ProcessedSticker>).data
        assertTrue(processed.isAnimated)
        assertEquals(15, processed.frameCount)
        assertTrue(processed.byteSize <= StickerSpecs.ANIMATED_SAFE_CEILING_BYTES)
    }

    @Test
    fun processStickerAnimatedProgressiveDecimationSucceedsAtTier3() = runTest(testDispatcher) {
        // Source is 30 fps (30 frames, 1000ms total). Exceeds limit at Tier 1 (30 frames)
        // and Tier 2 (15 frames), but succeeds when decimated to <= 10 fps at Tier 3.
        val fakeEncoder = FakeAnimatedWebPEncoder(
            sizeCalculator = { bitmaps, _ ->
                if (bitmaps.size > 10) 600 * 1024 else 280 * 1024
            }
        )
        val frames = (1..30).map { i ->
            AnimationFrame(
                bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
                durationMs = 33,
                timestampMs = i * 33
            )
        }
        val fakeExtractor = FakeFrameExtractor(frames)
        val pipeline = StickerMediaPipeline(
            frameExtractor = fakeExtractor,
            animatedWebPEncoder = fakeEncoder,
            dispatcher = testDispatcher
        )

        val input = RawMediaInput(bytes = byteArrayOf(1, 2, 3), isAnimated = true)
        val result = pipeline.processSticker(input, outputDir)

        assertTrue("Expected Success at Tier 3 decimation", result is StickerResult.Success<*>)
        val processed = (result as StickerResult.Success<ProcessedSticker>).data
        assertTrue(processed.isAnimated)
        assertTrue(processed.frameCount in 2..10)
        assertTrue(processed.byteSize <= StickerSpecs.ANIMATED_SAFE_CEILING_BYTES)
    }

    @Test
    fun processStickerAnimatedFailSafeProducesTwoFrameStickerWhenAllTiersExceed() = runTest(testDispatcher) {
        // Exceeds limit for any normal frame count (> 2), but succeeds on the 2-frame fail-safe loop
        val fakeEncoder = FakeAnimatedWebPEncoder(
            sizeCalculator = { bitmaps, _ ->
                if (bitmaps.size > 2) 600 * 1024 else 25 * 1024
            }
        )
        val frames = (1..30).map { i ->
            AnimationFrame(
                bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
                durationMs = 100,
                timestampMs = i * 100
            )
        }
        val fakeExtractor = FakeFrameExtractor(frames)
        val pipeline = StickerMediaPipeline(
            frameExtractor = fakeExtractor,
            animatedWebPEncoder = fakeEncoder,
            dispatcher = testDispatcher
        )

        val input = RawMediaInput(bytes = byteArrayOf(1, 2, 3), isAnimated = true)
        val result = pipeline.processSticker(input, outputDir)

        assertTrue("Expected Success via Tier 5 fail-safe 2-frame animated loop", result is StickerResult.Success<*>)
        val processed = (result as StickerResult.Success<ProcessedSticker>).data
        assertTrue(processed.isAnimated)
        assertEquals(2, processed.frameCount)
        assertEquals(1000, processed.durationMs)
        assertEquals(25 * 1024L, processed.byteSize)
        assertTrue(processed.byteSize <= StickerSpecs.ANIMATED_SAFE_CEILING_BYTES)
    }
}

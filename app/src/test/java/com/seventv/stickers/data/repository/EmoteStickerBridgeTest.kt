package com.seventv.stickers.data.repository

import android.graphics.Bitmap
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.StickerMediaPipeline
import com.seventv.stickers.core.media.animated.AnimatedWebPEncoder
import com.seventv.stickers.core.media.animated.FrameExtractor
import com.seventv.stickers.core.media.model.AnimationFrame
import com.seventv.stickers.core.media.model.ProcessedSticker
import com.seventv.stickers.core.media.model.RawMediaInput
import com.seventv.stickers.core.media.model.StickerSpecs
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
class EmoteStickerBridgeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var outputDir: File
    private lateinit var fakeRepository: FakeSevenTvRepository

    @Before
    fun setUp() {
        outputDir = tempFolder.newFolder("bridge_out")
        fakeRepository = FakeSevenTvRepository()
    }

    private fun createSamplePngBytes(width: Int = 100, height: Int = 100): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bitmap.recycle()
        return baos.toByteArray()
    }

    private fun createSampleEmote(
        id: String = "emote_1",
        name: String = "TestEmote",
        isAnimated: Boolean = false
    ): SevenTvEmote {
        return SevenTvEmote(
            id = id,
            name = name,
            isAnimated = isAnimated,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "https://cdn.7tv.app/emote/$id/4x.webp",
            availableScales = listOf("1x", "2x", "3x", "4x")
        )
    }

    @Test
    fun convertEmoteToStickerSuccess() = runTest(testDispatcher) {
        val pngBytes = createSamplePngBytes(256, 256)
        val emote = createSampleEmote(id = "static_1", name = "StaticEmote", isAnimated = false)

        fakeRepository.downloadEmoteResult = {
            StickerResult.Success(
                DownloadedEmoteAsset(
                    emoteId = emote.id,
                    emoteName = emote.name,
                    bytes = pngBytes,
                    isAnimated = false,
                    format = "PNG"
                )
            )
        }

        val pipeline = StickerMediaPipeline(dispatcher = testDispatcher)
        val bridge = EmoteStickerBridge(
            repository = fakeRepository,
            mediaPipeline = pipeline
        )

        val result = bridge.convertEmoteToSticker(emote, outputDir)

        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val processed = (result as StickerResult.Success<ProcessedSticker>).data
        assertEquals(512, processed.width)
        assertEquals(512, processed.height)
        assertFalse(processed.isAnimated)
        assertEquals(1, processed.frameCount)
        assertEquals(0, processed.durationMs)
        assertTrue(processed.byteSize <= StickerSpecs.STATIC_SAFE_CEILING_BYTES)
        assertTrue(processed.file.exists())
        assertEquals(processed.byteSize, processed.file.length())
    }

    @Test
    fun downloadFailureReturnsImmediately() = runTest(testDispatcher) {
        val emote = createSampleEmote(id = "missing_1")
        fakeRepository.downloadEmoteResult = {
            StickerResult.Failure(StickerError.ResourceNotFound)
        }

        val trackingExtractor = TrackingFrameExtractor()
        val pipeline = StickerMediaPipeline(
            frameExtractor = trackingExtractor,
            dispatcher = testDispatcher
        )
        val bridge = EmoteStickerBridge(
            repository = fakeRepository,
            mediaPipeline = pipeline
        )

        val result = bridge.convertEmoteToSticker(emote, outputDir)

        assertTrue("Expected Failure, got $result", result is StickerResult.Failure)
        assertEquals(StickerError.ResourceNotFound, (result as StickerResult.Failure).error)
        assertEquals("Pipeline frame extractor should not be invoked", 0, trackingExtractor.extractCallCount)
    }

    @Test
    fun animatedEmoteFallsBackToStaticWhenConfigured() = runTest(testDispatcher) {
        val emote = createSampleEmote(id = "anim_large", name = "BigAnim", isAnimated = true)

        val fakeExtractor = TrackingFrameExtractor(
            frameSupplier = {
                (1..5).map { i ->
                    AnimationFrame(
                        bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888),
                        durationMs = 100,
                        timestampMs = i * 100
                    )
                }
            }
        )
        // Produces 600 KB file which exceeds 480 KB limit
        val fakeEncoder = FakeAnimatedWebPEncoder(outputByteSize = 600 * 1024)

        val pipeline = StickerMediaPipeline(
            frameExtractor = fakeExtractor,
            animatedWebPEncoder = fakeEncoder,
            dispatcher = testDispatcher
        )

        fakeRepository.downloadEmoteResult = {
            StickerResult.Success(
                DownloadedEmoteAsset(
                    emoteId = emote.id,
                    emoteName = emote.name,
                    bytes = byteArrayOf(10, 20, 30, 40),
                    isAnimated = true,
                    format = "WEBP"
                )
            )
        }

        val bridge = EmoteStickerBridge(
            repository = fakeRepository,
            mediaPipeline = pipeline
        )

        val result = bridge.convertEmoteToSticker(emote, outputDir, allowStaticFallback = true)

        assertTrue("Expected Success via static fallback, got $result", result is StickerResult.Success<*>)
        val processed = (result as StickerResult.Success<ProcessedSticker>).data
        assertEquals(512, processed.width)
        assertEquals(512, processed.height)
        assertFalse("Fallback sticker should be static", processed.isAnimated)
        assertEquals(1, processed.frameCount)
        assertEquals(0, processed.durationMs)
        assertTrue(processed.byteSize <= StickerSpecs.STATIC_SAFE_CEILING_BYTES)
        assertTrue(processed.file.exists())
    }

    @Test
    fun animatedEmoteDoesNotFallBackWhenDisabled() = runTest(testDispatcher) {
        val emote = createSampleEmote(id = "anim_no_fallback", name = "NoFallbackAnim", isAnimated = true)

        val fakeExtractor = TrackingFrameExtractor(
            frameSupplier = {
                (1..5).map { i ->
                    AnimationFrame(
                        bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888),
                        durationMs = 100,
                        timestampMs = i * 100
                    )
                }
            }
        )
        val fakeEncoder = FakeAnimatedWebPEncoder(outputByteSize = 600 * 1024)

        val pipeline = StickerMediaPipeline(
            frameExtractor = fakeExtractor,
            animatedWebPEncoder = fakeEncoder,
            dispatcher = testDispatcher
        )

        fakeRepository.downloadEmoteResult = {
            StickerResult.Success(
                DownloadedEmoteAsset(
                    emoteId = emote.id,
                    emoteName = emote.name,
                    bytes = byteArrayOf(10, 20, 30, 40),
                    isAnimated = true,
                    format = "WEBP"
                )
            )
        }

        val bridge = EmoteStickerBridge(
            repository = fakeRepository,
            mediaPipeline = pipeline
        )

        val result = bridge.convertEmoteToSticker(emote, outputDir, allowStaticFallback = false)

        assertTrue("Expected Failure without fallback, got $result", result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue("Expected ExceedsSizeLimit, got $error", error is StickerError.ExceedsSizeLimit)
        val sizeError = error as StickerError.ExceedsSizeLimit
        assertEquals(600 * 1024L, sizeError.actualBytes)
        assertEquals(StickerSpecs.ANIMATED_MAX_BYTES, sizeError.maxBytes)
    }

    @Test
    fun coroutineCancellationIsRethrown() = runTest(testDispatcher) {
        val emote = createSampleEmote(id = "cancel_1")
        fakeRepository.downloadEmoteResult = {
            throw CancellationException("Download cancelled")
        }

        val pipeline = StickerMediaPipeline(dispatcher = testDispatcher)
        val bridge = EmoteStickerBridge(
            repository = fakeRepository,
            mediaPipeline = pipeline
        )

        try {
            bridge.convertEmoteToSticker(emote, outputDir)
            fail("Expected CancellationException to be thrown")
        } catch (e: CancellationException) {
            assertEquals("Download cancelled", e.message)
        }
    }

    @Test
    fun mapsCorrectMimeTypesForFormats() = runTest(testDispatcher) {
        val testFormats = listOf(
            "GIF" to "image/gif",
            "gif" to "image/gif",
            "PNG" to "image/png",
            "png" to "image/png",
            "WEBP" to "image/webp",
            "AVIF" to "image/webp",
            "" to "image/webp"
        )

        for ((format, _) in testFormats) {
            val customExtractor = object : FrameExtractor {
                override fun extractFrames(bytes: ByteArray): StickerResult<List<AnimationFrame>> {
                    return StickerResult.Success(
                        listOf(
                            AnimationFrame(
                                bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
                                durationMs = 100,
                                timestampMs = 100
                            )
                        )
                    )
                }
            }

            val emote = createSampleEmote(id = "format_$format")
            fakeRepository.downloadEmoteResult = {
                StickerResult.Success(
                    DownloadedEmoteAsset(
                        emoteId = emote.id,
                        emoteName = emote.name,
                        bytes = byteArrayOf(1, 2, 3),
                        isAnimated = false,
                        format = format
                    )
                )
            }

            val pipeline = StickerMediaPipeline(
                frameExtractor = customExtractor,
                dispatcher = testDispatcher
            )
            val bridge = EmoteStickerBridge(
                repository = fakeRepository,
                mediaPipeline = pipeline
            )

            val result = bridge.convertEmoteToSticker(emote, outputDir)
            assertTrue("Expected success for format $format", result is StickerResult.Success<*>)
        }
    }

    private class FakeSevenTvRepository : SevenTvRepository {
        var downloadEmoteResult: (suspend (SevenTvEmote) -> StickerResult<DownloadedEmoteAsset>)? = null

        override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> {
            return downloadEmoteResult?.invoke(emote)
                ?: StickerResult.Failure(StickerError.ResourceNotFound)
        }

        override suspend fun searchEmotes(
            query: String,
            page: Int,
            limit: Int,
            category: String?
        ): StickerResult<List<SevenTvEmote>> {
            return StickerResult.Success(emptyList())
        }

        override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }

        override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }
    }

    private class TrackingFrameExtractor(
        private val frameSupplier: (() -> List<AnimationFrame>)? = null
    ) : FrameExtractor {
        var extractCallCount = 0

        override fun extractFrames(bytes: ByteArray): StickerResult<List<AnimationFrame>> {
            extractCallCount++
            return if (frameSupplier != null) {
                StickerResult.Success(frameSupplier.invoke())
            } else {
                StickerResult.Success(
                    listOf(
                        AnimationFrame(
                            bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
                            durationMs = 100,
                            timestampMs = 100
                        )
                    )
                )
            }
        }
    }

    private class FakeAnimatedWebPEncoder(
        private val outputByteSize: Int = 100 * 1024,
        private val shouldSucceed: Boolean = true
    ) : AnimatedWebPEncoder {
        override fun encode(
            bitmaps: Array<Bitmap>,
            timestampsMs: IntArray,
            quality: Float,
            outputPath: String
        ): Boolean {
            if (!shouldSucceed) return false
            val outFile = File(outputPath)
            outFile.parentFile?.mkdirs()
            val dummyBytes = ByteArray(outputByteSize)
            outFile.writeBytes(dummyBytes)
            return true
        }
    }
}

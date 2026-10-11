package com.seventv.stickers.core.media.animated

import android.graphics.Bitmap
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FrameExtractorTest {
    private val extractor = DefaultFrameExtractor()

    @Test
    fun extractFramesWithEmptyBytesFailsWithCorruptedSourceData() {
        val result = extractor.extractFrames(ByteArray(0))
        assertTrue(result is StickerResult.Failure)
        assertEquals(StickerError.CorruptedSourceData, (result as StickerResult.Failure).error)
    }

    @Test
    fun extractFramesWithInvalidBytesFailsWithCorruptedSourceData() {
        val result = extractor.extractFrames(byteArrayOf(0, 1, 2, 3, 4))
        assertTrue(result is StickerResult.Failure)
        assertEquals(StickerError.CorruptedSourceData, (result as StickerResult.Failure).error)
    }

    @Test
    fun extractFramesWithValidStaticImageDecodesSingleFrame() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val result = extractor.extractFrames(baos.toByteArray())
        assertTrue(result is StickerResult.Success)
        val frames = (result as StickerResult.Success).data
        assertEquals(1, frames.size)
        assertEquals(32, frames[0].bitmap.width)
        assertEquals(32, frames[0].bitmap.height)
        assertEquals(Bitmap.Config.ARGB_8888, frames[0].bitmap.config)
    }
}

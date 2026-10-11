package com.seventv.stickers.core.media.optimizer

import android.graphics.Bitmap
import android.graphics.Color
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.model.StickerSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StickerSizeOptimizerTest {
    private val optimizer = StickerSizeOptimizer()

    @Test
    fun compressStaticWebPSucceedsUnder95KB() {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val outFile = File.createTempFile("test_static", ".webp")
        outFile.deleteOnExit()
        val result = optimizer.compressStaticWebP(bitmap, outFile)
        assertTrue(result is StickerResult.Success<*>)
        val bytes = (result as StickerResult.Success<Long>).data
        assertEquals(outFile.length(), bytes)
        assertTrue(bytes <= StickerSpecs.STATIC_SAFE_CEILING_BYTES)
        assertTrue(bytes > 0)
    }

    @Test
    fun compressTrayIconSucceedsUnder50KB() {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val outFile = File.createTempFile("test_tray", ".webp")
        outFile.deleteOnExit()
        val result = optimizer.compressTrayIcon(bitmap, outFile)
        assertTrue(result is StickerResult.Success<*>)
        val bytes = (result as StickerResult.Success<Long>).data
        assertEquals(outFile.length(), bytes)
        assertTrue(bytes <= StickerSpecs.TRAY_ICON_MAX_BYTES)
        assertTrue(bytes > 0)
    }

    @Test
    fun exceedsSizeLimitReturnsFailureWhenUncompressible() {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.MAGENTA)
        val outFile = File.createTempFile("test_too_small_limit", ".webp")
        outFile.deleteOnExit()

        // Setting an impossible byte limit (e.g., 5 bytes) forces exhaustion of all qualities and downscaling attempts
        val result = optimizer.compressWithOptimizer(bitmap, outFile, maxCeilingBytes = 5L)
        assertTrue(result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue(error is StickerError.ExceedsSizeLimit)
        val limitError = error as StickerError.ExceedsSizeLimit
        assertEquals(5L, limitError.maxBytes)
        assertTrue(limitError.actualBytes > 5L)
        assertFalse(limitError.canFallbackToStatic)
    }

    @Test
    fun recycledBitmapThrowsIllegalArgumentException() {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        val outFile = File.createTempFile("test_recycled", ".webp")
        outFile.deleteOnExit()
        assertThrows(IllegalArgumentException::class.java) {
            optimizer.compressStaticWebP(bitmap, outFile)
        }
        assertThrows(IllegalArgumentException::class.java) {
            optimizer.compressTrayIcon(bitmap, outFile)
        }
    }

    @Test
    fun storageIoErrorReturnsFailure() {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        // An existing directory cannot be written to as a regular file
        val invalidDirAsFile = File.createTempFile("test_io_dir", "").apply {
            delete()
            mkdir()
        }
        invalidDirAsFile.deleteOnExit()
        val result = optimizer.compressStaticWebP(bitmap, invalidDirAsFile)
        assertTrue(result is StickerResult.Failure)
        assertTrue((result as StickerResult.Failure).error is StickerError.StorageIoError)
    }
}

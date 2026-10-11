package com.seventv.stickers.core.media.normalizer

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CanvasStickerNormalizerTest {
    private val normalizer = CanvasStickerNormalizer()

    @Test
    fun normalizeWideBitmapMaintainsPaddingAndCenters() {
        val wideBitmap = Bitmap.createBitmap(128, 32, Bitmap.Config.ARGB_8888)
        wideBitmap.eraseColor(Color.RED)
        val normalized = normalizer.normalizeToStickerCanvas(wideBitmap)
        assertEquals(512, normalized.width)
        assertEquals(512, normalized.height)

        // Usable width is 480, height is 120, centered vertically (top padding = 196px, left padding = 16px)
        // Outer margin pixels must be transparent (alpha == 0)
        assertEquals(Color.TRANSPARENT, normalized.getPixel(0, 0))
        assertEquals(Color.TRANSPARENT, normalized.getPixel(511, 511))
        assertEquals(Color.TRANSPARENT, normalized.getPixel(256, 0))

        // Centered content pixel must contain source color
        assertEquals(Color.RED, normalized.getPixel(256, 256))
    }

    @Test
    fun normalizeTallBitmapMaintainsPaddingAndCenters() {
        val tallBitmap = Bitmap.createBitmap(32, 128, Bitmap.Config.ARGB_8888)
        tallBitmap.eraseColor(Color.BLUE)
        val normalized = normalizer.normalizeToStickerCanvas(tallBitmap)
        assertEquals(512, normalized.width)
        assertEquals(512, normalized.height)

        // Usable height is 480, width is 120, centered horizontally (left padding = 196px, top padding = 16px)
        assertEquals(Color.TRANSPARENT, normalized.getPixel(0, 0))
        assertEquals(Color.TRANSPARENT, normalized.getPixel(0, 256))

        // Centered content pixel must contain source color
        assertEquals(Color.BLUE, normalized.getPixel(256, 256))
    }

    @Test
    fun normalizeSquareBitmapMaintainsPaddingAndCenters() {
        val squareBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        squareBitmap.eraseColor(Color.MAGENTA)
        val normalized = normalizer.normalizeToStickerCanvas(squareBitmap)
        assertEquals(512, normalized.width)
        assertEquals(512, normalized.height)

        // 16px padding on all sides
        assertEquals(Color.TRANSPARENT, normalized.getPixel(0, 0))
        assertEquals(Color.MAGENTA, normalized.getPixel(256, 256))
    }

    @Test
    fun normalizeToTrayIconProduces96x96() {
        val squareBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        squareBitmap.eraseColor(Color.GREEN)
        val tray = normalizer.normalizeToTrayIcon(squareBitmap)
        assertEquals(96, tray.width)
        assertEquals(96, tray.height)

        // 1px padding on all sides (usable area 94x94 centered)
        assertEquals(Color.TRANSPARENT, tray.getPixel(0, 0))
        assertEquals(Color.GREEN, tray.getPixel(48, 48))
    }

    @Test
    fun normalizeToTrayIconWithNonSquareProduces96x96() {
        val wideBitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        wideBitmap.eraseColor(Color.YELLOW)
        val tray = normalizer.normalizeToTrayIcon(wideBitmap)
        assertEquals(96, tray.width)
        assertEquals(96, tray.height)

        assertEquals(Color.TRANSPARENT, tray.getPixel(0, 0))
        assertEquals(Color.YELLOW, tray.getPixel(48, 48))
    }

    @Test
    fun recycledBitmapThrowsIllegalArgumentException() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        assertThrows(IllegalArgumentException::class.java) {
            normalizer.normalizeToStickerCanvas(bitmap)
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizer.normalizeToTrayIcon(bitmap)
        }
    }

    @Test
    fun extremeAspectRatioDoesNotCollapseToZeroDimensions() {
        val thinBitmap = Bitmap.createBitmap(4800, 1, Bitmap.Config.ARGB_8888)
        val normalized = normalizer.normalizeToStickerCanvas(thinBitmap)
        assertEquals(512, normalized.width)
        assertEquals(512, normalized.height)
    }
}

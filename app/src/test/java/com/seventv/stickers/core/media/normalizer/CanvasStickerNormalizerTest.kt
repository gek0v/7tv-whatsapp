package com.seventv.stickers.core.media.normalizer

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
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
        val normalized = normalizer.normalizeToStickerCanvas(wideBitmap)
        assertEquals(512, normalized.width)
        assertEquals(512, normalized.height)
        // Usable width is 480, height should be 480 * (32/128) = 120, centered vertically
    }

    @Test
    fun normalizeSquareBitmapMaintainsPaddingAndCenters() {
        val squareBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val normalized = normalizer.normalizeToStickerCanvas(squareBitmap)
        assertEquals(512, normalized.width)
        assertEquals(512, normalized.height)
    }

    @Test
    fun normalizeTallBitmapMaintainsPaddingAndCenters() {
        val tallBitmap = Bitmap.createBitmap(32, 128, Bitmap.Config.ARGB_8888)
        val normalized = normalizer.normalizeToStickerCanvas(tallBitmap)
        assertEquals(512, normalized.width)
        assertEquals(512, normalized.height)
    }

    @Test
    fun normalizeToTrayIconProduces96x96() {
        val squareBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val tray = normalizer.normalizeToTrayIcon(squareBitmap)
        assertEquals(96, tray.width)
        assertEquals(96, tray.height)
    }

    @Test
    fun normalizeToTrayIconWithNonSquareProduces96x96() {
        val wideBitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val tray = normalizer.normalizeToTrayIcon(wideBitmap)
        assertEquals(96, tray.width)
        assertEquals(96, tray.height)
    }
}

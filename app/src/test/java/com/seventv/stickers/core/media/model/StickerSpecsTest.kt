package com.seventv.stickers.core.media.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StickerSpecsTest {

    @Test
    fun stickerSpecsMatchWhatsAppRequirements() {
        assertEquals(512, StickerSpecs.STICKER_SIZE)
        assertEquals(16, StickerSpecs.STICKER_PADDING)
        assertEquals(480, StickerSpecs.USABLE_AREA)
        assertEquals(100 * 1024L, StickerSpecs.STATIC_MAX_BYTES)
        assertEquals(95 * 1024L, StickerSpecs.STATIC_SAFE_CEILING_BYTES)
        assertEquals(500 * 1024L, StickerSpecs.ANIMATED_MAX_BYTES)
        assertEquals(480 * 1024L, StickerSpecs.ANIMATED_SAFE_CEILING_BYTES)
        assertEquals(3000, StickerSpecs.ANIMATED_MAX_DURATION_MS)
        assertEquals(30, StickerSpecs.ANIMATED_MAX_FPS)
        assertEquals(96, StickerSpecs.TRAY_ICON_SIZE)
        assertEquals(1, StickerSpecs.TRAY_ICON_PADDING)
        assertEquals(94, StickerSpecs.TRAY_ICON_USABLE_AREA)
        assertEquals(50 * 1024L, StickerSpecs.TRAY_ICON_MAX_BYTES)
    }

    @Test
    fun rawMediaInputEqualityAndHashCode() {
        val input1 = RawMediaInput(bytes = byteArrayOf(1, 2, 3), isAnimated = false, mimeType = "image/png")
        val input2 = RawMediaInput(bytes = byteArrayOf(1, 2, 3), isAnimated = false, mimeType = "image/png")
        val input3 = RawMediaInput(bytes = byteArrayOf(1, 2, 4), isAnimated = false, mimeType = "image/png")

        assertEquals(input1, input2)
        assertEquals(input1.hashCode(), input2.hashCode())
        assertFalse(input1 == input3)
    }

    @Test
    fun processedStickerDefaultValues() {
        val file = File("test.webp")
        val processed = ProcessedSticker(
            file = file,
            byteSize = 1024L,
            isAnimated = false
        )
        assertEquals(512, processed.width)
        assertEquals(512, processed.height)
        assertEquals(0, processed.durationMs)
        assertEquals(1, processed.frameCount)
        assertFalse(processed.isAnimated)
    }
}

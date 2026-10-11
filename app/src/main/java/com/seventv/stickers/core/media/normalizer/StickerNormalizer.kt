package com.seventv.stickers.core.media.normalizer

import android.graphics.Bitmap

/**
 * Contract for normalizing raw or converted bitmaps onto WhatsApp-compliant sticker and tray icon canvas dimensions.
 */
interface StickerNormalizer {
    /**
     * Normalizes the given [source] bitmap into a 512x512 ARGB_8888 sticker canvas,
     * maintaining aspect ratio within the 480x480 usable area and centering it with transparent padding.
     */
    fun normalizeToStickerCanvas(source: Bitmap): Bitmap

    /**
     * Normalizes the given [source] bitmap into a 96x96 ARGB_8888 tray icon canvas,
     * maintaining aspect ratio within the 94x94 usable area and centering it with transparent padding.
     */
    fun normalizeToTrayIcon(source: Bitmap): Bitmap
}

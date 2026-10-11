package com.seventv.stickers.core.media.model

/**
 * Strict technical specifications and constants imposed by WhatsApp sticker requirements.
 */
object StickerSpecs {
    const val STICKER_SIZE = 512
    const val STICKER_PADDING = 16
    const val USABLE_AREA = 480
    const val STATIC_MAX_BYTES = 100 * 1024L
    const val STATIC_SAFE_CEILING_BYTES = 95 * 1024L
    const val ANIMATED_MAX_BYTES = 500 * 1024L
    const val ANIMATED_SAFE_CEILING_BYTES = 480 * 1024L
    const val ANIMATED_MAX_DURATION_MS = 3000
    const val ANIMATED_MAX_FPS = 30
    const val TRAY_ICON_SIZE = 96
    const val TRAY_ICON_PADDING = 1
    const val TRAY_ICON_USABLE_AREA = 94
    const val TRAY_ICON_MAX_BYTES = 50 * 1024L
}

package com.seventv.stickers.core.media.model

import java.io.File

/**
 * Output model representing a finalized, normalized, and compressed sticker file ready for WhatsApp.
 *
 * @property file The generated WebP sticker file on disk.
 * @property width The final sticker canvas width in pixels (must be exactly 512).
 * @property height The final sticker canvas height in pixels (must be exactly 512).
 * @property byteSize File size in bytes (must be <= 100KB for static, <= 500KB for animated).
 * @property isAnimated Whether the sticker is an animated WebP sticker.
 * @property durationMs Total duration of the animation loop in milliseconds (0 for static).
 * @property frameCount Number of frames in the sticker (1 for static).
 */
data class ProcessedSticker(
    val file: File,
    val width: Int = 512,
    val height: Int = 512,
    val byteSize: Long,
    val isAnimated: Boolean,
    val durationMs: Int = 0,
    val frameCount: Int = 1
)

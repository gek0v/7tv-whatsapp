package com.seventv.stickers.core.media.model

import android.graphics.Bitmap

/**
 * Representation of a single frame in an animated sequence.
 *
 * @property bitmap Decoded frame image data as an ARGB_8888 bitmap.
 * @property durationMs Frame display duration in milliseconds.
 * @property timestampMs Cumulative presentation timestamp in milliseconds from animation start.
 */
data class AnimationFrame(
    val bitmap: Bitmap,
    val durationMs: Int,
    val timestampMs: Int = 0
)

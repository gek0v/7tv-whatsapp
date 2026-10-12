package com.seventv.stickers.core.media.animated

import android.graphics.Bitmap

/**
 * Result data class populated by [NativeWebPAnimDecoder] via JNI.
 */
data class WebPDecodedFrames(
    val bitmaps: Array<Bitmap>,
    val durationsMs: IntArray,
    val timestampsMs: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WebPDecodedFrames

        if (!bitmaps.contentEquals(other.bitmaps)) return false
        if (!durationsMs.contentEquals(other.durationsMs)) return false
        if (!timestampsMs.contentEquals(other.timestampsMs)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bitmaps.contentHashCode()
        result = 31 * result + durationsMs.contentHashCode()
        result = 31 * result + timestampsMs.contentHashCode()
        return result
    }
}

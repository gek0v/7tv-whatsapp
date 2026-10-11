package com.seventv.stickers.core.media.animated

import android.graphics.Bitmap

/**
 * Interface for encoding animated image sequences into animated WebP format.
 */
interface AnimatedWebPEncoder {

    /**
     * Encodes a sequence of 512x512 [Bitmap] frames into an animated WebP file.
     *
     * @param bitmaps Sequence of [Bitmap]s to be encoded. Must be ARGB_8888 and 512x512.
     * @param timestampsMs Presentation timestamps for each frame in milliseconds.
     * @param quality Compression quality from 0.0f (maximum compression) to 100.0f (maximum quality).
     * @param outputPath Absolute filesystem path where the animated WebP file will be saved.
     * @return `true` if encoding and file creation succeeded, `false` otherwise.
     */
    fun encode(
        bitmaps: Array<Bitmap>,
        timestampsMs: IntArray,
        quality: Float,
        outputPath: String
    ): Boolean
}

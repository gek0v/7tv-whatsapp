package com.seventv.stickers.core.media.animated

import android.graphics.Bitmap
import android.util.Log

/**
 * Native animated WebP encoder backed by Google's `libwebp` (`WebPAnimEncoder` API) via JNI.
 *
 * Produces WhatsApp-compliant animated stickers:
 * - Dimensions: 512 x 512 canvas
 * - Loop count: 0 (infinite loop)
 * - Compression method: 4
 */
class NativeWebPAnimEncoder : AnimatedWebPEncoder {

    /**
     * Native encoding entry point implemented in `webp_anim_encoder.cpp`.
     */
    external override fun encode(
        bitmaps: Array<Bitmap>,
        timestampsMs: IntArray,
        quality: Float,
        outputPath: String
    ): Boolean

    companion object {
        private const val TAG = "NativeWebPAnimEncoder"

        @Volatile
        var isLoaded: Boolean = false
            private set

        init {
            try {
                System.loadLibrary("seven_tv_webp")
                isLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                // Safeguard against linkage errors in JVM host unit tests without native binary
                Log.w(TAG, "Native library 'seven_tv_webp' could not be loaded: ${e.message}")
                isLoaded = false
            }
        }
    }
}

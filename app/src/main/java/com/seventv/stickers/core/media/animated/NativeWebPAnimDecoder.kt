package com.seventv.stickers.core.media.animated

import android.util.Log

/**
 * Native animated WebP decoder backed by Google's `libwebp` (`WebPAnimDecoder` API) via JNI.
 */
object NativeWebPAnimDecoder {
    private const val TAG = "NativeWebPAnimDecoder"

    val isAvailable: Boolean
        get() = NativeWebPAnimEncoder.isLoaded

    external fun decode(webpBytes: ByteArray): WebPDecodedFrames?
}

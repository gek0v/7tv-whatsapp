package com.seventv.stickers.ui.components

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/**
 * Global Coil setup for animated WebP and GIF decoding.
 */
object CoilSetup {
    fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                }
                add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}

/**
 * Top-level convenience function for creating a configured [ImageLoader].
 */
fun createCoilImageLoader(context: Context): ImageLoader = CoilSetup.createImageLoader(context)

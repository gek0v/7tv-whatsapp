package com.seventv.stickers.ui.components

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Global Coil setup for animated WebP and GIF decoding.
 */
object CoilSetup {
    fun emotePreviewCacheKey(emoteId: String): String = "emote_preview_$emoteId"
    fun packStickerPreviewCacheKey(stickerId: String): String = "pack_sticker_preview_$stickerId"

    fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .allowHardware(true)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                }
                add(GifDecoder.Factory())
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}

/**
 * Top-level convenience function for creating a configured [ImageLoader].
 */
fun createCoilImageLoader(context: Context): ImageLoader = CoilSetup.createImageLoader(context)

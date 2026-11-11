package com.seventv.stickers.ui.components

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Global Coil setup for animated WebP and GIF decoding with high CDN concurrency.
 */
object CoilSetup {
    fun emotePreviewCacheKey(emoteId: String): String = "emote_preview_$emoteId"
    fun packStickerPreviewCacheKey(stickerId: String): String = "pack_sticker_preview_$stickerId"

    fun createImageLoader(context: Context): ImageLoader {
        val imageDispatcher = Dispatcher().apply {
            maxRequests = 96
            maxRequestsPerHost = 24
        }
        val imageClient = OkHttpClient.Builder()
            .dispatcher(imageDispatcher)
            .connectionPool(ConnectionPool(12, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(imageClient)
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
            .crossfade(120)
            .build()
    }
}

/**
 * Top-level convenience function for creating a configured [ImageLoader].
 */
fun createCoilImageLoader(context: Context): ImageLoader = CoilSetup.createImageLoader(context)

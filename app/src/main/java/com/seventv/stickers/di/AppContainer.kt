package com.seventv.stickers.di

import android.content.Context
import com.seventv.stickers.core.media.DefaultStickerMediaPipeline
import com.seventv.stickers.core.network.api.SevenTvNetworkFactory
import com.seventv.stickers.core.network.downloader.DefaultEmoteDownloader
import com.seventv.stickers.data.local.AppDatabase
import com.seventv.stickers.data.local.storage.DefaultStickerStorageManager
import com.seventv.stickers.data.local.storage.StickerStorageManager
import com.seventv.stickers.data.repository.DefaultSevenTvRepository
import com.seventv.stickers.data.repository.DefaultStickerPackRepository
import com.seventv.stickers.data.repository.SevenTvRepository
import com.seventv.stickers.data.repository.StickerPackRepository
import java.io.File

interface AppContainer {
    val sevenTvRepository: SevenTvRepository
    val stickerPackRepository: StickerPackRepository
    val storageManager: StickerStorageManager
    val database: AppDatabase
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    override val storageManager: StickerStorageManager by lazy {
        DefaultStickerStorageManager(context.filesDir)
    }

    private val sevenTvApi by lazy {
        SevenTvNetworkFactory.createSevenTvApi()
    }

    private val emoteDownloader by lazy {
        DefaultEmoteDownloader(File(context.cacheDir, "7tv_emotes_cache"))
    }

    override val sevenTvRepository: SevenTvRepository by lazy {
        DefaultSevenTvRepository(sevenTvApi, emoteDownloader)
    }

    private val mediaPipeline by lazy {
        DefaultStickerMediaPipeline()
    }

    override val stickerPackRepository: StickerPackRepository by lazy {
        DefaultStickerPackRepository(
            database = database,
            storageManager = storageManager,
            mediaPipeline = mediaPipeline,
            sevenTvRepository = sevenTvRepository
        )
    }
}

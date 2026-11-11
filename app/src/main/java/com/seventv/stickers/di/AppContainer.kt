package com.seventv.stickers.di

import android.content.Context
import com.seventv.stickers.core.media.DefaultStickerMediaPipeline
import com.seventv.stickers.core.network.api.SevenTvNetworkFactory
import com.seventv.stickers.core.network.downloader.DefaultEmoteDownloader
import com.seventv.stickers.core.updater.AppUpdateChecker
import com.seventv.stickers.core.updater.AppUpdateManager
import com.seventv.stickers.core.updater.DataStoreUpdatePreferencesRepository
import com.seventv.stickers.core.updater.GitHubReleaseService
import com.seventv.stickers.core.updater.OkHttpChecksumAssetFetcher
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
    val packImportManager: com.seventv.stickers.core.domain.manager.PackImportManager
    val stickerShareManager: com.seventv.stickers.core.domain.manager.StickerShareManager
    val appUpdateManager: AppUpdateManager
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

    private val emoteStickerBridge by lazy {
        com.seventv.stickers.data.repository.EmoteStickerBridge(sevenTvRepository, mediaPipeline)
    }

    override val stickerPackRepository: StickerPackRepository by lazy {
        DefaultStickerPackRepository(
            database = database,
            storageManager = storageManager,
            mediaPipeline = mediaPipeline,
            sevenTvRepository = sevenTvRepository
        )
    }

    override val packImportManager: com.seventv.stickers.core.domain.manager.PackImportManager by lazy {
        com.seventv.stickers.core.domain.manager.PackImportManager(stickerPackRepository)
    }

    override val stickerShareManager: com.seventv.stickers.core.domain.manager.StickerShareManager by lazy {
        com.seventv.stickers.core.domain.manager.StickerShareManager(
            context = context,
            emoteStickerBridge = emoteStickerBridge,
            sevenTvRepository = sevenTvRepository
        )
    }

    private val gitHubReleaseService by lazy {
        GitHubReleaseService.create()
    }

    private val updatePreferences by lazy {
        DataStoreUpdatePreferencesRepository(context)
    }

    private val checksumAssetFetcher by lazy {
        OkHttpChecksumAssetFetcher(GitHubReleaseService.createOkHttpClient())
    }

    private val appUpdateChecker by lazy {
        AppUpdateChecker(
            releaseService = gitHubReleaseService,
            preferences = updatePreferences,
            checksumAssetFetcher = checksumAssetFetcher
        )
    }

    override val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManager(
            context = context,
            checker = appUpdateChecker,
            preferences = updatePreferences
        )
    }
}
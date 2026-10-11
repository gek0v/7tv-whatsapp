package com.seventv.stickers.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.engine.PackSplittingEngine
import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.media.StickerMediaPipeline
import com.seventv.stickers.core.media.model.AnimationFrame
import com.seventv.stickers.core.media.animated.DefaultFrameExtractor
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.data.local.dao.StickerDao
import com.seventv.stickers.data.local.dao.StickerPackDao
import com.seventv.stickers.data.local.entity.PackWithStickers
import com.seventv.stickers.data.local.entity.StickerEntity
import com.seventv.stickers.data.local.entity.StickerPackEntity
import com.seventv.stickers.data.local.storage.StickerStorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DefaultStickerPackRepository(
    private val stickerPackDao: StickerPackDao,
    private val stickerDao: StickerDao,
    private val storageManager: StickerStorageManager,
    private val emoteBridge: EmoteStickerBridge,
    private val mediaPipeline: StickerMediaPipeline,
    private val splittingEngine: PackSplittingEngine = PackSplittingEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : StickerPackRepository {

    override fun observeAllPacks(): Flow<List<StickerPack>> {
        return stickerPackDao.observeAllPacks()
            .map { packsWithStickers ->
                packsWithStickers.map { toDomain(it) }
            }
            .flowOn(dispatcher)
    }

    override suspend fun getPack(identifier: String): StickerResult<StickerPack> = withContext(dispatcher) {
        try {
            val packWithStickers = stickerPackDao.getPackByIdentifier(identifier)
            if (packWithStickers == null) {
                StickerResult.Failure(StickerError.PackNotFound(identifier))
            } else {
                StickerResult.Success(toDomain(packWithStickers))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    override suspend fun createPacksFromEmotes(
        basePackName: String,
        publisher: String,
        emotes: List<SevenTvEmote>
    ): StickerResult<List<StickerPack>> = withContext(dispatcher) {
        try {
            val splitResult = splittingEngine.splitEmotes(basePackName, emotes)
            val splitPlans = when (splitResult) {
                is StickerResult.Success -> splitResult.data
                is StickerResult.Failure -> return@withContext splitResult
            }

            val createdPacks = mutableListOf<StickerPack>()

            for (plan in splitPlans) {
                val packIdentifier = UUID.randomUUID().toString()
                val packDir = storageManager.getPackDirectory(packIdentifier)
                val stagingDir = File(packDir, "staging").apply { mkdirs() }

                val stickerEntities = mutableListOf<StickerEntity>()
                var firstProcessedFile: File? = null

                try {
                    for ((index, emote) in plan.emotes.withIndex()) {
                        val stickerId = UUID.randomUUID().toString()
                        val processResult = emoteBridge.convertEmoteToSticker(
                            emote = emote,
                            outputDir = stagingDir,
                            allowStaticFallback = true
                        )

                        val processedSticker = when (processResult) {
                            is StickerResult.Success -> processResult.data
                            is StickerResult.Failure -> {
                                stagingDir.deleteRecursively()
                                storageManager.deletePackDirectory(packIdentifier)
                                for (p in createdPacks) {
                                    stickerPackDao.deletePack(p.identifier)
                                    storageManager.deletePackDirectory(p.identifier)
                                }
                                return@withContext processResult
                            }
                        }

                        if (firstProcessedFile == null) {
                            firstProcessedFile = processedSticker.file
                        }

                        val stickerBytes = processedSticker.file.readBytes()
                        val savedFile = storageManager.saveStickerFile(
                            packIdentifier = packIdentifier,
                            stickerId = stickerId,
                            bytes = stickerBytes
                        )

                        val relativePath = "sticker_packs/$packIdentifier/sticker_$stickerId.webp"
                        stickerEntities.add(
                            StickerEntity(
                                id = stickerId,
                                packIdentifier = packIdentifier,
                                emoteId = emote.id,
                                emoteName = emote.name,
                                relativeFilePath = relativePath,
                                emojis = listOf("💬"),
                                fileLength = savedFile.length(),
                                sortOrder = index
                            )
                        )
                    }

                    if (firstProcessedFile == null || !firstProcessedFile.exists()) {
                        stagingDir.deleteRecursively()
                        storageManager.deletePackDirectory(packIdentifier)
                        for (p in createdPacks) {
                            stickerPackDao.deletePack(p.identifier)
                            storageManager.deletePackDirectory(p.identifier)
                        }
                        return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
                    }

                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    var firstBitmap: Bitmap? = BitmapFactory.decodeFile(firstProcessedFile.absolutePath, options)
                    val extraFramesToRecycle = mutableListOf<AnimationFrame>()

                    if (firstBitmap == null) {
                        val frameExtractor = DefaultFrameExtractor()
                        val framesResult = frameExtractor.extractFrames(firstProcessedFile.readBytes())
                        if (framesResult is StickerResult.Success && framesResult.data.isNotEmpty()) {
                            firstBitmap = framesResult.data.first().bitmap
                            if (framesResult.data.size > 1) {
                                extraFramesToRecycle.addAll(framesResult.data.drop(1))
                            }
                        }
                    }

                    if (firstBitmap == null) {
                        stagingDir.deleteRecursively()
                        storageManager.deletePackDirectory(packIdentifier)
                        for (p in createdPacks) {
                            stickerPackDao.deletePack(p.identifier)
                            storageManager.deletePackDirectory(p.identifier)
                        }
                        return@withContext StickerResult.Failure(StickerError.CorruptedSourceData)
                    }

                    val trayOutFile = File(stagingDir, "tray_icon.webp")
                    val trayResult = try {
                        mediaPipeline.processTrayIcon(firstBitmap, trayOutFile)
                    } finally {
                        for (extra in extraFramesToRecycle) {
                            if (!extra.bitmap.isRecycled) {
                                extra.bitmap.recycle()
                            }
                        }
                        if (!firstBitmap.isRecycled) {
                            firstBitmap.recycle()
                        }
                    }

                    when (trayResult) {
                        is StickerResult.Success -> {
                            val trayBytes = trayOutFile.readBytes()
                            storageManager.saveTrayIcon(packIdentifier, trayBytes)
                        }
                        is StickerResult.Failure -> {
                            stagingDir.deleteRecursively()
                            storageManager.deletePackDirectory(packIdentifier)
                            for (p in createdPacks) {
                                stickerPackDao.deletePack(p.identifier)
                                storageManager.deletePackDirectory(p.identifier)
                            }
                            return@withContext trayResult
                        }
                    }

                    stagingDir.deleteRecursively()

                    val trayRelativePath = "sticker_packs/$packIdentifier/tray_icon.webp"
                    val createdAt = System.currentTimeMillis()
                    val packEntity = StickerPackEntity(
                        identifier = packIdentifier,
                        name = plan.packName,
                        publisher = publisher,
                        trayImageRelativePath = trayRelativePath,
                        isAnimated = plan.isAnimated,
                        isWhitelisted = false,
                        createdAt = createdAt
                    )

                    stickerPackDao.insertPackWithStickers(packEntity, stickerEntities)

                    val domainStickers = stickerEntities.sortedBy { it.sortOrder }.map { entity ->
                        PackSticker(
                            id = entity.id,
                            packIdentifier = entity.packIdentifier,
                            emoteId = entity.emoteId,
                            emoteName = entity.emoteName,
                            filePath = entity.relativeFilePath,
                            emojis = entity.emojis,
                            fileLength = entity.fileLength,
                            sortOrder = entity.sortOrder
                        )
                    }

                    val domainPack = StickerPack(
                        identifier = packIdentifier,
                        name = plan.packName,
                        publisher = publisher,
                        trayImagePath = trayRelativePath,
                        isAnimated = plan.isAnimated,
                        isWhitelisted = false,
                        stickers = domainStickers,
                        createdAt = createdAt
                    )

                    createdPacks.add(domainPack)

                } catch (e: CancellationException) {
                    stagingDir.deleteRecursively()
                    storageManager.deletePackDirectory(packIdentifier)
                    for (p in createdPacks) {
                        stickerPackDao.deletePack(p.identifier)
                        storageManager.deletePackDirectory(p.identifier)
                    }
                    throw e
                } catch (e: Exception) {
                    stagingDir.deleteRecursively()
                    storageManager.deletePackDirectory(packIdentifier)
                    for (p in createdPacks) {
                        stickerPackDao.deletePack(p.identifier)
                        storageManager.deletePackDirectory(p.identifier)
                    }
                    return@withContext StickerResult.Failure(StickerError.StorageIoError(e))
                }
            }

            StickerResult.Success(createdPacks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    override suspend fun deletePack(identifier: String): StickerResult<Unit> = withContext(dispatcher) {
        try {
            stickerPackDao.deletePack(identifier)
            storageManager.deletePackDirectory(identifier)
            StickerResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    override suspend fun updateStickerEmojis(
        stickerId: String,
        emojis: List<String>
    ): StickerResult<Unit> = withContext(dispatcher) {
        try {
            stickerDao.updateEmojis(stickerId, emojis)
            StickerResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    override suspend fun updatePackWhitelisted(
        identifier: String,
        isWhitelisted: Boolean
    ): StickerResult<Unit> = withContext(dispatcher) {
        try {
            stickerPackDao.updateWhitelisted(identifier, isWhitelisted)
            StickerResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    private fun toDomain(packWithStickers: PackWithStickers): StickerPack {
        return StickerPack(
            identifier = packWithStickers.pack.identifier,
            name = packWithStickers.pack.name,
            publisher = packWithStickers.pack.publisher,
            trayImagePath = packWithStickers.pack.trayImageRelativePath,
            isAnimated = packWithStickers.pack.isAnimated,
            isWhitelisted = packWithStickers.pack.isWhitelisted,
            stickers = packWithStickers.stickers.sortedBy { it.sortOrder }.map { entity ->
                PackSticker(
                    id = entity.id,
                    packIdentifier = entity.packIdentifier,
                    emoteId = entity.emoteId,
                    emoteName = entity.emoteName,
                    filePath = entity.relativeFilePath,
                    emojis = entity.emojis,
                    fileLength = entity.fileLength,
                    sortOrder = entity.sortOrder
                )
            },
            createdAt = packWithStickers.pack.createdAt
        )
    }
}

package com.seventv.stickers.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.whatsapp.WhatsAppWhitelistChecker
import com.seventv.stickers.core.domain.engine.PackSplittingEngine
import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.media.StickerMediaPipeline
import com.seventv.stickers.core.media.model.AnimationFrame
import com.seventv.stickers.core.media.animated.DefaultFrameExtractor
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.data.local.AppDatabase
import com.seventv.stickers.data.local.dao.StickerDao
import com.seventv.stickers.data.local.dao.StickerPackDao
import com.seventv.stickers.data.local.entity.PackWithStickers
import com.seventv.stickers.data.local.entity.StickerEntity
import com.seventv.stickers.data.local.entity.StickerPackEntity
import com.seventv.stickers.data.local.storage.StickerStorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class DefaultStickerPackRepository(
    private val stickerPackDao: StickerPackDao,
    private val stickerDao: StickerDao,
    private val storageManager: StickerStorageManager,
    private val emoteBridge: EmoteStickerBridge,
    private val mediaPipeline: StickerMediaPipeline,
    private val splittingEngine: PackSplittingEngine = PackSplittingEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : StickerPackRepository {

    constructor(
        database: AppDatabase,
        storageManager: StickerStorageManager,
        mediaPipeline: StickerMediaPipeline,
        sevenTvRepository: SevenTvRepository,
        splittingEngine: PackSplittingEngine = PackSplittingEngine,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        stickerPackDao = database.stickerPackDao(),
        stickerDao = database.stickerDao(),
        storageManager = storageManager,
        emoteBridge = EmoteStickerBridge(sevenTvRepository, mediaPipeline),
        mediaPipeline = mediaPipeline,
        splittingEngine = splittingEngine,
        dispatcher = dispatcher
    )

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
        emotes: List<SevenTvEmote>,
        onProgress: ((current: Int, total: Int) -> Unit)?
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
                    val semaphore = Semaphore(4)
                    val totalInPlan = plan.emotes.size
                    val completedCounter = AtomicInteger(0)

                    val processedResults = coroutineScope {
                        plan.emotes.mapIndexed { index, emote ->
                            async(Dispatchers.Default) {
                                semaphore.withPermit {
                                    val stickerId = UUID.randomUUID().toString()
                                    val processResult = emoteBridge.convertEmoteToSticker(
                                        emote = emote,
                                        outputDir = stagingDir,
                                        allowStaticFallback = !plan.isAnimated
                                    )
                                    val done = completedCounter.incrementAndGet()
                                    onProgress?.invoke(done, totalInPlan)
                                    ProcessItem(
                                        index = index,
                                        emote = emote,
                                        stickerId = stickerId,
                                        result = processResult
                                    )
                                }
                            }
                        }.awaitAll()
                    }

                    val firstFailure = processedResults.firstOrNull { it.result is StickerResult.Failure }
                    if (firstFailure != null) {
                        stagingDir.deleteRecursively()
                        storageManager.deletePackDirectory(packIdentifier)
                        for (p in createdPacks) {
                            stickerPackDao.deletePack(p.identifier)
                            storageManager.deletePackDirectory(p.identifier)
                        }
                        return@withContext firstFailure.result as StickerResult.Failure
                    }

                    for (item in processedResults.sortedBy { it.index }) {
                        val processedSticker = (item.result as StickerResult.Success).data
                        if (firstProcessedFile == null) {
                            firstProcessedFile = processedSticker.file
                        }

                        val stickerBytes = processedSticker.file.readBytes()
                        val savedFile = storageManager.saveStickerFile(
                            packIdentifier = packIdentifier,
                            stickerId = item.stickerId,
                            bytes = stickerBytes
                        )

                        val relativePath = "sticker_packs/$packIdentifier/sticker_${item.stickerId}.webp"
                        stickerEntities.add(
                            StickerEntity(
                                id = item.stickerId,
                                packIdentifier = packIdentifier,
                                emoteId = item.emote.id,
                                emoteName = item.emote.name,
                                relativeFilePath = relativePath,
                                emojis = com.seventv.stickers.core.domain.mapper.EmoteEmojiMapper.mapEmoteNameToEmojis(item.emote.name),
                                fileLength = savedFile.length(),
                                sortOrder = item.index
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

                    val trayOutFile = File(stagingDir, "tray_icon.png")
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

                    val trayRelativePath = "sticker_packs/$packIdentifier/tray_icon.png"
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
                            filePath = storageManager.getAbsoluteFile(entity.relativeFilePath).absolutePath,
                            emojis = entity.emojis,
                            fileLength = entity.fileLength,
                            sortOrder = entity.sortOrder
                        )
                    }

                    val domainPack = StickerPack(
                        identifier = packIdentifier,
                        name = plan.packName,
                        publisher = publisher,
                        trayImagePath = storageManager.getAbsoluteFile(trayRelativePath).absolutePath,
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

    override suspend fun deletePacks(identifiers: List<String>): StickerResult<Unit> = withContext(dispatcher) {
        try {
            stickerPackDao.deletePacks(identifiers)
            for (id in identifiers) {
                storageManager.deletePackDirectory(id)
            }
            StickerResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    override suspend fun renamePack(identifier: String, newName: String): StickerResult<Unit> = withContext(dispatcher) {
        try {
            stickerPackDao.updatePackName(identifier, newName)
            stickerPackDao.updateWhitelisted(identifier, false)
            StickerResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    override suspend fun mergePacks(
        sourcePackIdentifiers: List<String>,
        basePackName: String
    ): StickerResult<List<StickerPack>> = withContext(dispatcher) {
        try {
            if (sourcePackIdentifiers.size < 2) {
                return@withContext StickerResult.Failure(StickerError.InsufficientStickers(sourcePackIdentifiers.size, 2))
            }

            val sourcePacks = sourcePackIdentifiers.map { id ->
                stickerPackDao.getPackByIdentifier(id)
                    ?: return@withContext StickerResult.Failure(StickerError.PackNotFound(id))
            }

            val isAnimated = sourcePacks.first().pack.isAnimated
            if (sourcePacks.any { it.pack.isAnimated != isAnimated }) {
                return@withContext StickerResult.Failure(
                    StickerError.UnsupportedFormat("No se pueden combinar paquetes animados y estáticos juntos.")
                )
            }

            val seenEmoteIds = mutableSetOf<String>()
            val seenEmoteNames = mutableSetOf<String>()
            val allStickers = sourcePacks.flatMap { it.stickers }
                .filter { sticker ->
                    val id = sticker.emoteId.trim()
                    val name = sticker.emoteName.trim().lowercase()
                    val isDuplicate = (id.isNotEmpty() && seenEmoteIds.contains(id)) ||
                        (name.isNotEmpty() && seenEmoteNames.contains(name))
                    if (!isDuplicate) {
                        if (id.isNotEmpty()) seenEmoteIds.add(id)
                        if (name.isNotEmpty()) seenEmoteNames.add(name)
                        true
                    } else {
                        false
                    }
                }
            if (allStickers.size < 3) {
                return@withContext StickerResult.Failure(
                    StickerError.InsufficientStickers(allStickers.size, 3)
                )
            }

            val chunks = partitionStickersIntoPacks(allStickers, maxPerPack = 30, minPerPack = 3)
            val createdPacks = mutableListOf<StickerPack>()

            for ((chunkIndex, chunk) in chunks.withIndex()) {
                val newPackId = java.util.UUID.randomUUID().toString()
                val packName = if (chunks.size == 1) basePackName else "$basePackName ${chunkIndex + 1}"
                val packDir = storageManager.getPackDirectory(newPackId)
                packDir.mkdirs()

                val newPackStickers = mutableListOf<PackSticker>()
                val newPackStickerEntities = mutableListOf<StickerEntity>()
                var firstSavedFile: java.io.File? = null

                for ((stickerIndex, sticker) in chunk.withIndex()) {
                    val newStickerId = java.util.UUID.randomUUID().toString()
                    val sourceFile = storageManager.getAbsoluteFile(sticker.relativeFilePath)
                    val bytes = if (sourceFile.exists()) {
                        sourceFile.readBytes()
                    } else {
                        val fallback = java.io.File(storageManager.getPackDirectory(sticker.packIdentifier), "sticker_${sticker.id}.webp")
                        if (fallback.exists()) fallback.readBytes() else ByteArray(0)
                    }

                    if (bytes.isNotEmpty()) {
                        val savedFile = storageManager.saveStickerFile(newPackId, newStickerId, bytes)
                        if (firstSavedFile == null) {
                            firstSavedFile = savedFile
                        }
                        val relativePath = "sticker_packs/$newPackId/sticker_${newStickerId}.webp"
                        val entity = StickerEntity(
                            id = newStickerId,
                            packIdentifier = newPackId,
                            emoteId = sticker.emoteId,
                            emoteName = sticker.emoteName,
                            relativeFilePath = relativePath,
                            emojis = sticker.emojis,
                            fileLength = savedFile.length(),
                            sortOrder = stickerIndex
                        )
                        newPackStickerEntities.add(entity)
                        newPackStickers.add(
                            PackSticker(
                                id = newStickerId,
                                packIdentifier = newPackId,
                                emoteId = sticker.emoteId,
                                emoteName = sticker.emoteName,
                                filePath = savedFile.absolutePath,
                                emojis = sticker.emojis,
                                fileLength = savedFile.length(),
                                sortOrder = stickerIndex
                            )
                        )
                    }
                }

                var traySaved = false
                for (srcId in sourcePackIdentifiers) {
                    val srcTrayPng = java.io.File(storageManager.getPackDirectory(srcId), "tray_icon.png")
                    if (srcTrayPng.exists() && srcTrayPng.length() > 0) {
                        storageManager.saveTrayIcon(newPackId, srcTrayPng.readBytes())
                        traySaved = true
                        break
                    }
                    val srcTrayWebp = java.io.File(storageManager.getPackDirectory(srcId), "tray_icon.webp")
                    if (srcTrayWebp.exists() && srcTrayWebp.length() > 0) {
                        try {
                            val bmp = BitmapFactory.decodeFile(srcTrayWebp.absolutePath)
                            if (bmp != null) {
                                val baos = java.io.ByteArrayOutputStream()
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                storageManager.saveTrayIcon(newPackId, baos.toByteArray())
                                bmp.recycle()
                                traySaved = true
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (!traySaved && firstSavedFile != null && firstSavedFile.exists()) {
                    val stagingDir = java.io.File(packDir, "staging_tray").apply { mkdirs() }
                    val trayOutFile = java.io.File(stagingDir, "tray_icon.png")
                    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    var firstBitmap: Bitmap? = BitmapFactory.decodeFile(firstSavedFile.absolutePath, options)
                    if (firstBitmap == null) {
                        val frameExtractor = DefaultFrameExtractor()
                        val framesResult = frameExtractor.extractFrames(firstSavedFile.readBytes())
                        if (framesResult is StickerResult.Success && framesResult.data.isNotEmpty()) {
                            firstBitmap = framesResult.data.first().bitmap
                        }
                    }

                    if (firstBitmap != null) {
                        try {
                            val trayResult = mediaPipeline.processTrayIcon(firstBitmap, trayOutFile)
                            if (trayResult is StickerResult.Success && trayOutFile.exists()) {
                                storageManager.saveTrayIcon(newPackId, trayOutFile.readBytes())
                            }
                        } finally {
                            stagingDir.deleteRecursively()
                            if (!firstBitmap.isRecycled) firstBitmap.recycle()
                        }
                    }
                }

                val createdAt = System.currentTimeMillis() + chunkIndex
                val packEntity = StickerPackEntity(
                    identifier = newPackId,
                    name = packName,
                    publisher = "7TV Stickers",
                    trayImageRelativePath = "sticker_packs/$newPackId/tray_icon.png",
                    isAnimated = isAnimated,
                    isWhitelisted = false,
                    createdAt = createdAt
                )
                stickerPackDao.insertPackWithStickers(packEntity, newPackStickerEntities)

                createdPacks.add(
                    StickerPack(
                        identifier = newPackId,
                        name = packName,
                        publisher = "7TV Stickers",
                        trayImagePath = "sticker_packs/$newPackId/tray_icon.webp",
                        isAnimated = isAnimated,
                        isWhitelisted = false,
                        stickers = newPackStickers
                    )
                )
            }

            deletePacks(sourcePackIdentifiers)

            StickerResult.Success(createdPacks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    private fun <T> partitionStickersIntoPacks(items: List<T>, maxPerPack: Int = 30, minPerPack: Int = 3): List<List<T>> {
        if (items.size <= maxPerPack) return listOf(items)
        val rawChunks = items.chunked(maxPerPack).toMutableList()
        if (rawChunks.size > 1 && rawChunks.last().size < minPerPack) {
            val needed = minPerPack - rawChunks.last().size
            val prev = rawChunks[rawChunks.size - 2].toMutableList()
            val last = rawChunks.last().toMutableList()
            if (prev.size - needed >= minPerPack) {
                val stolen = prev.takeLast(needed)
                rawChunks[rawChunks.size - 2] = prev.dropLast(needed)
                rawChunks[rawChunks.size - 1] = stolen + last
            }
        }
        return rawChunks
    }

    override suspend fun deleteStickerFromPack(packIdentifier: String, stickerId: String): StickerResult<Unit> = withContext(dispatcher) {
        try {
            stickerDao.deleteSticker(stickerId)
            storageManager.deleteStickerFile(packIdentifier, stickerId)
            stickerPackDao.updateWhitelisted(packIdentifier, false)
            StickerResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    override suspend fun addEmotesToPack(packIdentifier: String, emotes: List<SevenTvEmote>): StickerResult<Unit> = withContext(dispatcher) {
        try {
            val pack = stickerPackDao.getPackByIdentifier(packIdentifier)
                ?: return@withContext StickerResult.Failure(StickerError.PackNotFound(packIdentifier))

            val packIsAnimated = pack.pack.isAnimated
            if (emotes.any { it.isAnimated != packIsAnimated }) {
                return@withContext StickerResult.Failure(
                    StickerError.UnsupportedFormat("No puedes mezclar stickers animados y estáticos en el mismo pack")
                )
            }

            val currentCount = pack.stickers.size
            if (currentCount + emotes.size > 30) {
                return@withContext StickerResult.Failure(StickerError.ExceedsPackLimit(currentCount + emotes.size, 30))
            }

            val packDir = storageManager.getPackDirectory(packIdentifier)
            val stagingDir = File(packDir, "staging").apply { mkdirs() }

            try {
                for ((index, emote) in emotes.withIndex()) {
                    val stickerId = UUID.randomUUID().toString()
                    val processResult = emoteBridge.convertEmoteToSticker(
                        emote = emote,
                        outputDir = stagingDir,
                        allowStaticFallback = false
                    )

                    val processedSticker = when (processResult) {
                        is StickerResult.Success -> processResult.data
                        is StickerResult.Failure -> {
                            stagingDir.deleteRecursively()
                            return@withContext processResult
                        }
                    }

                    val stickerBytes = processedSticker.file.readBytes()
                    val savedFile = storageManager.saveStickerFile(
                        packIdentifier = packIdentifier,
                        stickerId = stickerId,
                        bytes = stickerBytes
                    )

                    val relativePath = "sticker_packs/$packIdentifier/sticker_${stickerId}.webp"
                    val entity = StickerEntity(
                        id = stickerId,
                        packIdentifier = packIdentifier,
                        emoteId = emote.id,
                        emoteName = emote.name,
                        relativeFilePath = relativePath,
                        emojis = com.seventv.stickers.core.domain.mapper.EmoteEmojiMapper.mapEmoteNameToEmojis(emote.name),
                        fileLength = savedFile.length(),
                        sortOrder = currentCount + index
                    )
                    stickerDao.insertSticker(entity)
                }

                stagingDir.deleteRecursively()
                stickerPackDao.updateWhitelisted(packIdentifier, false)
                StickerResult.Success(Unit)
            } catch (e: Exception) {
                stagingDir.deleteRecursively()
                if (e is CancellationException) throw e
                StickerResult.Failure(StickerError.StorageIoError(e))
            }
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

    override suspend fun syncWhitelistStatus(
        context: Context,
        packIdentifier: String
    ): StickerResult<Boolean> = withContext(dispatcher) {
        try {
            val isWhitelisted = WhatsAppWhitelistChecker.isPackWhitelisted(context, packIdentifier)
            stickerPackDao.updateWhitelisted(packIdentifier, isWhitelisted)
            StickerResult.Success(isWhitelisted)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            StickerResult.Failure(StickerError.StorageIoError("Failed to sync whitelist: ${e.message}"))
        }
    }

    private fun toDomain(packWithStickers: PackWithStickers): StickerPack {
        return StickerPack(
            identifier = packWithStickers.pack.identifier,
            name = packWithStickers.pack.name,
            publisher = packWithStickers.pack.publisher,
            trayImagePath = storageManager.getAbsoluteFile(packWithStickers.pack.trayImageRelativePath).absolutePath,
            isAnimated = packWithStickers.pack.isAnimated,
            isWhitelisted = packWithStickers.pack.isWhitelisted,
            stickers = packWithStickers.stickers.sortedBy { it.sortOrder }.map { entity ->
                PackSticker(
                    id = entity.id,
                    packIdentifier = entity.packIdentifier,
                    emoteId = entity.emoteId,
                    emoteName = entity.emoteName,
                    filePath = storageManager.getAbsoluteFile(entity.relativeFilePath).absolutePath,
                    emojis = entity.emojis,
                    fileLength = entity.fileLength,
                    sortOrder = entity.sortOrder
                )
            },
            createdAt = packWithStickers.pack.createdAt
        )
    }

    private data class ProcessItem(
        val index: Int,
        val emote: SevenTvEmote,
        val stickerId: String,
        val result: StickerResult<com.seventv.stickers.core.media.model.ProcessedSticker>
    )
}

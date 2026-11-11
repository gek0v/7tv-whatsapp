package com.seventv.stickers.data.repository

import android.content.Context
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote
import kotlinx.coroutines.flow.Flow

interface StickerPackRepository {
    fun observeAllPacks(): Flow<List<StickerPack>>
    suspend fun getPack(identifier: String): StickerResult<StickerPack>
    suspend fun createPacksFromEmotes(
        basePackName: String,
        publisher: String,
        emotes: List<SevenTvEmote>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        originSetId: String? = null,
        originStreamerName: String? = null
    ): StickerResult<List<StickerPack>>
    suspend fun deletePack(identifier: String): StickerResult<Unit>
    suspend fun deletePacks(identifiers: List<String>): StickerResult<Unit>
    suspend fun renamePack(identifier: String, newName: String): StickerResult<Unit>
    suspend fun mergePacks(sourcePackIdentifiers: List<String>, basePackName: String): StickerResult<List<StickerPack>>
    suspend fun deleteStickerFromPack(packIdentifier: String, stickerId: String): StickerResult<Unit>
    suspend fun addEmotesToPack(packIdentifier: String, emotes: List<SevenTvEmote>): StickerResult<Unit>
    suspend fun updateStickerEmojis(stickerId: String, emojis: List<String>): StickerResult<Unit>
    suspend fun reorderStickers(packIdentifier: String, orderedStickerIds: List<String>): StickerResult<Unit>
    suspend fun setStickerAsTrayIcon(packIdentifier: String, stickerId: String): StickerResult<Unit>
    suspend fun updatePackWhitelisted(identifier: String, isWhitelisted: Boolean): StickerResult<Unit>
    suspend fun syncWhitelistStatus(context: Context, packIdentifier: String): StickerResult<Boolean>
    suspend fun markPackSynced(packIdentifier: String): StickerResult<Unit> = StickerResult.Success(Unit)
    suspend fun checkForPackUpdates(packIdentifier: String): StickerResult<List<SevenTvEmote>>
}

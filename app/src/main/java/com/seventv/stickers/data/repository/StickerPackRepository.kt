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
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): StickerResult<List<StickerPack>>
    suspend fun deletePack(identifier: String): StickerResult<Unit>
    suspend fun updateStickerEmojis(stickerId: String, emojis: List<String>): StickerResult<Unit>
    suspend fun updatePackWhitelisted(identifier: String, isWhitelisted: Boolean): StickerResult<Unit>
    suspend fun syncWhitelistStatus(context: Context, packIdentifier: String): StickerResult<Boolean>
}

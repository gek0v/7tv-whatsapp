package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote
import com.seventv.stickers.core.network.model.toSevenTvEmote
import com.seventv.stickers.core.network.model.toUnifiedEmote

class SevenTvProviderRepository(
    private val sevenTvRepository: SevenTvRepository
) : EmoteProviderRepository {

    override val provider: EmoteProvider = EmoteProvider.SEVENTV

    override suspend fun searchEmotes(
        query: String,
        page: Int,
        limit: Int
    ): StickerResult<List<UnifiedEmote>> {
        val category = if (query.isBlank()) "TRENDING_DAY" else null
        return when (val result = sevenTvRepository.searchEmotes(query, page, limit, category)) {
            is StickerResult.Success -> StickerResult.Success(result.data.map { it.toUnifiedEmote() })
            is StickerResult.Failure -> result
        }
    }

    override suspend fun getChannelEmotes(channelQuery: String): StickerResult<List<UnifiedEmote>> {
        return when (val result = sevenTvRepository.getChannelData(channelQuery)) {
            is StickerResult.Success -> {
                val owner = result.data.streamerName
                val emotes = result.data.activeSet.emotes.map {
                    it.toUnifiedEmote().copy(originalOwner = owner)
                }
                StickerResult.Success(emotes)
            }
            is StickerResult.Failure -> result
        }
    }

    override suspend fun downloadEmote(emote: UnifiedEmote): StickerResult<DownloadedEmoteAsset> {
        return sevenTvRepository.downloadEmote(emote.toSevenTvEmote())
    }
}

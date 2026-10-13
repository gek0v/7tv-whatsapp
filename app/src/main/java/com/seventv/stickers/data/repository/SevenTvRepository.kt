package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.ChannelEmoteSetSummary
import com.seventv.stickers.core.network.model.ChannelSearchResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
interface SevenTvRepository {
    suspend fun searchEmotes(
        query: String,
        page: Int = 1,
        limit: Int = 30,
        category: String? = null
    ): StickerResult<List<SevenTvEmote>>
    suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote>
    suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet>
    suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset>
    suspend fun downloadEmoteGif(emote: SevenTvEmote): StickerResult<ByteArray> =
        StickerResult.Failure(StickerError.ResourceNotFound)
    suspend fun getChannelData(query: String): StickerResult<ChannelSearchResult> =
        when (val result = getEmoteSet(query)) {
            is StickerResult.Success -> {
                val set = result.data
                val summary = ChannelEmoteSetSummary(
                    id = set.id,
                    name = set.name,
                    emoteCount = set.emotes.size,
                    isActive = true
                )
                StickerResult.Success(
                    ChannelSearchResult(
                        streamerName = set.name,
                        username = query,
                        availableSets = listOf(summary),
                        activeSet = set
                    )
                )
            }
            is StickerResult.Failure -> StickerResult.Failure(result.error)
        }
    suspend fun getStreamerEmoteSet(query: String): StickerResult<SevenTvEmoteSet> =
        when (val result = getChannelData(query)) {
            is StickerResult.Success -> StickerResult.Success(result.data.activeSet)
            is StickerResult.Failure -> StickerResult.Failure(result.error)
        }
}

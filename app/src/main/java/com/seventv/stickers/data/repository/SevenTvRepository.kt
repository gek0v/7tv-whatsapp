package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet

interface SevenTvRepository {
    suspend fun searchEmotes(query: String, page: Int = 1, limit: Int = 30): StickerResult<List<SevenTvEmote>>
    suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote>
    suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet>
    suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset>
}

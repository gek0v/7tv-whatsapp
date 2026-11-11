package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote

interface EmoteProviderRepository {
    val provider: EmoteProvider
    suspend fun searchEmotes(query: String, page: Int = 1, limit: Int = 100): StickerResult<List<UnifiedEmote>>
    suspend fun getChannelEmotes(channelQuery: String): StickerResult<List<UnifiedEmote>>
    suspend fun downloadEmote(emote: UnifiedEmote): StickerResult<DownloadedEmoteAsset>
}

package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.ChannelEmoteSetSummary
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote

interface AggregatedEmoteCatalogRepository {
    suspend fun searchAllProviders(
        query: String,
        page: Int = 1,
        limit: Int = 100
    ): StickerResult<Map<EmoteProvider, List<UnifiedEmote>>>

    suspend fun getUnifiedChannelEmotes(twitchChannel: String): StickerResult<UnifiedChannelResult>

    suspend fun downloadEmote(emote: UnifiedEmote): StickerResult<DownloadedEmoteAsset>
}

data class UnifiedChannelResult(
    val channelName: String,
    val avatarUrl: String?,
    val emotesByProvider: Map<EmoteProvider, List<UnifiedEmote>>,
    val totalEmotesCount: Int,
    val sevenTvSets: List<ChannelEmoteSetSummary> = emptyList(),
    val activeSevenTvSetId: String? = null,
    val failedProviders: Set<EmoteProvider> = emptySet()
)

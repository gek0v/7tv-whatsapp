package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.ChannelEmoteSetSummary
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AggregatedEmoteCatalogRepository {
    suspend fun searchAllProviders(
        query: String,
        page: Int = 1,
        limit: Int = 100
    ): StickerResult<Map<EmoteProvider, List<UnifiedEmote>>>

    fun searchAllProvidersProgressive(
        query: String,
        page: Int = 1,
        limit: Int = 100,
        targetProviders: Set<EmoteProvider>? = null
    ): Flow<StickerResult<Map<EmoteProvider, List<UnifiedEmote>>>> = flow {
        val res = searchAllProviders(query, page, limit)
        if (targetProviders.isNullOrEmpty() || res !is StickerResult.Success) {
            emit(res)
        } else {
            emit(StickerResult.Success(res.data.filterKeys { it in targetProviders }))
        }
    }

    suspend fun prefetchSearchPage(
        query: String,
        page: Int,
        limit: Int = 100
    ) {
        // Default no-op for test doubles
    }

    suspend fun getUnifiedChannelEmotes(twitchChannel: String): StickerResult<UnifiedChannelResult>

    fun getUnifiedChannelEmotesProgressive(
        twitchChannel: String
    ): Flow<StickerResult<UnifiedChannelResult>> = flow {
        emit(getUnifiedChannelEmotes(twitchChannel))
    }

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

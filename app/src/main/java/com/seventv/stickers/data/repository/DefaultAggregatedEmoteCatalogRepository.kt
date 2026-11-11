package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.model.ChannelEmoteSetSummary
import com.seventv.stickers.core.network.model.ChannelSearchResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote
import com.seventv.stickers.core.network.model.toUnifiedEmote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class DefaultAggregatedEmoteCatalogRepository(
    private val providers: List<EmoteProviderRepository>,
    private val sevenTvRepository: SevenTvRepository? = null,
    private val downloader: EmoteDownloader? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    clockMs: () -> Long = { System.currentTimeMillis() }
) : AggregatedEmoteCatalogRepository {

    private data class SearchCacheKey(
        val query: String,
        val page: Int,
        val limit: Int
    )

    private val searchCache = TtlLruCache<SearchCacheKey, Map<EmoteProvider, List<UnifiedEmote>>>(
        maxEntries = 64,
        ttlMs = cacheTtlMs,
        clockMs = clockMs
    )

    private val channelCache = TtlLruCache<String, UnifiedChannelResult>(
        maxEntries = 32,
        ttlMs = cacheTtlMs,
        clockMs = clockMs
    )

    override suspend fun searchAllProviders(
        query: String,
        page: Int,
        limit: Int
    ): StickerResult<Map<EmoteProvider, List<UnifiedEmote>>> = withContext(dispatcher) {
        if (providers.isEmpty()) {
            return@withContext StickerResult.Failure(StickerError.ResourceNotFound)
        }

        val cacheKey = SearchCacheKey(query = query.trim().lowercase(), page = page, limit = limit)
        searchCache.get(cacheKey)?.let { cached ->
            return@withContext StickerResult.Success(cached)
        }

        val outcomes = coroutineScope {
            providers.map { repo ->
                async(dispatcher) {
                    val res = try {
                        repo.searchEmotes(query = query, page = page, limit = limit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        StickerResult.Failure(StickerError.NetworkError(message = e.message ?: "Provider error"))
                    }
                    repo.provider to res
                }
            }.awaitAll()
        }

        val successMap = linkedMapOf<EmoteProvider, List<UnifiedEmote>>()
        var firstFailure: StickerResult.Failure? = null

        for ((provider, result) in outcomes) {
            when (result) {
                is StickerResult.Success -> successMap[provider] = result.data
                is StickerResult.Failure -> if (firstFailure == null) firstFailure = result
            }
        }

        if (successMap.isNotEmpty()) {
            searchCache.put(cacheKey, successMap)
            StickerResult.Success(successMap)
        } else {
            firstFailure ?: StickerResult.Failure(StickerError.ResourceNotFound)
        }
    }

    override suspend fun getUnifiedChannelEmotes(
        twitchChannel: String
    ): StickerResult<UnifiedChannelResult> = withContext(dispatcher) {
        val trimmed = twitchChannel.trim()
        if (trimmed.isBlank()) {
            return@withContext StickerResult.Failure(StickerError.ResourceNotFound)
        }
        if (providers.isEmpty() && sevenTvRepository == null) {
            return@withContext StickerResult.Failure(StickerError.ResourceNotFound)
        }

        val cacheKey = trimmed.lowercase()
        channelCache.get(cacheKey)?.let { cached ->
            return@withContext StickerResult.Success(cached)
        }

        val activeProviders = if (sevenTvRepository != null) {
            providers.filterNot { it.provider == EmoteProvider.SEVENTV }
        } else {
            providers
        }

        var sevenTvChannelMeta: ChannelSearchResult? = null
        val outcomes = coroutineScope {
            val sevenTvDeferred = if (sevenTvRepository != null) {
                async(dispatcher) {
                    val res = try {
                        sevenTvRepository.getChannelData(trimmed)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        StickerResult.Failure(StickerError.NetworkError(message = e.message ?: "7TV error"))
                    }
                    when (res) {
                        is StickerResult.Success -> {
                            sevenTvChannelMeta = res.data
                            val owner = res.data.streamerName
                            val unified = res.data.activeSet.emotes.map {
                                it.toUnifiedEmote().copy(originalOwner = owner)
                            }
                            EmoteProvider.SEVENTV to StickerResult.Success(unified)
                        }
                        is StickerResult.Failure -> EmoteProvider.SEVENTV to res
                    }
                }
            } else null

            val otherDeferreds = activeProviders.map { repo ->
                async(dispatcher) {
                    val res = try {
                        repo.getChannelEmotes(trimmed)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        StickerResult.Failure(StickerError.NetworkError(message = e.message ?: "Provider error"))
                    }
                    repo.provider to res
                }
            }

            buildList {
                sevenTvDeferred?.let { add(it) }
                addAll(otherDeferreds)
            }.awaitAll()
        }

        val emotesByProvider = linkedMapOf<EmoteProvider, List<UnifiedEmote>>()
        val failedProviders = mutableSetOf<EmoteProvider>()
        var firstFailure: StickerResult.Failure? = null
        var anySuccess = false

        for ((provider, result) in outcomes) {
            when (result) {
                is StickerResult.Success -> {
                    anySuccess = true
                    if (result.data.isNotEmpty()) {
                        emotesByProvider[provider] = result.data.distinctBy { it.id }
                    }
                }
                is StickerResult.Failure -> {
                    failedProviders.add(provider)
                    if (firstFailure == null) firstFailure = result
                }
            }
        }

        val totalCount = emotesByProvider.values.sumOf { it.size }
        if (!anySuccess || (totalCount == 0 && failedProviders.isNotEmpty())) {
            return@withContext firstFailure ?: StickerResult.Failure(StickerError.ResourceNotFound)
        }

        val inferredOwner = sevenTvChannelMeta?.streamerName
            ?: emotesByProvider.values.flatten().firstNotNullOfOrNull { it.originalOwner }
            ?: trimmed

        val sevenTvSets: List<ChannelEmoteSetSummary> = sevenTvChannelMeta?.availableSets.orEmpty()
        val activeSetId: String? = sevenTvChannelMeta?.activeSet?.id

        val channelResult = UnifiedChannelResult(
            channelName = inferredOwner,
            avatarUrl = sevenTvChannelMeta?.avatarUrl,
            emotesByProvider = emotesByProvider,
            totalEmotesCount = totalCount,
            sevenTvSets = sevenTvSets,
            activeSevenTvSetId = activeSetId,
            failedProviders = failedProviders
        )
        channelCache.put(cacheKey, channelResult)
        StickerResult.Success(channelResult)
    }

    override suspend fun downloadEmote(emote: UnifiedEmote): StickerResult<DownloadedEmoteAsset> {
        val providerRepo = providers.firstOrNull { it.provider == emote.provider }
        if (providerRepo != null) {
            return providerRepo.downloadEmote(emote)
        }
        if (downloader != null) {
            return downloader.downloadEmote(emote)
        }
        return StickerResult.Failure(StickerError.ResourceNotFound)
    }

    fun clearCaches() {
        searchCache.clear()
        channelCache.clear()
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MS = 2 * 60_000L
    }
}

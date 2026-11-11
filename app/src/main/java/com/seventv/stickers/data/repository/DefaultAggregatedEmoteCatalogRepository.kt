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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class DefaultAggregatedEmoteCatalogRepository(
    private val providers: List<EmoteProviderRepository>,
    private val sevenTvRepository: SevenTvRepository? = null,
    private val downloader: EmoteDownloader? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val providerTimeoutMs: Long = DEFAULT_PROVIDER_TIMEOUT_MS,
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
                    val res = executeWithTimeout {
                        repo.searchEmotes(query = query, page = page, limit = limit)
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

    override fun searchAllProvidersProgressive(
        query: String,
        page: Int,
        limit: Int,
        targetProviders: Set<EmoteProvider>?
    ): Flow<StickerResult<Map<EmoteProvider, List<UnifiedEmote>>>> = channelFlow {
        val selectedRepos = if (targetProviders.isNullOrEmpty()) {
            providers
        } else {
            providers.filter { it.provider in targetProviders }
        }

        if (selectedRepos.isEmpty()) {
            send(StickerResult.Failure(StickerError.ResourceNotFound))
            return@channelFlow
        }

        val cacheKey = SearchCacheKey(query = query.trim().lowercase(), page = page, limit = limit)
        searchCache.get(cacheKey)?.let { cached ->
            val filtered = if (targetProviders.isNullOrEmpty()) {
                cached
            } else {
                cached.filterKeys { it in targetProviders }
            }
            if (filtered.isNotEmpty()) {
                send(StickerResult.Success(filtered))
                return@channelFlow
            }
        }

        val lock = Any()
        val accumulated = linkedMapOf<EmoteProvider, List<UnifiedEmote>>()
        var firstFailure: StickerResult.Failure? = null
        var emittedAny = false

        val jobs = selectedRepos.map { repo ->
            launch(dispatcher) {
                val res = executeWithTimeout {
                    repo.searchEmotes(query = query, page = page, limit = limit)
                }
                val snapshotToEmit: Map<EmoteProvider, List<UnifiedEmote>>? = synchronized(lock) {
                    when (res) {
                        is StickerResult.Success -> {
                            accumulated[repo.provider] = res.data
                            if (res.data.isNotEmpty()) {
                                emittedAny = true
                                LinkedHashMap(accumulated)
                            } else {
                                null
                            }
                        }
                        is StickerResult.Failure -> {
                            if (firstFailure == null) firstFailure = res
                            null
                        }
                    }
                }
                if (snapshotToEmit != null) {
                    send(StickerResult.Success(snapshotToEmit))
                }
            }
        }

        jobs.joinAll()

        val finalSnapshot: Map<EmoteProvider, List<UnifiedEmote>>
        val failureToEmit: StickerResult.Failure?
        val needFinalEmit: Boolean
        synchronized(lock) {
            finalSnapshot = LinkedHashMap(accumulated)
            failureToEmit = firstFailure
            needFinalEmit = !emittedAny && finalSnapshot.isNotEmpty()
        }

        if (finalSnapshot.isNotEmpty()) {
            if (targetProviders.isNullOrEmpty()) {
                searchCache.put(cacheKey, finalSnapshot)
            }
            if (needFinalEmit) {
                send(StickerResult.Success(finalSnapshot))
            }
        } else {
            send(failureToEmit ?: StickerResult.Failure(StickerError.ResourceNotFound))
        }
    }.flowOn(dispatcher)

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
                    val res = executeWithTimeout {
                        sevenTvRepository.getChannelData(trimmed)
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
                    val res = executeWithTimeout {
                        repo.getChannelEmotes(trimmed)
                    }
                    repo.provider to res
                }
            }

            buildList {
                sevenTvDeferred?.let { add(it) }
                addAll(otherDeferreds)
            }.awaitAll()
        }

        val result = buildUnifiedChannelResult(trimmed, outcomes, sevenTvChannelMeta)
        if (result is StickerResult.Success) {
            channelCache.put(cacheKey, result.data)
        }
        result
    }

    override fun getUnifiedChannelEmotesProgressive(
        twitchChannel: String
    ): Flow<StickerResult<UnifiedChannelResult>> = channelFlow {
        val trimmed = twitchChannel.trim()
        if (trimmed.isBlank() || (providers.isEmpty() && sevenTvRepository == null)) {
            send(StickerResult.Failure(StickerError.ResourceNotFound))
            return@channelFlow
        }

        val cacheKey = trimmed.lowercase()
        channelCache.get(cacheKey)?.let { cached ->
            send(StickerResult.Success(cached))
            return@channelFlow
        }

        val activeProviders = if (sevenTvRepository != null) {
            providers.filterNot { it.provider == EmoteProvider.SEVENTV }
        } else {
            providers
        }

        val lock = Any()
        var sevenTvChannelMeta: ChannelSearchResult? = null
        val emotesByProvider = linkedMapOf<EmoteProvider, List<UnifiedEmote>>()
        val failedProviders = mutableSetOf<EmoteProvider>()
        var firstFailure: StickerResult.Failure? = null
        var anySuccess = false
        var emittedNonEmpty = false

        val jobs = buildList {
            if (sevenTvRepository != null) {
                add(launch(dispatcher) {
                    val res = executeWithTimeout {
                        sevenTvRepository.getChannelData(trimmed)
                    }
                    val snapshot: UnifiedChannelResult? = synchronized(lock) {
                        when (res) {
                            is StickerResult.Success -> {
                                anySuccess = true
                                sevenTvChannelMeta = res.data
                                val owner = res.data.streamerName
                                val unified = res.data.activeSet.emotes
                                    .map { it.toUnifiedEmote().copy(originalOwner = owner) }
                                    .distinctBy { it.id }
                                if (unified.isNotEmpty()) {
                                    emotesByProvider[EmoteProvider.SEVENTV] = unified
                                    emittedNonEmpty = true
                                    snapshotChannelResult(trimmed, emotesByProvider, failedProviders, sevenTvChannelMeta)
                                } else {
                                    null
                                }
                            }
                            is StickerResult.Failure -> {
                                failedProviders.add(EmoteProvider.SEVENTV)
                                if (firstFailure == null) firstFailure = res
                                null
                            }
                        }
                    }
                    if (snapshot != null) {
                        send(StickerResult.Success(snapshot))
                    }
                })
            }

            activeProviders.forEach { repo ->
                add(launch(dispatcher) {
                    val res = executeWithTimeout {
                        repo.getChannelEmotes(trimmed)
                    }
                    val snapshot: UnifiedChannelResult? = synchronized(lock) {
                        when (res) {
                            is StickerResult.Success -> {
                                anySuccess = true
                                val deduped = res.data.distinctBy { it.id }
                                if (deduped.isNotEmpty()) {
                                    emotesByProvider[repo.provider] = deduped
                                    emittedNonEmpty = true
                                    snapshotChannelResult(trimmed, emotesByProvider, failedProviders, sevenTvChannelMeta)
                                } else {
                                    null
                                }
                            }
                            is StickerResult.Failure -> {
                                failedProviders.add(repo.provider)
                                if (firstFailure == null) firstFailure = res
                                null
                            }
                        }
                    }
                    if (snapshot != null) {
                        send(StickerResult.Success(snapshot))
                    }
                })
            }
        }

        jobs.joinAll()

        val finalSnapshot: UnifiedChannelResult?
        val failureToEmit: StickerResult.Failure?
        val totalCount: Int
        val hasFailures: Boolean
        val hadAnySuccess: Boolean
        synchronized(lock) {
            totalCount = emotesByProvider.values.sumOf { it.size }
            hasFailures = failedProviders.isNotEmpty()
            hadAnySuccess = anySuccess
            failureToEmit = firstFailure
            finalSnapshot = if (hadAnySuccess && !(totalCount == 0 && hasFailures)) {
                snapshotChannelResult(trimmed, emotesByProvider, failedProviders, sevenTvChannelMeta)
            } else {
                null
            }
        }

        if (finalSnapshot != null) {
            channelCache.put(cacheKey, finalSnapshot)
            if (!emittedNonEmpty || hasFailures) {
                send(StickerResult.Success(finalSnapshot))
            }
        } else {
            send(failureToEmit ?: StickerResult.Failure(StickerError.ResourceNotFound))
        }
    }.flowOn(dispatcher)

    private fun snapshotChannelResult(
        trimmed: String,
        emotesByProvider: Map<EmoteProvider, List<UnifiedEmote>>,
        failedProviders: Set<EmoteProvider>,
        sevenTvChannelMeta: ChannelSearchResult?
    ): UnifiedChannelResult {
        val copyMap = linkedMapOf<EmoteProvider, List<UnifiedEmote>>()
        copyMap.putAll(emotesByProvider)
        val totalCount = copyMap.values.sumOf { it.size }
        val inferredOwner = sevenTvChannelMeta?.streamerName
            ?: copyMap.values.firstNotNullOfOrNull { list -> list.firstNotNullOfOrNull { it.originalOwner } }
            ?: trimmed
        return UnifiedChannelResult(
            channelName = inferredOwner,
            avatarUrl = sevenTvChannelMeta?.avatarUrl,
            emotesByProvider = copyMap,
            totalEmotesCount = totalCount,
            sevenTvSets = sevenTvChannelMeta?.availableSets.orEmpty(),
            activeSevenTvSetId = sevenTvChannelMeta?.activeSet?.id,
            failedProviders = failedProviders.toSet()
        )
    }

    private fun buildUnifiedChannelResult(
        trimmed: String,
        outcomes: List<Pair<EmoteProvider, StickerResult<List<UnifiedEmote>>>>,
        sevenTvChannelMeta: ChannelSearchResult?
    ): StickerResult<UnifiedChannelResult> {
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
            return firstFailure ?: StickerResult.Failure(StickerError.ResourceNotFound)
        }

        return StickerResult.Success(
            snapshotChannelResult(trimmed, emotesByProvider, failedProviders, sevenTvChannelMeta)
        )
    }

    private suspend inline fun <T> executeWithTimeout(
        crossinline block: suspend () -> StickerResult<T>
    ): StickerResult<T> {
        return try {
            withTimeoutOrNull(providerTimeoutMs) {
                block()
            } ?: StickerResult.Failure(StickerError.NetworkError(message = "Provider request timed out"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.NetworkError(message = e.message ?: "Provider error"))
        }
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
        const val DEFAULT_PROVIDER_TIMEOUT_MS = 4_500L
    }
}

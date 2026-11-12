package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.api.SevenTvApi
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.dto.SevenTvGqlQueries
import com.seventv.stickers.core.network.mapper.SevenTvMapper
import com.seventv.stickers.core.network.model.ChannelEmoteSetSummary
import com.seventv.stickers.core.network.model.ChannelSearchResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.Response

class DefaultSevenTvRepository(
    private val api: SevenTvApi,
    private val downloader: EmoteDownloader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    clockMs: () -> Long = { System.currentTimeMillis() }
) : SevenTvRepository {

    private val emoteSetCache = TtlLruCache<String, SevenTvEmoteSet>(
        maxEntries = 32,
        ttlMs = cacheTtlMs,
        clockMs = clockMs
    )

    private val channelDataCache = TtlLruCache<String, ChannelSearchResult>(
        maxEntries = 32,
        ttlMs = cacheTtlMs,
        clockMs = clockMs
    )

    override suspend fun searchEmotes(
        query: String,
        page: Int,
        limit: Int,
        category: String?
    ): StickerResult<List<SevenTvEmote>> = safeApiCall(
        call = { api.searchEmotes(SevenTvGqlQueries.searchEmotesRequest(query, page, limit, category)) },
        transform = { gqlResponse ->
            val items = gqlResponse.data?.emotes?.items.orEmpty()
            items.map { SevenTvMapper.toDomain(it) }
        }
    )

    override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> = safeApiCall(
        call = { api.getEmote(emoteId) },
        transform = { SevenTvMapper.toDomain(it) }
    )

    override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> {
        val trimmedId = setId.trim()
        if (trimmedId.isBlank()) return StickerResult.Failure(StickerError.ResourceNotFound)

        emoteSetCache.get(trimmedId)?.let { return StickerResult.Success(it) }

        val result = safeApiCall(
            call = { api.getEmoteSet(trimmedId) },
            transform = { SevenTvMapper.toDomain(it) }
        )
        if (result is StickerResult.Success) {
            emoteSetCache.put(trimmedId, result.data)
        }
        return result
    }

    override suspend fun getChannelData(query: String): StickerResult<ChannelSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }

        val cacheKey = trimmed.lowercase()
        channelDataCache.get(cacheKey)?.let { return StickerResult.Success(it) }

        // Concurrently query direct emote set and user search via coroutineScope
        val (directSetResult, userSearchCall) = coroutineScope {
            val directDeferred = async(dispatcher) { getEmoteSet(trimmed) }
            val userDeferred = async(dispatcher) {
                safeApiCall(
                    call = { api.searchUsers(SevenTvGqlQueries.searchUsersRequest(trimmed, limit = 15)) },
                    transform = { gqlResponse -> gqlResponse.data?.users.orEmpty() }
                )
            }
            val direct = directDeferred.await()
            if (direct is StickerResult.Success) {
                userDeferred.cancel()
                direct to null
            } else {
                direct to userDeferred.await()
            }
        }

        // 1. If direct set lookup succeeded, return it immediately
        if (directSetResult is StickerResult.Success) {
            val set = directSetResult.data
            val summary = ChannelEmoteSetSummary(
                id = set.id,
                name = set.name.ifBlank { "Emote Set" },
                emoteCount = set.emotes.size,
                isActive = true
            )
            val result = ChannelSearchResult(
                streamerName = set.name.ifBlank { set.ownerName ?: trimmed },
                username = set.ownerName ?: trimmed,
                avatarUrl = null,
                availableSets = listOf(summary),
                activeSet = set
            )
            channelDataCache.put(cacheKey, result)
            return StickerResult.Success(result)
        }

        // 2. Process user search results (which was already fetched in parallel!)
        if (userSearchCall is StickerResult.Success) {
            val users = userSearchCall.data
            if (users.isNotEmpty()) {
                val targetUser = users.firstOrNull { it.username.equals(trimmed, ignoreCase = true) }
                    ?: users.firstOrNull { it.displayName?.equals(trimmed, ignoreCase = true) == true }
                    ?: users.firstOrNull { it.username.contains(trimmed, ignoreCase = true) }
                    ?: users.firstOrNull { it.displayName?.contains(trimmed, ignoreCase = true) == true }
                    ?: users.firstOrNull()!!

                val displayName = targetUser.displayName?.ifBlank { null } ?: targetUser.username
                val rawSets = targetUser.emoteSets

                // Filter non-empty sets if any have emotes, otherwise keep candidate sets
                val nonZeroSets = rawSets.filter { it.emoteCount > 0 }
                val candidateSets = if (nonZeroSets.isNotEmpty()) nonZeroSets else rawSets

                if (candidateSets.isNotEmpty()) {
                    val connectionSetId = targetUser.connections
                        .firstNotNullOfOrNull { it.emoteSetId?.ifBlank { null } }

                    val primarySetRef = candidateSets.firstOrNull { it.id == connectionSetId }
                        ?: candidateSets.maxByOrNull { it.emoteCount }
                        ?: candidateSets.first()

                    val userSetResult = getEmoteSet(primarySetRef.id)
                    if (userSetResult is StickerResult.Success) {
                        val activeEmoteSet = userSetResult.data.copy(
                            name = primarySetRef.name.ifBlank { displayName },
                            ownerName = displayName
                        )

                        val summaries = candidateSets.map { ref ->
                            ChannelEmoteSetSummary(
                                id = ref.id,
                                name = ref.name.ifBlank { displayName },
                                emoteCount = if (ref.id == activeEmoteSet.id) activeEmoteSet.emotes.size else ref.emoteCount,
                                isActive = (ref.id == primarySetRef.id)
                            )
                        }

                        val result = ChannelSearchResult(
                            streamerName = displayName,
                            username = targetUser.username,
                            avatarUrl = targetUser.avatarUrl,
                            availableSets = summaries,
                            activeSet = activeEmoteSet
                        )
                        channelDataCache.put(cacheKey, result)
                        return StickerResult.Success(result)
                    }
                }
            }
        }

        // 3. Fallback: search emotes matching the channel name and synthesize a set
        val searchResult = searchEmotes(trimmed, limit = 100)
        if (searchResult is StickerResult.Success) {
            val emotes = searchResult.data
            if (emotes.isNotEmpty()) {
                val set = SevenTvEmoteSet(
                    id = trimmed,
                    name = trimmed,
                    ownerName = trimmed,
                    emotes = emotes
                )
                val summary = ChannelEmoteSetSummary(
                    id = trimmed,
                    name = trimmed,
                    emoteCount = emotes.size,
                    isActive = true
                )
                val result = ChannelSearchResult(
                    streamerName = trimmed,
                    username = trimmed,
                    avatarUrl = null,
                    availableSets = listOf(summary),
                    activeSet = set
                )
                channelDataCache.put(cacheKey, result)
                return StickerResult.Success(result)
            }
        }

        return StickerResult.Failure(StickerError.ResourceNotFound)
    }

    override suspend fun getStreamerEmoteSet(query: String): StickerResult<SevenTvEmoteSet> =
        when (val result = getChannelData(query)) {
            is StickerResult.Success -> StickerResult.Success(result.data.activeSet)
            is StickerResult.Failure -> StickerResult.Failure(result.error)
        }

    override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> =
        withContext(dispatcher) {
            try {
                downloader.downloadEmote(emote)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StickerResult.Failure(StickerError.NetworkError(message = e.message ?: "Download failed"))
            }
        }

    override suspend fun downloadEmoteGif(emote: SevenTvEmote): StickerResult<ByteArray> =
        withContext(dispatcher) {
            val targetUrl = emote.gifUrl ?: if (emote.provider == com.seventv.stickers.core.network.model.EmoteProvider.SEVENTV && emote.isAnimated && emote.bestAssetUrl.isNotBlank()) {
                val base = emote.bestAssetUrl.substringBeforeLast('/')
                "$base/4x.gif"
            } else null

            if (targetUrl.isNullOrBlank()) {
                return@withContext StickerResult.Failure(StickerError.ResourceNotFound)
            }

            try {
                downloader.downloadFromUrl(targetUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StickerResult.Failure(StickerError.NetworkError(message = e.message ?: "GIF download failed"))
            }
        }

    private suspend fun <T, R> safeApiCall(
        call: suspend () -> Response<T>,
        transform: (T) -> R
    ): StickerResult<R> = withContext(dispatcher) {
        try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    StickerResult.Failure(
                        StickerError.NetworkError(
                            statusCode = response.code(),
                            message = "Empty response body"
                        )
                    )
                } else {
                    StickerResult.Success(transform(body))
                }
            } else {
                when (response.code()) {
                    404 -> StickerResult.Failure(StickerError.ResourceNotFound)
                    429 -> StickerResult.Failure(StickerError.RateLimited)
                    else -> StickerResult.Failure(
                        StickerError.NetworkError(
                            statusCode = response.code(),
                            message = response.message().ifBlank { "HTTP ${response.code()}" }
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(
                StickerError.NetworkError(message = e.message ?: "Network error")
            )
        }
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MS = 5 * 60_000L
    }
}

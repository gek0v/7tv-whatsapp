package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.api.TwitchEmotesApi
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.mapper.TwitchMapper
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response

class TwitchProviderRepository(
    private val twitchApi: TwitchEmotesApi,
    private val downloader: EmoteDownloader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val globalCacheTtlMs: Long = DEFAULT_GLOBAL_CACHE_TTL_MS,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : EmoteProviderRepository {

    override val provider: EmoteProvider = EmoteProvider.TWITCH

    private val globalCacheMutex = Mutex()
    @Volatile
    private var cachedGlobalEmotes: List<UnifiedEmote>? = null
    @Volatile
    private var cachedGlobalExpiresAtMs: Long = 0L

    override suspend fun searchEmotes(
        query: String,
        page: Int,
        limit: Int
    ): StickerResult<List<UnifiedEmote>> = withContext(dispatcher) {
        val trimmed = query.trim()
        val safeLimit = limit.coerceAtLeast(1)
        val offset = ((page - 1).coerceAtLeast(0)) * safeLimit

        val globalResult = getOrFetchGlobalEmotes()
        when (globalResult) {
            is StickerResult.Failure -> globalResult
            is StickerResult.Success -> {
                val all = globalResult.data
                val paged = if (trimmed.isBlank()) {
                    if (offset >= all.size) emptyList()
                    else all.subList(offset, (offset + safeLimit).coerceAtMost(all.size))
                } else {
                    val result = ArrayList<UnifiedEmote>(safeLimit.coerceAtMost(all.size))
                    var matched = 0
                    for (emote in all) {
                        if (emote.name.contains(trimmed, ignoreCase = true)) {
                            if (matched >= offset) {
                                result.add(emote)
                                if (result.size >= safeLimit) break
                            }
                            matched++
                        }
                    }
                    result
                }
                StickerResult.Success(paged)
            }
        }
    }

    private suspend fun getOrFetchGlobalEmotes(): StickerResult<List<UnifiedEmote>> {
        val now = clockMs()
        val snapshot = cachedGlobalEmotes
        if (snapshot != null && now < cachedGlobalExpiresAtMs) {
            return StickerResult.Success(snapshot)
        }

        return globalCacheMutex.withLock {
            val recheckNow = clockMs()
            val recheckSnapshot = cachedGlobalEmotes
            if (recheckSnapshot != null && recheckNow < cachedGlobalExpiresAtMs) {
                return@withLock StickerResult.Success(recheckSnapshot)
            }

            val fetched = safeApiCall(
                call = { twitchApi.getGlobalEmotes() },
                transform = { sets -> TwitchMapper.toGlobalEmotes(sets) }
            )
            if (fetched is StickerResult.Success && globalCacheTtlMs > 0L) {
                cachedGlobalEmotes = fetched.data
                cachedGlobalExpiresAtMs = clockMs() + globalCacheTtlMs
            }
            fetched
        }
    }

    override suspend fun getChannelEmotes(channelQuery: String): StickerResult<List<UnifiedEmote>> =
        withContext(dispatcher) {
            val trimmed = channelQuery.trim()
            if (trimmed.isBlank()) {
                return@withContext StickerResult.Failure(StickerError.ResourceNotFound)
            }

            safeApiCall(
                call = { twitchApi.getChannelEmotes(trimmed.lowercase()) },
                transform = { body -> TwitchMapper.toChannelEmotes(body) }
            )
        }

    override suspend fun downloadEmote(emote: UnifiedEmote): StickerResult<DownloadedEmoteAsset> {
        return downloader.downloadEmote(emote)
    }

    private suspend fun <T, R> safeApiCall(
        call: suspend () -> Response<T>,
        transform: (T) -> R
    ): StickerResult<R> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return StickerResult.Failure(
                        StickerError.NetworkError(
                            statusCode = response.code(),
                            message = "Empty Twitch response body"
                        )
                    )
                StickerResult.Success(transform(body))
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
            StickerResult.Failure(StickerError.NetworkError(message = e.message ?: "Twitch network error"))
        }
    }

    companion object {
        const val DEFAULT_GLOBAL_CACHE_TTL_MS = 5 * 60_000L
    }
}

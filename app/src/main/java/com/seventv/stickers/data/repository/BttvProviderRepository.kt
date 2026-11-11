package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.api.BttvApi
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.dto.BttvEmoteDto
import com.seventv.stickers.core.network.mapper.BttvMapper
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

class BttvProviderRepository(
    private val bttvApi: BttvApi,
    private val downloader: EmoteDownloader,
    private val twitchIdResolver: suspend (String) -> String? = { query ->
        if (query.all { it.isDigit() } && query.isNotEmpty()) query else null
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val globalCacheTtlMs: Long = DEFAULT_GLOBAL_CACHE_TTL_MS,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : EmoteProviderRepository {

    override val provider: EmoteProvider = EmoteProvider.BETTERTTV

    private val globalCacheMutex = Mutex()
    @Volatile
    private var cachedGlobalDtos: List<BttvEmoteDto>? = null
    @Volatile
    private var cachedGlobalExpiresAtMs: Long = 0L

    override suspend fun searchEmotes(
        query: String,
        page: Int,
        limit: Int
    ): StickerResult<List<UnifiedEmote>> = withContext(dispatcher) {
        val trimmed = query.trim()
        val safeLimit = limit.coerceIn(1, 100)
        val offset = ((page - 1).coerceAtLeast(0)) * safeLimit
        val queryOffset = offset.takeIf { it > 0 }

        if (trimmed.isBlank()) {
            return@withContext safeApiCall(
                call = { bttvApi.getTrendingEmotes(offset = queryOffset, limit = safeLimit) },
                transform = { list -> list.map { BttvMapper.toDomain(it) } }
            )
        }

        if (trimmed.length < 3) {
            val globalResult = getOrFetchGlobalDtos()
            return@withContext when (globalResult) {
                is StickerResult.Failure -> globalResult
                is StickerResult.Success -> {
                    val list = globalResult.data
                    val filtered = ArrayList<UnifiedEmote>(safeLimit.coerceAtMost(list.size))
                    var matched = 0
                    for (i in list.indices) {
                        val dto = list[i]
                        if (dto.code.contains(trimmed, ignoreCase = true)) {
                            if (matched >= offset) {
                                filtered.add(BttvMapper.toDomain(dto))
                                if (filtered.size >= safeLimit) break
                            }
                            matched++
                        }
                    }
                    StickerResult.Success(filtered)
                }
            }
        }

        safeApiCall(
            call = { bttvApi.searchEmotes(query = trimmed, offset = queryOffset, limit = safeLimit) },
            transform = { list -> list.map { BttvMapper.toDomain(it) } }
        )
    }

    private suspend fun getOrFetchGlobalDtos(): StickerResult<List<BttvEmoteDto>> {
        val now = clockMs()
        val snapshot = cachedGlobalDtos
        if (snapshot != null && now < cachedGlobalExpiresAtMs) {
            return StickerResult.Success(snapshot)
        }

        return globalCacheMutex.withLock {
            val recheckNow = clockMs()
            val recheckSnapshot = cachedGlobalDtos
            if (recheckSnapshot != null && recheckNow < cachedGlobalExpiresAtMs) {
                return@withLock StickerResult.Success(recheckSnapshot)
            }

            val fetched = safeApiCall(
                call = { bttvApi.getGlobalEmotes() },
                transform = { it }
            )
            if (fetched is StickerResult.Success && globalCacheTtlMs > 0L) {
                cachedGlobalDtos = fetched.data
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

            val twitchId = if (trimmed.all { it.isDigit() }) {
                trimmed
            } else {
                try {
                    twitchIdResolver(trimmed)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }

            if (twitchId.isNullOrBlank()) {
                return@withContext StickerResult.Failure(StickerError.ResourceNotFound)
            }

            safeApiCall(
                call = { bttvApi.getUserByTwitchId(twitchId) },
                transform = { body -> BttvMapper.toChannelEmotes(body, trimmed) }
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
                            message = "Empty BTTV response body"
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
            StickerResult.Failure(StickerError.NetworkError(message = e.message ?: "BTTV network error"))
        }
    }

    companion object {
        const val DEFAULT_GLOBAL_CACHE_TTL_MS = 5 * 60_000L
    }
}

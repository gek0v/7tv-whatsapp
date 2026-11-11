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
import kotlinx.coroutines.withContext
import retrofit2.Response

class TwitchProviderRepository(
    private val twitchApi: TwitchEmotesApi,
    private val downloader: EmoteDownloader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : EmoteProviderRepository {

    override val provider: EmoteProvider = EmoteProvider.TWITCH

    override suspend fun searchEmotes(
        query: String,
        page: Int,
        limit: Int
    ): StickerResult<List<UnifiedEmote>> = withContext(dispatcher) {
        val trimmed = query.trim()
        val safeLimit = limit.coerceAtLeast(1)
        val offset = ((page - 1).coerceAtLeast(0)) * safeLimit

        safeApiCall(
            call = { twitchApi.getGlobalEmotes() },
            transform = { sets ->
                val all = TwitchMapper.toGlobalEmotes(sets)
                val filtered = if (trimmed.isBlank()) {
                    all
                } else {
                    all.filter { it.name.contains(trimmed, ignoreCase = true) }
                }
                filtered.drop(offset).take(safeLimit)
            }
        )
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
}

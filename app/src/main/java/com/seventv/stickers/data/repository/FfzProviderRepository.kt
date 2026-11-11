package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.api.FfzApi
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.mapper.FfzMapper
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class FfzProviderRepository(
    private val ffzApi: FfzApi,
    private val downloader: EmoteDownloader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : EmoteProviderRepository {

    override val provider: EmoteProvider = EmoteProvider.FRANKERFACEZ

    override suspend fun searchEmotes(
        query: String,
        page: Int,
        limit: Int
    ): StickerResult<List<UnifiedEmote>> = withContext(dispatcher) {
        val safePerPage = limit.coerceIn(1, 50)
        val safePage = page.coerceAtLeast(1)
        val trimmed = query.trim()

        safeApiCall(
            call = {
                ffzApi.searchEmoticons(
                    query = trimmed,
                    page = safePage,
                    perPage = safePerPage
                )
            },
            transform = { body ->
                val src = body.emoticons
                val result = ArrayList<UnifiedEmote>(src.size)
                val seenIds = HashSet<Long>(src.size * 2)
                for (i in src.indices) {
                    val dto = src[i]
                    if (!dto.hidden && seenIds.add(dto.id)) {
                        result.add(FfzMapper.toDomain(dto))
                    }
                }
                result
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
                call = {
                    if (trimmed.all { it.isDigit() }) {
                        ffzApi.getRoomByTwitchId(trimmed)
                    } else {
                        ffzApi.getRoom(trimmed.lowercase())
                    }
                },
                transform = { body -> FfzMapper.toRoomEmotes(body) }
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
                            message = "Empty FFZ response body"
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
            StickerResult.Failure(StickerError.NetworkError(message = e.message ?: "FFZ network error"))
        }
    }
}

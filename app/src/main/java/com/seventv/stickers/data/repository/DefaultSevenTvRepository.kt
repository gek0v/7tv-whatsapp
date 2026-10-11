package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.api.SevenTvApi
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.mapper.SevenTvMapper
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class DefaultSevenTvRepository(
    private val api: SevenTvApi,
    private val downloader: EmoteDownloader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SevenTvRepository {

    override suspend fun searchEmotes(
        query: String,
        page: Int,
        limit: Int
    ): StickerResult<List<SevenTvEmote>> = safeApiCall(
        call = { api.searchEmotes(query, page, limit) },
        transform = { dtoList -> dtoList.map { SevenTvMapper.toDomain(it) } }
    )

    override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> = safeApiCall(
        call = { api.getEmote(emoteId) },
        transform = { SevenTvMapper.toDomain(it) }
    )

    override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> = safeApiCall(
        call = { api.getEmoteSet(setId) },
        transform = { SevenTvMapper.toDomain(it) }
    )

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
}

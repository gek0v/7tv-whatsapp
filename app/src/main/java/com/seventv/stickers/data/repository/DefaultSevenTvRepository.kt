package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.api.SevenTvApi
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.dto.SevenTvGqlQueries
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
        call = { api.searchEmotes(SevenTvGqlQueries.searchEmotesRequest(query, page, limit)) },
        transform = { gqlResponse ->
            val items = gqlResponse.data?.emotes?.items.orEmpty()
            items.map { SevenTvMapper.toDomain(it) }
        }
    )

    override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> = safeApiCall(
        call = { api.getEmote(emoteId) },
        transform = { SevenTvMapper.toDomain(it) }
    )

    override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> = safeApiCall(
        call = { api.getEmoteSet(setId) },
        transform = { SevenTvMapper.toDomain(it) }
    )

    override suspend fun getStreamerEmoteSet(query: String): StickerResult<SevenTvEmoteSet> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }

        // 1. First attempt to fetch emote set directly (in case query is an emote set ID)
        val directSetResult = getEmoteSet(trimmed)
        if (directSetResult is StickerResult.Success) {
            return directSetResult
        }

        // 2. Search users via GraphQL
        val userSearchCall = safeApiCall(
            call = { api.searchUsers(SevenTvGqlQueries.searchUsersRequest(trimmed, limit = 5)) },
            transform = { gqlResponse -> gqlResponse.data?.users.orEmpty() }
        )

        if (userSearchCall is StickerResult.Success) {
            val users = userSearchCall.data
            val targetUser = users.firstOrNull { it.username.equals(trimmed, ignoreCase = true) }
                ?: users.firstOrNull()

            val activeSetId = targetUser?.emoteSets?.firstOrNull()?.id
            if (!activeSetId.isNullOrBlank()) {
                val userSetResult = getEmoteSet(activeSetId)
                if (userSetResult is StickerResult.Success) {
                    val displayName = targetUser.displayName ?: targetUser.username
                    val setName = userSetResult.data.name.ifBlank { displayName }
                    return StickerResult.Success(
                        userSetResult.data.copy(
                            name = setName,
                            ownerName = displayName
                        )
                    )
                }
            }
        }

        // 3. Fallback: search emotes matching the channel name and synthesize a set
        val searchResult = searchEmotes(trimmed, limit = 100)
        if (searchResult is StickerResult.Success) {
            val emotes = searchResult.data
            if (emotes.isNotEmpty()) {
                return StickerResult.Success(
                    SevenTvEmoteSet(
                        id = trimmed,
                        name = trimmed,
                        ownerName = trimmed,
                        emotes = emotes
                    )
                )
            }
        }

        return StickerResult.Failure(StickerError.ResourceNotFound)
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

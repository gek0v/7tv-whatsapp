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

    override suspend fun getChannelData(query: String): StickerResult<ChannelSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }

        // 1. First attempt to fetch emote set directly (in case query is an emote set ID)
        val directSetResult = getEmoteSet(trimmed)
        if (directSetResult is StickerResult.Success) {
            val set = directSetResult.data
            val summary = ChannelEmoteSetSummary(
                id = set.id,
                name = set.name.ifBlank { "Emote Set" },
                emoteCount = set.emotes.size,
                isActive = true
            )
            return StickerResult.Success(
                ChannelSearchResult(
                    streamerName = set.name.ifBlank { set.ownerName ?: trimmed },
                    username = set.ownerName ?: trimmed,
                    avatarUrl = null,
                    availableSets = listOf(summary),
                    activeSet = set
                )
            )
        }

        // 2. Search users via GraphQL
        val userSearchCall = safeApiCall(
            call = { api.searchUsers(SevenTvGqlQueries.searchUsersRequest(trimmed, limit = 5)) },
            transform = { gqlResponse -> gqlResponse.data?.users.orEmpty() }
        )

        if (userSearchCall is StickerResult.Success) {
            val users = userSearchCall.data
            if (users.isNotEmpty()) {
                val targetUser = users.firstOrNull { it.username.equals(trimmed, ignoreCase = true) }
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

                        return StickerResult.Success(
                            ChannelSearchResult(
                                streamerName = displayName,
                                username = targetUser.username,
                                avatarUrl = targetUser.avatarUrl,
                                availableSets = summaries,
                                activeSet = activeEmoteSet
                            )
                        )
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
                return StickerResult.Success(
                    ChannelSearchResult(
                        streamerName = trimmed,
                        username = trimmed,
                        avatarUrl = null,
                        availableSets = listOf(summary),
                        activeSet = set
                    )
                )
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

package com.seventv.stickers.data.repository

import com.seventv.stickers.core.network.api.FfzApi
import com.seventv.stickers.core.network.api.SevenTvApi
import com.seventv.stickers.core.network.api.TwitchEmotesApi
import com.seventv.stickers.core.network.dto.SevenTvGqlQueries
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a Twitch username/login to a numeric Twitch user ID across public APIs
 * (7TV GraphQL connections, FFZ room lookup, and IVR Twitch lookup) with in-memory caching.
 */
class TwitchIdResolver(
    private val sevenTvApi: SevenTvApi? = null,
    private val ffzApi: FfzApi? = null,
    private val twitchApi: TwitchEmotesApi? = null
) {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun resolve(channelQuery: String): String? {
        val trimmed = channelQuery.trim().lowercase()
        if (trimmed.isBlank()) return null
        if (trimmed.all { it.isDigit() }) return trimmed

        cache[trimmed]?.let { return it }

        // 1. Try Twitch IVR user lookup (fast direct login -> id)
        if (twitchApi != null) {
            try {
                val resp = twitchApi.getUser(trimmed)
                if (resp.isSuccessful) {
                    val userId = resp.body()?.firstOrNull {
                        it.login.equals(trimmed, ignoreCase = true)
                    }?.id?.takeIf { it.isNotBlank() }
                        ?: resp.body()?.firstOrNull()?.id?.takeIf { it.isNotBlank() }
                    if (userId != null) {
                        cache[trimmed] = userId
                        return userId
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        // 2. Try FFZ room lookup (returns room.twitch_id)
        if (ffzApi != null) {
            try {
                val resp = ffzApi.getRoom(trimmed)
                if (resp.isSuccessful) {
                    val twitchId = resp.body()?.room?.twitchId?.toString()
                    if (!twitchId.isNullOrBlank()) {
                        cache[trimmed] = twitchId
                        return twitchId
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        // 3. Try 7TV user search connections
        if (sevenTvApi != null) {
            try {
                val resp = sevenTvApi.searchUsers(SevenTvGqlQueries.searchUsersRequest(trimmed, limit = 5))
                if (resp.isSuccessful) {
                    val users = resp.body()?.data?.users.orEmpty()
                    val user = users.firstOrNull { it.username.equals(trimmed, ignoreCase = true) }
                        ?: users.firstOrNull()
                    val twitchId = user?.connections
                        ?.firstOrNull { it.platform.equals("TWITCH", ignoreCase = true) }
                        ?.id
                        ?.takeIf { it.isNotBlank() }
                    if (twitchId != null) {
                        cache[trimmed] = twitchId
                        return twitchId
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        return null
    }
}

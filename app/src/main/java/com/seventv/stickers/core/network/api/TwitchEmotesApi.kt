package com.seventv.stickers.core.network.api

import com.seventv.stickers.core.network.dto.TwitchChannelEmotesResponseDto
import com.seventv.stickers.core.network.dto.TwitchEmoteSetDto
import com.seventv.stickers.core.network.dto.TwitchUserDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TwitchEmotesApi {
    @GET("v2/twitch/emotes/channel/{login}")
    suspend fun getChannelEmotes(
        @Path("login") login: String
    ): Response<TwitchChannelEmotesResponseDto>

    @GET("v2/twitch/emotes/sets")
    suspend fun getGlobalEmotes(
        @Query("set_id") setId: String = "0"
    ): Response<List<TwitchEmoteSetDto>>

    @GET("v2/twitch/user")
    suspend fun getUser(
        @Query("login") login: String
    ): Response<List<TwitchUserDto>>
}

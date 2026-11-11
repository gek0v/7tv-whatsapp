package com.seventv.stickers.core.network.api

import com.seventv.stickers.core.network.dto.BttvEmoteDto
import com.seventv.stickers.core.network.dto.BttvTrendingEmoteDto
import com.seventv.stickers.core.network.dto.BttvUserResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BttvApi {
    @GET("3/cached/users/twitch/{twitchId}")
    suspend fun getUserByTwitchId(
        @Path("twitchId") twitchId: String
    ): Response<BttvUserResponseDto>

    @GET("3/cached/emotes/global")
    suspend fun getGlobalEmotes(): Response<List<BttvEmoteDto>>

    @GET("3/emotes/shared/trending")
    suspend fun getTrendingEmotes(
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<BttvTrendingEmoteDto>>

    @GET("3/emotes/shared/search")
    suspend fun searchEmotes(
        @Query("query") query: String,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<BttvEmoteDto>>
}

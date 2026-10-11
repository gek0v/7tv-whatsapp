package com.seventv.stickers.core.network.api

import com.seventv.stickers.core.network.dto.SevenTvEmoteDto
import com.seventv.stickers.core.network.dto.SevenTvEmoteSetDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SevenTvApi {
    @GET("v3/emotes")
    suspend fun searchEmotes(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): Response<List<SevenTvEmoteDto>>

    @GET("v3/emotes/{id}")
    suspend fun getEmote(
        @Path("id") emoteId: String
    ): Response<SevenTvEmoteDto>

    @GET("v3/emote-sets/{id}")
    suspend fun getEmoteSet(
        @Path("id") setId: String
    ): Response<SevenTvEmoteSetDto>
}

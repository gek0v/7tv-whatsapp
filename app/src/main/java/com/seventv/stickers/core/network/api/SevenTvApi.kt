package com.seventv.stickers.core.network.api

import com.seventv.stickers.core.network.dto.SevenTvEmoteDto
import com.seventv.stickers.core.network.dto.SevenTvEmoteSetDto
import com.seventv.stickers.core.network.dto.SevenTvGqlEmotesData
import com.seventv.stickers.core.network.dto.SevenTvGqlRequest
import com.seventv.stickers.core.network.dto.SevenTvGqlResponse
import com.seventv.stickers.core.network.dto.SevenTvGqlUsersData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SevenTvApi {
    @POST("v3/gql")
    suspend fun searchEmotes(
        @Body request: SevenTvGqlRequest
    ): Response<SevenTvGqlResponse<SevenTvGqlEmotesData>>

    @POST("v3/gql")
    suspend fun searchUsers(
        @Body request: SevenTvGqlRequest
    ): Response<SevenTvGqlResponse<SevenTvGqlUsersData>>

    @GET("v3/emotes/{id}")
    suspend fun getEmote(
        @Path("id") emoteId: String
    ): Response<SevenTvEmoteDto>

    @GET("v3/emote-sets/{id}")
    suspend fun getEmoteSet(
        @Path("id") setId: String
    ): Response<SevenTvEmoteSetDto>
}

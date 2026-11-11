package com.seventv.stickers.core.network.api

import com.seventv.stickers.core.network.dto.FfzEmoticonsSearchResponseDto
import com.seventv.stickers.core.network.dto.FfzGlobalResponseDto
import com.seventv.stickers.core.network.dto.FfzRoomResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FfzApi {
    @GET("v1/room/{channelName}")
    suspend fun getRoom(
        @Path("channelName") channelName: String
    ): Response<FfzRoomResponseDto>

    @GET("v1/room/id/{twitchId}")
    suspend fun getRoomByTwitchId(
        @Path("twitchId") twitchId: String
    ): Response<FfzRoomResponseDto>

    @GET("v1/set/global")
    suspend fun getGlobalSets(): Response<FfzGlobalResponseDto>

    @GET("v1/emoticons")
    suspend fun searchEmoticons(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Query("sort") sort: String = "count-desc"
    ): Response<FfzEmoticonsSearchResponseDto>
}

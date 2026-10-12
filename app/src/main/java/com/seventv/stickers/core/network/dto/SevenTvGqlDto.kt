package com.seventv.stickers.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class SevenTvGqlRequest(
    val query: String,
    val variables: JsonObject? = null
)

@Serializable
data class SevenTvGqlResponse<T>(
    val data: T? = null,
    val errors: List<SevenTvGqlError>? = null
)

@Serializable
data class SevenTvGqlError(
    val message: String
)

@Serializable
data class SevenTvGqlEmotesData(
    val emotes: SevenTvGqlEmotesConnection? = null
)

@Serializable
data class SevenTvGqlEmotesConnection(
    val count: Int = 0,
    val items: List<SevenTvEmoteDto> = emptyList()
)

@Serializable
data class SevenTvGqlUsersData(
    val users: List<SevenTvGqlUserDto> = emptyList()
)

@Serializable
data class SevenTvGqlUserDto(
    val id: String,
    val username: String,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("emote_sets")
    val emoteSets: List<SevenTvGqlEmoteSetRefDto> = emptyList()
)

@Serializable
data class SevenTvGqlEmoteSetRefDto(
    val id: String,
    val name: String
)

object SevenTvGqlQueries {
    const val SEARCH_EMOTES = """
        query SearchEmotes(${'$'}query: String!, ${'$'}page: Int, ${'$'}limit: Int) {
          emotes(query: ${'$'}query, page: ${'$'}page, limit: ${'$'}limit) {
            count
            items {
              id
              name
              animated
              host {
                url
                files {
                  name
                  format
                  width
                  height
                }
              }
            }
          }
        }
    """

    const val SEARCH_USERS = """
        query SearchUsers(${'$'}query: String!, ${'$'}limit: Int) {
          users(query: ${'$'}query, limit: ${'$'}limit) {
            id
            username
            display_name
            emote_sets {
              id
              name
            }
          }
        }
    """

    fun searchEmotesRequest(query: String, page: Int = 1, limit: Int = 30): SevenTvGqlRequest {
        return SevenTvGqlRequest(
            query = SEARCH_EMOTES,
            variables = buildJsonObject {
                put("query", query)
                put("page", page)
                put("limit", limit)
            }
        )
    }

    fun searchUsersRequest(query: String, limit: Int = 5): SevenTvGqlRequest {
        return SevenTvGqlRequest(
            query = SEARCH_USERS,
            variables = buildJsonObject {
                put("query", query)
                put("limit", limit)
            }
        )
    }
}

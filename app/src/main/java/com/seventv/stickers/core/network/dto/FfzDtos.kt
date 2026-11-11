package com.seventv.stickers.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FfzRoomResponseDto(
    val room: FfzRoomDto? = null,
    val sets: Map<String, FfzEmoteSetDto> = emptyMap()
)

@Serializable
data class FfzRoomDto(
    val id: Long? = null,
    @SerialName("twitch_id")
    val twitchId: Long? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    val set: Long? = null,
    @SerialName("is_group")
    val isGroup: Boolean = false
)

@Serializable
data class FfzGlobalResponseDto(
    @SerialName("default_sets")
    val defaultSets: List<Long> = emptyList(),
    val sets: Map<String, FfzEmoteSetDto> = emptyMap()
)

@Serializable
data class FfzEmoticonsSearchResponseDto(
    val emoticons: List<FfzEmoticonDto> = emptyList(),
    @SerialName("_total")
    val total: Int = 0,
    @SerialName("_pages")
    val pages: Int = 0
)

@Serializable
data class FfzEmoteSetDto(
    val id: Long = 0L,
    val title: String? = null,
    val emoticons: List<FfzEmoticonDto> = emptyList()
)

@Serializable
data class FfzEmoticonDto(
    val id: Long,
    val name: String,
    val width: Int? = null,
    val height: Int? = null,
    val modifier: Boolean = false,
    val hidden: Boolean = false,
    val animated: Map<String, String>? = null,
    val urls: Map<String, String> = emptyMap(),
    val owner: FfzOwnerDto? = null
)

@Serializable
data class FfzOwnerDto(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("display_name")
    val displayName: String? = null
)

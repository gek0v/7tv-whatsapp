package com.seventv.stickers.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class BttvEmoteDto(
    val id: String,
    val code: String,
    val imageType: String = "png",
    val animated: Boolean = false,
    val userId: String? = null,
    val user: BttvUserRefDto? = null
)

@Serializable
data class BttvUserRefDto(
    val id: String? = null,
    val name: String? = null,
    val displayName: String? = null,
    val providerId: String? = null
)

@Serializable
data class BttvUserResponseDto(
    val id: String = "",
    val bots: List<String> = emptyList(),
    val avatar: String? = null,
    val channelEmotes: List<BttvEmoteDto> = emptyList(),
    val sharedEmotes: List<BttvEmoteDto> = emptyList()
)

@Serializable
data class BttvTrendingEmoteDto(
    val emote: BttvEmoteDto,
    val total: Int = 0
)

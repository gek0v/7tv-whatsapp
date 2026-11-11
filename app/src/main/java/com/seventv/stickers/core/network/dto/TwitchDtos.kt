package com.seventv.stickers.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TwitchEmoteDto(
    val id: String,
    val code: String,
    val assetType: String = "STATIC",
    val emoteType: String? = null
)

@Serializable
data class TwitchEmoteSetDto(
    val setID: String = "",
    val emotes: List<TwitchEmoteDto> = emptyList()
)

@Serializable
data class TwitchChannelEmotesResponseDto(
    val channelName: String? = null,
    val channelLogin: String? = null,
    val channelID: String? = null,
    val emoteSets: List<TwitchEmoteSetDto> = emptyList(),
    val subscriberEmotes: List<TwitchEmoteSetDto> = emptyList(),
    val bitEmotes: List<TwitchEmoteDto> = emptyList()
)

@Serializable
data class TwitchUserDto(
    val id: String = "",
    val login: String = "",
    val displayName: String? = null,
    val logo: String? = null
)

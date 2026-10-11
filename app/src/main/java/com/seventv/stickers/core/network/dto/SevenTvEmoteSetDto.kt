package com.seventv.stickers.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SevenTvEmoteSetDto(
    val id: String,
    val name: String,
    val flags: Int = 0,
    val emotes: List<SevenTvEmoteDto> = emptyList(),
    val capacity: Int = 0,
    @SerialName("owner_name")
    val ownerName: String? = null
)

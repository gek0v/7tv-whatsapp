package com.seventv.stickers.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SevenTvEmoteDto(
    val id: String,
    val name: String,
    val flags: Int = 0,
    val animated: Boolean = false,
    val data: SevenTvEmoteDataDto? = null,
    val host: SevenTvHostDto? = null
)

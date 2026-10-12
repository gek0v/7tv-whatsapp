package com.seventv.stickers.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SevenTvHostDto(
    val url: String,
    val files: List<SevenTvFileDto> = emptyList()
)

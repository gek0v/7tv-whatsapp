package com.seventv.stickers.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SevenTvEmoteDataDto(
    val id: String? = null,
    val name: String? = null,
    val flags: Int = 0,
    val animated: Boolean = false,
    val host: SevenTvHostDto? = null,
    val tags: List<String> = emptyList()
)

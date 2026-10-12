package com.seventv.stickers.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SevenTvFileDto(
    val name: String,
    val format: String = "WEBP",
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0L
)

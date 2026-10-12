package com.seventv.stickers.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImagePath: String,
    val isAnimated: Boolean,
    val isWhitelisted: Boolean = false,
    val stickers: List<PackSticker> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

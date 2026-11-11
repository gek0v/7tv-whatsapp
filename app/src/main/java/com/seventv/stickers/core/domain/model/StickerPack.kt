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
    val createdAt: Long = System.currentTimeMillis(),
    val originSetId: String? = null,
    val originStreamerName: String? = null,
    val imageDataVersion: Long = 1L,
    val lastSyncedVersion: Long = 0L
) {
    val hasPendingChanges: Boolean
        get() = isWhitelisted && lastSyncedVersion < imageDataVersion
}

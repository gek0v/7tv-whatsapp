package com.seventv.stickers.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sticker_packs")
data class StickerPackEntity(
    @PrimaryKey
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageRelativePath: String,
    val isAnimated: Boolean,
    val isWhitelisted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val originSetId: String? = null,
    val originStreamerName: String? = null
)

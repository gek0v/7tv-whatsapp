package com.seventv.stickers.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class PackWithStickers(
    @Embedded
    val pack: StickerPackEntity,
    @Relation(
        parentColumn = "identifier",
        entityColumn = "packIdentifier"
    )
    val stickers: List<StickerEntity>
)

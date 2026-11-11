package com.seventv.stickers.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stickers",
    foreignKeys = [
        ForeignKey(
            entity = StickerPackEntity::class,
            parentColumns = ["identifier"],
            childColumns = ["packIdentifier"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("packIdentifier"),
        Index("emoteId")
    ]
)
data class StickerEntity(
    @PrimaryKey
    val id: String,
    val packIdentifier: String,
    val emoteId: String,
    val emoteName: String,
    val relativeFilePath: String,
    val emojis: List<String>,
    val fileLength: Long,
    val sortOrder: Int,
    val provider: String = "SEVENTV"
)

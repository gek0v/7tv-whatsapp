package com.seventv.stickers.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface StickerDao {
    @Query("UPDATE stickers SET emojis = :emojis WHERE id = :stickerId")
    suspend fun updateEmojis(stickerId: String, emojis: List<String>)

    @Query("UPDATE stickers SET sortOrder = :newOrder WHERE id = :stickerId")
    suspend fun updateSortOrder(stickerId: String, newOrder: Int)

    @Query("DELETE FROM stickers WHERE id = :stickerId")
    suspend fun deleteSticker(stickerId: String)
}

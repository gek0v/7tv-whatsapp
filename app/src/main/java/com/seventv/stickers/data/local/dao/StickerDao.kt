package com.seventv.stickers.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.seventv.stickers.data.local.entity.StickerEntity

@Dao
interface StickerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSticker(sticker: StickerEntity)

    @Query("UPDATE stickers SET emojis = :emojis WHERE id = :stickerId")
    suspend fun updateEmojis(stickerId: String, emojis: List<String>)

    @Query("UPDATE stickers SET sortOrder = :newOrder WHERE id = :stickerId")
    suspend fun updateSortOrder(stickerId: String, newOrder: Int)

    @Query("DELETE FROM stickers WHERE id = :stickerId")
    suspend fun deleteSticker(stickerId: String)

    @Query("SELECT COUNT(*) FROM stickers WHERE packIdentifier = :packIdentifier")
    suspend fun getStickerCount(packIdentifier: String): Int
}

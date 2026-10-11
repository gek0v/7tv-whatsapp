package com.seventv.stickers.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.seventv.stickers.data.local.entity.PackWithStickers
import com.seventv.stickers.data.local.entity.StickerEntity
import com.seventv.stickers.data.local.entity.StickerPackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerPackDao {
    @Transaction
    @Query("SELECT * FROM sticker_packs ORDER BY createdAt DESC")
    fun observeAllPacks(): Flow<List<PackWithStickers>>

    @Transaction
    @Query("SELECT * FROM sticker_packs WHERE identifier = :identifier LIMIT 1")
    suspend fun getPackByIdentifier(identifier: String): PackWithStickers?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPack(pack: StickerPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickers(stickers: List<StickerEntity>)

    @Transaction
    suspend fun insertPackWithStickers(pack: StickerPackEntity, stickers: List<StickerEntity>) {
        insertPack(pack)
        insertStickers(stickers)
    }

    @Query("UPDATE sticker_packs SET isWhitelisted = :isWhitelisted WHERE identifier = :identifier")
    suspend fun updateWhitelisted(identifier: String, isWhitelisted: Boolean)

    @Query("DELETE FROM sticker_packs WHERE identifier = :identifier")
    suspend fun deletePack(identifier: String)
}

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
    @Query("SELECT * FROM sticker_packs ORDER BY createdAt DESC")
    fun getAllPacksDirect(): List<PackWithStickers>

    @Transaction
    @Query("SELECT * FROM sticker_packs WHERE identifier = :identifier LIMIT 1")
    suspend fun getPackByIdentifier(identifier: String): PackWithStickers?

    @Transaction
    @Query("SELECT * FROM sticker_packs WHERE identifier = :identifier LIMIT 1")
    fun getPackByIdentifierDirect(identifier: String): PackWithStickers?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPack(pack: StickerPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickers(stickers: List<StickerEntity>)

    @Transaction
    suspend fun insertPackWithStickers(pack: StickerPackEntity, stickers: List<StickerEntity>) {
        insertPack(pack)
        insertStickers(stickers)
    }

    @Transaction
    suspend fun replacePacksWithMerged(
        deletePackIds: List<String>,
        newPacksWithStickers: List<Pair<StickerPackEntity, List<StickerEntity>>>
    ) {
        for ((pack, stickers) in newPacksWithStickers) {
            insertPack(pack)
            insertStickers(stickers)
        }
        deletePacks(deletePackIds)
    }

    @Transaction
    suspend fun addStickersIfUnderLimit(
        packIdentifier: String,
        stickers: List<StickerEntity>,
        maxStickers: Int = 30
    ): Boolean {
        val current = getPackByIdentifier(packIdentifier) ?: return false
        if (current.stickers.size + stickers.size > maxStickers) {
            return false
        }
        val startSortOrder = (current.stickers.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val updatedStickers = stickers.mapIndexed { idx, s ->
            s.copy(sortOrder = startSortOrder + idx)
        }
        insertStickers(updatedStickers)
        incrementImageDataVersion(packIdentifier)
        return true
    }

    @Query("UPDATE sticker_packs SET isWhitelisted = :isWhitelisted, lastSyncedVersion = CASE WHEN :isWhitelisted = 0 THEN 0 ELSE lastSyncedVersion END WHERE identifier = :identifier")
    suspend fun updateWhitelisted(identifier: String, isWhitelisted: Boolean)

    @Query("UPDATE sticker_packs SET isWhitelisted = 0, lastSyncedVersion = 0 WHERE identifier = :identifier")
    suspend fun resetWhitelistStatus(identifier: String)

    @Query("UPDATE sticker_packs SET isWhitelisted = 1, lastSyncedVersion = imageDataVersion WHERE identifier = :identifier")
    suspend fun markPackSynced(identifier: String)

    @Query("UPDATE sticker_packs SET imageDataVersion = imageDataVersion + 1 WHERE identifier = :identifier")
    suspend fun incrementImageDataVersion(identifier: String)

    @Query("UPDATE sticker_packs SET name = :newName WHERE identifier = :identifier")
    suspend fun updatePackName(identifier: String, newName: String)

    @Query("DELETE FROM sticker_packs WHERE identifier = :identifier")
    suspend fun deletePack(identifier: String)

    @Query("DELETE FROM sticker_packs WHERE identifier IN (:identifiers)")
    suspend fun deletePacks(identifiers: List<String>)
}

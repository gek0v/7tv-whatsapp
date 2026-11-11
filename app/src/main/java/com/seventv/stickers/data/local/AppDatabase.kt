package com.seventv.stickers.data.local

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.seventv.stickers.data.local.converter.EmojiListConverter
import com.seventv.stickers.data.local.dao.StickerDao
import com.seventv.stickers.data.local.dao.StickerPackDao
import com.seventv.stickers.data.local.entity.StickerEntity
import com.seventv.stickers.data.local.entity.StickerPackEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StickerPackEntity::class, StickerEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(EmojiListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stickerPackDao(): StickerPackDao
    abstract fun stickerDao(): StickerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sticker_packs ADD COLUMN originSetId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sticker_packs ADD COLUMN originStreamerName TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sticker_packs ADD COLUMN imageDataVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sticker_packs ADD COLUMN lastSyncedVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE sticker_packs SET lastSyncedVersion = 1 WHERE isWhitelisted = 1")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "seventv_stickers.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { INSTANCE = it }
            }
        }

        @VisibleForTesting
        fun setTestInstance(testInstance: AppDatabase?) {
            INSTANCE = testInstance
        }
    }
}

package com.seventv.stickers.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.data.local.entity.StickerEntity
import com.seventv.stickers.data.local.entity.StickerPackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StickerDatabaseMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_migration.db"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration2To3_addsProviderColumnWithDefaultSevenTv() = runTest {
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `sticker_packs` (`identifier` TEXT NOT NULL, `name` TEXT NOT NULL, `publisher` TEXT NOT NULL, `trayImageRelativePath` TEXT NOT NULL, `isAnimated` INTEGER NOT NULL, `isWhitelisted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `originSetId` TEXT, `originStreamerName` TEXT, PRIMARY KEY(`identifier`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `stickers` (`id` TEXT NOT NULL, `packIdentifier` TEXT NOT NULL, `emoteId` TEXT NOT NULL, `emoteName` TEXT NOT NULL, `relativeFilePath` TEXT NOT NULL, `emojis` TEXT NOT NULL, `fileLength` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`packIdentifier`) REFERENCES `sticker_packs`(`identifier`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_stickers_packIdentifier` ON `stickers` (`packIdentifier`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_stickers_emoteId` ON `stickers` (`emoteId`)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
        val writableDb = openHelper.writableDatabase

        // Insert legacy data at version 2
        writableDb.execSQL(
            "INSERT INTO sticker_packs VALUES ('pack_v2', 'Legacy Pack', 'Owner', 'tray.png', 0, 0, 1000, NULL, NULL)"
        )
        writableDb.execSQL(
            "INSERT INTO stickers VALUES ('stk_legacy', 'pack_v2', 'emote_old', 'OldEmote', 'path.webp', '[]', 2048, 0)"
        )

        writableDb.close()
        openHelper.close()

        // Now open via Room v3 to ensure Room schema and entities load cleanly
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val retrievedPack = roomDb.stickerPackDao().getPackByIdentifier("pack_v2")
        assertNotNull(retrievedPack)
        assertEquals(1, retrievedPack!!.stickers.size)
        val sticker = retrievedPack.stickers[0]
        assertEquals("SEVENTV", sticker.provider)
        assertEquals("stk_legacy", sticker.id)

        // Insert new multi-provider sticker entity at v3
        val bttvSticker = StickerEntity(
            id = "stk_bttv",
            packIdentifier = "pack_v2",
            emoteId = "b1",
            emoteName = "catJAM",
            relativeFilePath = "path_bttv.webp",
            emojis = listOf("🐱"),
            fileLength = 4096,
            sortOrder = 1,
            provider = EmoteProvider.BETTERTTV.name
        )
        roomDb.stickerDao().insertSticker(bttvSticker)

        val updated = roomDb.stickerPackDao().getPackByIdentifier("pack_v2")
        assertEquals(2, updated!!.stickers.size)
        assertEquals("BETTERTTV", updated.stickers[1].provider)

        roomDb.close()
    }
}

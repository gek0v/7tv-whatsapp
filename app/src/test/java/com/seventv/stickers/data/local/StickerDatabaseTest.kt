package com.seventv.stickers.data.local

import android.content.Context
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.seventv.stickers.data.local.dao.StickerDao
import com.seventv.stickers.data.local.dao.StickerPackDao
import com.seventv.stickers.data.local.entity.StickerEntity
import com.seventv.stickers.data.local.entity.StickerPackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StickerDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var packDao: StickerPackDao
    private lateinit var stickerDao: StickerDao

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        packDao = db.stickerPackDao()
        stickerDao = db.stickerDao()
    }

    @After
    fun tearDown() {
        AppDatabase.setTestInstance(null)
        db.close()
    }

    @Test
    fun insertAndQueryPackWithStickers_returnsCorrectData() = runTest {
        val pack = StickerPackEntity(
            identifier = "pack-1",
            name = "Pepe Pack",
            publisher = "7TV",
            trayImageRelativePath = "sticker_packs/pack-1/tray_icon.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        val stickers = listOf(
            StickerEntity(
                id = "stk-1",
                packIdentifier = "pack-1",
                emoteId = "emote-1",
                emoteName = "pepeLaugh",
                relativeFilePath = "sticker_packs/pack-1/sticker_stk-1.webp",
                emojis = listOf("😂", "🐸"),
                fileLength = 12345L,
                sortOrder = 0
            ),
            StickerEntity(
                id = "stk-2",
                packIdentifier = "pack-1",
                emoteId = "emote-2",
                emoteName = "pepeClap",
                relativeFilePath = "sticker_packs/pack-1/sticker_stk-2.webp",
                emojis = listOf("👏"),
                fileLength = 23456L,
                sortOrder = 1
            )
        )

        packDao.insertPackWithStickers(pack, stickers)

        val retrieved = packDao.getPackByIdentifier("pack-1")
        assertNotNull(retrieved)
        assertEquals("pack-1", retrieved!!.pack.identifier)
        assertEquals("Pepe Pack", retrieved.pack.name)
        assertEquals("7TV", retrieved.pack.publisher)
        assertEquals(2, retrieved.stickers.size)
        assertEquals("stk-1", retrieved.stickers[0].id)
        assertEquals(listOf("😂", "🐸"), retrieved.stickers[0].emojis)
        assertEquals("stk-2", retrieved.stickers[1].id)
    }

    @Test
    fun getPackByIdentifier_returnsNull_whenNotFound() = runTest {
        val retrieved = packDao.getPackByIdentifier("non-existent")
        assertNull(retrieved)
    }

    @Test
    fun updateWhitelisted_updatesFieldCorrectly() = runTest {
        val pack = StickerPackEntity(
            identifier = "pack-wl",
            name = "WL Pack",
            publisher = "Tester",
            trayImageRelativePath = "tray.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        packDao.insertPack(pack)

        val initial = packDao.getPackByIdentifier("pack-wl")
        assertNotNull(initial)
        assertFalse(initial!!.pack.isWhitelisted)

        packDao.updateWhitelisted("pack-wl", true)
        val afterUpdate = packDao.getPackByIdentifier("pack-wl")
        assertNotNull(afterUpdate)
        assertTrue(afterUpdate!!.pack.isWhitelisted)

        packDao.updateWhitelisted("pack-wl", false)
        val afterReset = packDao.getPackByIdentifier("pack-wl")
        assertNotNull(afterReset)
        assertFalse(afterReset!!.pack.isWhitelisted)
    }

    @Test
    fun cascadeDeletion_deletesChildStickersAutomatically() = runTest {
        val pack = StickerPackEntity(
            identifier = "pack-cascade",
            name = "Cascade Pack",
            publisher = "Cascade Tester",
            trayImageRelativePath = "tray.webp",
            isAnimated = true,
            isWhitelisted = false,
            createdAt = 1000L
        )
        val stickers = listOf(
            StickerEntity(
                id = "stk-c1",
                packIdentifier = "pack-cascade",
                emoteId = "emote-c1",
                emoteName = "catJAM",
                relativeFilePath = "path1.webp",
                emojis = listOf("🐱"),
                fileLength = 5000L,
                sortOrder = 0
            ),
            StickerEntity(
                id = "stk-c2",
                packIdentifier = "pack-cascade",
                emoteId = "emote-c2",
                emoteName = "pepeJam",
                relativeFilePath = "path2.webp",
                emojis = listOf("🐸"),
                fileLength = 6000L,
                sortOrder = 1
            )
        )
        packDao.insertPackWithStickers(pack, stickers)

        // Verify count in stickers table before delete
        val countBefore = countStickersInDb("pack-cascade")
        assertEquals(2, countBefore)

        // Delete the parent pack
        packDao.deletePack("pack-cascade")

        // Parent pack is gone
        assertNull(packDao.getPackByIdentifier("pack-cascade"))

        // Child stickers must be automatically deleted by SQLite foreign key CASCADE
        val countAfter = countStickersInDb("pack-cascade")
        assertEquals(0, countAfter)
    }

    @Test
    fun updateEmojisAndSortOrder_updatesIndividualSticker() = runTest {
        val pack = StickerPackEntity(
            identifier = "pack-stk-update",
            name = "Sticker Update Pack",
            publisher = "Tester",
            trayImageRelativePath = "tray.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        val sticker = StickerEntity(
            id = "stk-up-1",
            packIdentifier = "pack-stk-update",
            emoteId = "emote-up-1",
            emoteName = "monkaS",
            relativeFilePath = "monkaS.webp",
            emojis = listOf("😨"),
            fileLength = 4000L,
            sortOrder = 0
        )
        packDao.insertPackWithStickers(pack, listOf(sticker))

        stickerDao.updateEmojis("stk-up-1", listOf("😱", "👀"))
        stickerDao.updateSortOrder("stk-up-1", 10)

        val updatedPack = packDao.getPackByIdentifier("pack-stk-update")
        assertNotNull(updatedPack)
        val updatedSticker = updatedPack!!.stickers.first { it.id == "stk-up-1" }
        assertEquals(listOf("😱", "👀"), updatedSticker.emojis)
        assertEquals(10, updatedSticker.sortOrder)
    }

    @Test
    fun deleteSticker_removesSingleSticker() = runTest {
        val pack = StickerPackEntity(
            identifier = "pack-del-stk",
            name = "Delete Sticker Pack",
            publisher = "Tester",
            trayImageRelativePath = "tray.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        val stickers = listOf(
            StickerEntity(
                id = "stk-d1",
                packIdentifier = "pack-del-stk",
                emoteId = "emote-d1",
                emoteName = "s1",
                relativeFilePath = "s1.webp",
                emojis = listOf("1️⃣"),
                fileLength = 1000L,
                sortOrder = 0
            ),
            StickerEntity(
                id = "stk-d2",
                packIdentifier = "pack-del-stk",
                emoteId = "emote-d2",
                emoteName = "s2",
                relativeFilePath = "s2.webp",
                emojis = listOf("2️⃣"),
                fileLength = 2000L,
                sortOrder = 1
            )
        )
        packDao.insertPackWithStickers(pack, stickers)

        stickerDao.deleteSticker("stk-d1")

        val packWithStickers = packDao.getPackByIdentifier("pack-del-stk")
        assertNotNull(packWithStickers)
        assertEquals(1, packWithStickers!!.stickers.size)
        assertEquals("stk-d2", packWithStickers.stickers.first().id)
    }

    @Test
    fun observeAllPacks_emitsUpdatedListReactively() = runTest {
        val initialList = packDao.observeAllPacks().first()
        assertTrue(initialList.isEmpty())

        val pack1 = StickerPackEntity(
            identifier = "pack-1",
            name = "Pack 1",
            publisher = "Pub 1",
            trayImageRelativePath = "tray1.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        packDao.insertPack(pack1)

        val afterFirstInsert = packDao.observeAllPacks().first()
        assertEquals(1, afterFirstInsert.size)
        assertEquals("pack-1", afterFirstInsert[0].pack.identifier)

        val pack2 = StickerPackEntity(
            identifier = "pack-2",
            name = "Pack 2",
            publisher = "Pub 2",
            trayImageRelativePath = "tray2.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 2000L
        )
        packDao.insertPack(pack2)

        val afterSecondInsert = packDao.observeAllPacks().first()
        assertEquals(2, afterSecondInsert.size)
        // Ordered by createdAt DESC
        assertEquals("pack-2", afterSecondInsert[0].pack.identifier)
        assertEquals("pack-1", afterSecondInsert[1].pack.identifier)

        packDao.deletePack("pack-2")
        val afterDelete = packDao.observeAllPacks().first()
        assertEquals(1, afterDelete.size)
        assertEquals("pack-1", afterDelete[0].pack.identifier)
    }

    @Test
    fun getAllPacksDirect_returnsPacksSynchronouslyWithStickers() = runTest {
        val pack1 = StickerPackEntity(
            identifier = "pack-sync-1",
            name = "Pack 1",
            publisher = "7TV",
            trayImageRelativePath = "tray1.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        val pack2 = StickerPackEntity(
            identifier = "pack-sync-2",
            name = "Pack 2",
            publisher = "7TV",
            trayImageRelativePath = "tray2.webp",
            isAnimated = true,
            isWhitelisted = true,
            createdAt = 2000L
        )
        val stickers1 = listOf(
            StickerEntity(
                id = "s1",
                packIdentifier = "pack-sync-1",
                emoteId = "e1",
                emoteName = "emote1",
                relativeFilePath = "s1.webp",
                emojis = listOf("😀"),
                fileLength = 1000L,
                sortOrder = 0
            )
        )
        val stickers2 = listOf(
            StickerEntity(
                id = "s2",
                packIdentifier = "pack-sync-2",
                emoteId = "e2",
                emoteName = "emote2",
                relativeFilePath = "s2.webp",
                emojis = listOf("🎉"),
                fileLength = 2000L,
                sortOrder = 0
            )
        )
        packDao.insertPackWithStickers(pack1, stickers1)
        packDao.insertPackWithStickers(pack2, stickers2)

        val directPacks = packDao.getAllPacksDirect()
        assertEquals(2, directPacks.size)
        assertEquals("pack-sync-2", directPacks[0].pack.identifier)
        assertEquals(1, directPacks[0].stickers.size)
        assertEquals("s2", directPacks[0].stickers[0].id)

        assertEquals("pack-sync-1", directPacks[1].pack.identifier)
        assertEquals(1, directPacks[1].stickers.size)
        assertEquals("s1", directPacks[1].stickers[0].id)
    }

    @Test
    fun getPackByIdentifierDirect_returnsPackSynchronously_andNullWhenNotFound() = runTest {
        val pack = StickerPackEntity(
            identifier = "pack-direct-find",
            name = "Direct Find Pack",
            publisher = "7TV",
            trayImageRelativePath = "tray.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1500L
        )
        val sticker = StickerEntity(
            id = "s-find",
            packIdentifier = "pack-direct-find",
            emoteId = "e-find",
            emoteName = "emoteFind",
            relativeFilePath = "find.webp",
            emojis = listOf("🔍"),
            fileLength = 1200L,
            sortOrder = 0
        )
        packDao.insertPackWithStickers(pack, listOf(sticker))

        val found = packDao.getPackByIdentifierDirect("pack-direct-find")
        assertNotNull(found)
        assertEquals("pack-direct-find", found!!.pack.identifier)
        assertEquals(1, found.stickers.size)
        assertEquals("s-find", found.stickers[0].id)

        val notFound = packDao.getPackByIdentifierDirect("non-existent-pack")
        assertNull(notFound)
    }

    @Test
    fun appDatabaseSingleton_getInstanceReturnsSameInstance_andSetTestInstanceOverrides() {
        val context = RuntimeEnvironment.getApplication()
        try {
            AppDatabase.setTestInstance(null)
            val instance1 = AppDatabase.getInstance(context)
            val instance2 = AppDatabase.getInstance(context)
            assertNotNull(instance1)
            assertTrue(instance1 === instance2)

            AppDatabase.setTestInstance(db)
            val testInstance = AppDatabase.getInstance(context)
            assertTrue(testInstance === db)
        } finally {
            AppDatabase.setTestInstance(null)
        }
    }

    @Test
    fun insertAndQueryPackWithOriginSet_persistsOriginMetadata() = runTest {
        val pack = StickerPackEntity(
            identifier = "streamer-pack-1",
            name = "Ibai Emotes",
            publisher = "Ibai",
            trayImageRelativePath = "sticker_packs/streamer-pack-1/tray_icon.webp",
            isAnimated = true,
            isWhitelisted = false,
            createdAt = 1000L,
            originSetId = "60b947c94c979d3118cf9456",
            originStreamerName = "ibai"
        )
        packDao.insertPack(pack)

        val retrieved = packDao.getPackByIdentifierDirect("streamer-pack-1")
        assertNotNull(retrieved)
        assertEquals("60b947c94c979d3118cf9456", retrieved?.pack?.originSetId)
        assertEquals("ibai", retrieved?.pack?.originStreamerName)
    }

    @Test
    fun migration2To3_addsVersionColumnsAndInitializesLastSyncedForWhitelisted() {
        val context: Context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("test_migration_2_3.db")
        dbFile.delete()

        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS sticker_packs (
                            identifier TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            publisher TEXT NOT NULL,
                            trayImageRelativePath TEXT NOT NULL,
                            isAnimated INTEGER NOT NULL,
                            isWhitelisted INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            originSetId TEXT DEFAULT NULL,
                            originStreamerName TEXT DEFAULT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO sticker_packs VALUES ('wl-pack', 'WL', '7TV', 't.png', 0, 1, 1000, NULL, NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO sticker_packs VALUES ('local-pack', 'Local', '7TV', 't.png', 0, 0, 2000, NULL, NULL)"
                    )
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        try {
            val sqliteDb = helper.writableDatabase
            AppDatabase.MIGRATION_2_3.migrate(sqliteDb)

            sqliteDb.query("SELECT identifier, imageDataVersion, lastSyncedVersion FROM sticker_packs ORDER BY identifier").use { cursor ->
                assertEquals(2, cursor.count)

                assertTrue(cursor.moveToNext())
                assertEquals("local-pack", cursor.getString(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals(0L, cursor.getLong(2))

                assertTrue(cursor.moveToNext())
                assertEquals("wl-pack", cursor.getString(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals(1L, cursor.getLong(2))
            }
        } finally {
            helper.close()
            dbFile.delete()
        }
    }

    @Test
    fun daoVersioningAndSyncQueries_behaveCorrectly() = runTest {
        val pack = StickerPackEntity(
            identifier = "pack-ver",
            name = "Version Pack",
            publisher = "7TV",
            trayImageRelativePath = "tray.png",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        packDao.insertPack(pack)

        packDao.incrementImageDataVersion("pack-ver")
        packDao.incrementImageDataVersion("pack-ver")
        var loaded = packDao.getPackByIdentifier("pack-ver")!!.pack
        assertEquals(3L, loaded.imageDataVersion)
        assertEquals(0L, loaded.lastSyncedVersion)

        packDao.markPackSynced("pack-ver")
        loaded = packDao.getPackByIdentifier("pack-ver")!!.pack
        assertTrue(loaded.isWhitelisted)
        assertEquals(3L, loaded.imageDataVersion)
        assertEquals(3L, loaded.lastSyncedVersion)

        packDao.incrementImageDataVersion("pack-ver")
        packDao.updateWhitelisted("pack-ver", true)
        loaded = packDao.getPackByIdentifier("pack-ver")!!.pack
        assertEquals(4L, loaded.imageDataVersion)
        assertEquals(3L, loaded.lastSyncedVersion)

        packDao.updateWhitelisted("pack-ver", false)
        loaded = packDao.getPackByIdentifier("pack-ver")!!.pack
        assertFalse(loaded.isWhitelisted)
        assertEquals(0L, loaded.lastSyncedVersion)
    }

    private fun countStickersInDb(packIdentifier: String): Int {
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM stickers WHERE packIdentifier = ?",
            arrayOf(packIdentifier)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }
}

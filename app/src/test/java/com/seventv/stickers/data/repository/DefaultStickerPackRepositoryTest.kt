package com.seventv.stickers.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.media.StickerMediaPipeline
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import com.seventv.stickers.data.local.AppDatabase
import com.seventv.stickers.data.local.dao.StickerDao
import com.seventv.stickers.data.local.dao.StickerPackDao
import com.seventv.stickers.data.local.entity.StickerEntity
import com.seventv.stickers.data.local.entity.StickerPackEntity
import com.seventv.stickers.data.local.storage.DefaultStickerStorageManager
import com.seventv.stickers.whatsapp.WhatsAppConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DefaultStickerPackRepositoryTest {

    class MockWhitelistContentProvider : ContentProvider() {
        companion object {
            var cursorToReturn: Cursor? = null
            var exceptionToThrow: RuntimeException? = null

            fun reset() {
                cursorToReturn = null
                exceptionToThrow = null
            }
        }

        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            exceptionToThrow?.let { throw it }
            return cursorToReturn
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var packDao: StickerPackDao
    private lateinit var stickerDao: StickerDao
    private lateinit var storageManager: DefaultStickerStorageManager
    private lateinit var fakeSevenTvRepo: FakeSevenTvRepository
    private lateinit var mediaPipeline: StickerMediaPipeline
    private lateinit var emoteBridge: EmoteStickerBridge
    private lateinit var repository: DefaultStickerPackRepository
    private lateinit var storageDir: File

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        MockWhitelistContentProvider.reset()
        val providerInfo = ProviderInfo().apply {
            authority = WhatsAppConstants.WHITELIST_AUTHORITY
        }
        Robolectric.buildContentProvider(MockWhitelistContentProvider::class.java)
            .create(providerInfo)
            .get()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        packDao = db.stickerPackDao()
        stickerDao = db.stickerDao()

        storageDir = tempFolder.newFolder("storage_root")
        storageManager = DefaultStickerStorageManager(storageDir, testDispatcher)

        fakeSevenTvRepo = FakeSevenTvRepository()
        mediaPipeline = StickerMediaPipeline(dispatcher = testDispatcher)
        emoteBridge = EmoteStickerBridge(fakeSevenTvRepo, mediaPipeline)

        repository = DefaultStickerPackRepository(
            stickerPackDao = packDao,
            stickerDao = stickerDao,
            storageManager = storageManager,
            emoteBridge = emoteBridge,
            mediaPipeline = mediaPipeline,
            dispatcher = testDispatcher,
            sevenTvRepository = fakeSevenTvRepo
        )
    }

    @After
    fun tearDown() {
        MockWhitelistContentProvider.reset()
        db.close()
    }

    private fun createSamplePngBytes(width: Int = 100, height: Int = 100): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bitmap.recycle()
        return baos.toByteArray()
    }

    private fun createSampleEmote(
        id: String,
        name: String,
        isAnimated: Boolean = false
    ): SevenTvEmote {
        return SevenTvEmote(
            id = id,
            name = name,
            isAnimated = isAnimated,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "https://cdn.7tv.app/emote/$id/4x.webp",
            availableScales = listOf("1x", "2x", "3x", "4x")
        )
    }

    @Test
    fun createPacksFromEmotesSuccess() = runTest(testDispatcher) {
        val sampleBytes = createSamplePngBytes(120, 120)
        val emotes = (1..35).map { index ->
            createSampleEmote(id = "e_$index", name = "emote_$index", isAnimated = false)
        }

        fakeSevenTvRepo.downloadHandler = { emote ->
            StickerResult.Success(
                DownloadedEmoteAsset(
                    emoteId = emote.id,
                    emoteName = emote.name,
                    bytes = sampleBytes,
                    isAnimated = false,
                    format = "PNG"
                )
            )
        }

        val result = repository.createPacksFromEmotes("Community Pack", "7TV Team", emotes)

        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val packs = (result as StickerResult.Success<List<StickerPack>>).data
        assertEquals("Expected 35 emotes split into 2 packs", 2, packs.size)

        val pack1 = packs[0]
        val pack2 = packs[1]

        assertEquals("Community Pack Part 1", pack1.name)
        assertEquals("Community Pack Part 2", pack2.name)
        assertEquals("7TV Team", pack1.publisher)
        assertEquals("7TV Team", pack2.publisher)
        assertFalse(pack1.isAnimated)
        assertFalse(pack2.isAnimated)
        assertEquals(18, pack1.stickers.size)
        assertEquals(17, pack2.stickers.size)

        for (pack in packs) {
            // Verify tray icon file exists on disk
            val trayFile = storageManager.getAbsoluteFile(pack.trayImagePath)
            assertTrue("Tray icon file must exist on disk: ${trayFile.path}", trayFile.exists())
            assertTrue("Tray icon must be > 0 bytes", trayFile.length() > 0)

            // Verify staging directory was cleaned up
            val stagingDir = File(storageManager.getPackDirectory(pack.identifier), "staging")
            assertFalse("Staging directory must be cleaned up", stagingDir.exists())

            // Verify stickers saved to disk and order
            for ((idx, sticker) in pack.stickers.withIndex()) {
                assertEquals(idx, sticker.sortOrder)
                assertEquals(listOf("💬"), sticker.emojis)
                val stickerFile = storageManager.getAbsoluteFile(sticker.filePath)
                assertTrue("Sticker file must exist: ${stickerFile.path}", stickerFile.exists())
                assertTrue("Sticker file must be > 0 bytes", stickerFile.length() > 0)
                assertEquals(stickerFile.length(), sticker.fileLength)
            }

            // Verify persistence in Room DB
            val dbPack = packDao.getPackByIdentifier(pack.identifier)
            assertNotNull("Pack must exist in Room DB", dbPack)
            assertEquals(pack.name, dbPack!!.pack.name)
            assertEquals(pack.stickers.size, dbPack.stickers.size)
        }
    }

    @Test
    fun observeAllPacksEmitsPacksWithSortedStickers() = runTest(testDispatcher) {
        val packEntity = StickerPackEntity(
            identifier = "test-pack-1",
            name = "Test Sorted Pack",
            publisher = "Tester",
            trayImageRelativePath = "sticker_packs/test-pack-1/tray_icon.webp",
            isAnimated = false,
            isWhitelisted = true,
            createdAt = 1000L
        )

        // Insert stickers intentionally out of order
        val unorderedStickers = listOf(
            StickerEntity(
                id = "s3",
                packIdentifier = "test-pack-1",
                emoteId = "e3",
                emoteName = "three",
                relativeFilePath = "path3.webp",
                emojis = listOf("3️⃣"),
                fileLength = 300L,
                sortOrder = 2
            ),
            StickerEntity(
                id = "s1",
                packIdentifier = "test-pack-1",
                emoteId = "e1",
                emoteName = "one",
                relativeFilePath = "path1.webp",
                emojis = listOf("1️⃣"),
                fileLength = 100L,
                sortOrder = 0
            ),
            StickerEntity(
                id = "s2",
                packIdentifier = "test-pack-1",
                emoteId = "e2",
                emoteName = "two",
                relativeFilePath = "path2.webp",
                emojis = listOf("2️⃣"),
                fileLength = 200L,
                sortOrder = 1
            )
        )

        packDao.insertPackWithStickers(packEntity, unorderedStickers)

        val packs = repository.observeAllPacks().first()
        assertEquals(1, packs.size)
        val domainPack = packs[0]

        assertEquals("test-pack-1", domainPack.identifier)
        assertEquals("Test Sorted Pack", domainPack.name)
        assertTrue(domainPack.isWhitelisted)
        assertEquals(3, domainPack.stickers.size)

        // Verify stickers are strictly sorted by sortOrder
        assertEquals("s1", domainPack.stickers[0].id)
        assertEquals(0, domainPack.stickers[0].sortOrder)
        assertEquals("s2", domainPack.stickers[1].id)
        assertEquals(1, domainPack.stickers[1].sortOrder)
        assertEquals("s3", domainPack.stickers[2].id)
        assertEquals(2, domainPack.stickers[2].sortOrder)
    }

    @Test
    fun getPackReturnsPackWhenExistsAndNotFoundWhenMissing() = runTest(testDispatcher) {
        // Missing pack returns PackNotFound
        val missingResult = repository.getPack("non-existent-id")
        assertTrue("Expected failure for missing pack", missingResult is StickerResult.Failure)
        val missingError = (missingResult as StickerResult.Failure).error
        assertTrue("Expected PackNotFound, got $missingError", missingError is StickerError.PackNotFound)
        assertEquals("non-existent-id", (missingError as StickerError.PackNotFound).identifier)

        // Existing pack returns Success
        val packEntity = StickerPackEntity(
            identifier = "existing-pack",
            name = "Existing Pack",
            publisher = "Author",
            trayImageRelativePath = "sticker_packs/existing-pack/tray_icon.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 2000L
        )
        val stickers = listOf(
            StickerEntity(
                id = "stk-1",
                packIdentifier = "existing-pack",
                emoteId = "em-1",
                emoteName = "Smile",
                relativeFilePath = "sticker_packs/existing-pack/sticker_stk-1.webp",
                emojis = listOf("😄"),
                fileLength = 500L,
                sortOrder = 0
            )
        )
        packDao.insertPackWithStickers(packEntity, stickers)

        val successResult = repository.getPack("existing-pack")
        assertTrue("Expected success for existing pack", successResult is StickerResult.Success<*>)
        val domainPack = (successResult as StickerResult.Success<StickerPack>).data
        assertEquals("existing-pack", domainPack.identifier)
        assertEquals("Existing Pack", domainPack.name)
        assertEquals(1, domainPack.stickers.size)
        assertEquals("stk-1", domainPack.stickers[0].id)
        assertEquals("Smile", domainPack.stickers[0].emoteName)
    }

    @Test
    fun deletePackRemovesDatabaseRowAndDiskFolder() = runTest(testDispatcher) {
        val packIdentifier = "pack-to-delete"
        val packDir = storageManager.getPackDirectory(packIdentifier).apply { mkdirs() }
        val dummyFile = File(packDir, "dummy.txt").apply { writeText("hello") }
        assertTrue(dummyFile.exists())

        val packEntity = StickerPackEntity(
            identifier = packIdentifier,
            name = "Pack To Delete",
            publisher = "Tester",
            trayImageRelativePath = "sticker_packs/$packIdentifier/tray_icon.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        val sticker = StickerEntity(
            id = "s-del-1",
            packIdentifier = packIdentifier,
            emoteId = "e-del-1",
            emoteName = "DelEmote",
            relativeFilePath = "path.webp",
            emojis = listOf("❌"),
            fileLength = 100L,
            sortOrder = 0
        )
        packDao.insertPackWithStickers(packEntity, listOf(sticker))

        assertNotNull(packDao.getPackByIdentifier(packIdentifier))

        val deleteResult = repository.deletePack(packIdentifier)
        assertTrue(deleteResult is StickerResult.Success<*>)

        // Verify database row and cascaded stickers are gone
        assertNull(packDao.getPackByIdentifier(packIdentifier))

        // Verify disk folder is gone
        assertFalse("Pack directory on disk must be deleted", packDir.exists())
    }

    @Test
    fun updateStickerEmojisAndWhitelisted() = runTest(testDispatcher) {
        val packIdentifier = "pack-update-test"
        val packEntity = StickerPackEntity(
            identifier = packIdentifier,
            name = "Update Test Pack",
            publisher = "Tester",
            trayImageRelativePath = "tray.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        val sticker = StickerEntity(
            id = "s-up-1",
            packIdentifier = packIdentifier,
            emoteId = "e-up-1",
            emoteName = "UpEmote",
            relativeFilePath = "path.webp",
            emojis = listOf("💬"),
            fileLength = 100L,
            sortOrder = 0
        )
        packDao.insertPackWithStickers(packEntity, listOf(sticker))

        val emojiResult = repository.updateStickerEmojis("s-up-1", listOf("🐸", "🔥"))
        assertTrue(emojiResult is StickerResult.Success<*>)

        val whitelistResult = repository.updatePackWhitelisted(packIdentifier, true)
        assertTrue(whitelistResult is StickerResult.Success<*>)

        val updatedPack = packDao.getPackByIdentifier(packIdentifier)
        assertNotNull(updatedPack)
        assertTrue(updatedPack!!.pack.isWhitelisted)
        val updatedSticker = updatedPack.stickers.first { it.id == "s-up-1" }
        assertEquals(listOf("🐸", "🔥"), updatedSticker.emojis)
    }

    @Test
    fun renamePackUpdatesNameAndIncrementsVersion() = runTest(testDispatcher) {
        val packIdentifier = "pack-rename-test"
        val packEntity = StickerPackEntity(
            identifier = packIdentifier,
            name = "Old Name",
            publisher = "Tester",
            trayImageRelativePath = "tray.webp",
            isAnimated = false,
            isWhitelisted = true,
            createdAt = 1000L,
            imageDataVersion = 1L,
            lastSyncedVersion = 1L
        )
        packDao.insertPackWithStickers(packEntity, emptyList())

        val renameResult = repository.renamePack(packIdentifier, "New Brand Name")
        assertTrue(renameResult is StickerResult.Success<*>)

        val updated = packDao.getPackByIdentifier(packIdentifier)
        assertNotNull(updated)
        assertEquals("New Brand Name", updated!!.pack.name)
        assertTrue("Whitelist should stay true on edit", updated.pack.isWhitelisted)
        assertEquals(2L, updated.pack.imageDataVersion)
        assertEquals(1L, updated.pack.lastSyncedVersion)

        val domain = (repository.getPack(packIdentifier) as StickerResult.Success).data
        assertTrue("Pack should report pending changes", domain.hasPendingChanges)
    }

    @Test
    fun deletePacksDeletesMultiplePacksAndDiskFolders() = runTest(testDispatcher) {
        val p1 = "bulk-del-1"
        val p2 = "bulk-del-2"
        val d1 = storageManager.getPackDirectory(p1).apply { mkdirs() }
        val d2 = storageManager.getPackDirectory(p2).apply { mkdirs() }
        assertTrue(d1.exists() && d2.exists())

        packDao.insertPack(StickerPackEntity(p1, "P1", "Pub", "t.webp", false, false, 1L))
        packDao.insertPack(StickerPackEntity(p2, "P2", "Pub", "t.webp", false, false, 2L))

        val result = repository.deletePacks(listOf(p1, p2))
        assertTrue(result is StickerResult.Success<*>)

        assertNull(packDao.getPackByIdentifier(p1))
        assertNull(packDao.getPackByIdentifier(p2))
        assertFalse(d1.exists())
        assertFalse(d2.exists())
    }

    @Test
    fun deleteStickerFromPackRemovesEntityAndFile() = runTest(testDispatcher) {
        val packId = "pack-del-sticker"
        val packDir = storageManager.getPackDirectory(packId).apply { mkdirs() }
        val stickerId = "stk-to-del"
        val sFile = File(packDir, "sticker_${stickerId}.webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(sFile.exists())

        packDao.insertPack(
            StickerPackEntity(
                identifier = packId,
                name = "P",
                publisher = "Pub",
                trayImageRelativePath = "t.webp",
                isAnimated = false,
                isWhitelisted = true,
                createdAt = 1L,
                imageDataVersion = 1L,
                lastSyncedVersion = 1L
            )
        )
        stickerDao.insertSticker(StickerEntity(stickerId, packId, "e1", "Emote", "path.webp", listOf("💬"), 3L, 0))

        val result = repository.deleteStickerFromPack(packId, stickerId)
        assertTrue(result is StickerResult.Success<*>)

        val updated = packDao.getPackByIdentifier(packId)
        assertNotNull(updated)
        assertEquals(0, updated!!.stickers.size)
        assertFalse("File must be deleted", sFile.exists())
        assertTrue("Whitelist stays true so WhatsApp knows pack is installed", updated.pack.isWhitelisted)
        assertEquals(2L, updated.pack.imageDataVersion)
        assertEquals(1L, updated.pack.lastSyncedVersion)

        val domain = (repository.getPack(packId) as StickerResult.Success).data
        assertTrue("Pack should report pending changes after deleting sticker", domain.hasPendingChanges)
    }

    @Test
    fun createPacksFromEmotesFailsWhenInsufficientEmotes() = runTest(testDispatcher) {
        val emotes = listOf(
            createSampleEmote("e1", "one"),
            createSampleEmote("e2", "two")
        )

        val result = repository.createPacksFromEmotes("Small Pack", "Publisher", emotes)
        assertTrue("Expected failure with < 3 emotes", result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue("Expected InsufficientStickers, got $error", error is StickerError.InsufficientStickers)
        assertEquals(2, (error as StickerError.InsufficientStickers).actual)
    }

    @Test
    fun createPacksFromEmotesCleansUpOnEmoteConversionFailure() = runTest(testDispatcher) {
        val sampleBytes = createSamplePngBytes(100, 100)
        val emotes = (1..5).map { index ->
            createSampleEmote(id = "err_e_$index", name = "err_emote_$index")
        }

        // Fail download on 3rd emote
        fakeSevenTvRepo.downloadHandler = { emote ->
            if (emote.id == "err_e_3") {
                StickerResult.Failure(StickerError.ResourceNotFound)
            } else {
                StickerResult.Success(
                    DownloadedEmoteAsset(
                        emoteId = emote.id,
                        emoteName = emote.name,
                        bytes = sampleBytes,
                        isAnimated = false,
                        format = "PNG"
                    )
                )
            }
        }

        val result = repository.createPacksFromEmotes("Failing Pack", "Tester", emotes)
        assertTrue("Expected failure on emote download error", result is StickerResult.Failure)
        assertEquals(StickerError.ResourceNotFound, (result as StickerResult.Failure).error)

        // Verify no packs were inserted into DB
        val dbPacks = packDao.observeAllPacks().first()
        assertTrue("No packs should remain in DB on failure", dbPacks.isEmpty())

        // Verify storage directory has no pack folders left
        val packDirs = storageManager.getPackDirectory("any").parentFile?.listFiles() ?: emptyArray()
        assertEquals("No pack directories should remain on failure", 0, packDirs.size)
    }

    @Test
    fun coroutineCancellationIsRethrown() = runTest(testDispatcher) {
        val emotes = (1..5).map { index ->
            createSampleEmote(id = "cancel_e_$index", name = "cancel_$index")
        }

        fakeSevenTvRepo.downloadHandler = {
            throw CancellationException("Emote conversion was cancelled")
        }

        try {
            repository.createPacksFromEmotes("Cancel Pack", "Tester", emotes)
            fail("CancellationException should be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Emote conversion was cancelled", e.message)
        }
    }

    @Test
    fun syncWhitelistStatusUpdatesDatabaseAndReturnsTrueWhenWhitelisted() = runTest(testDispatcher) {
        val context: Context = RuntimeEnvironment.getApplication()
        val packIdentifier = "sync-test-pack-1"
        val packEntity = StickerPackEntity(
            identifier = packIdentifier,
            name = "Sync Whitelist Pack",
            publisher = "Tester",
            trayImageRelativePath = "tray.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        packDao.insertPackWithStickers(packEntity, emptyList())

        // Mock whitelist provider returns 1 (whitelisted)
        val cursor = MatrixCursor(arrayOf(WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN)).apply {
            addRow(arrayOf(1))
        }
        MockWhitelistContentProvider.cursorToReturn = cursor

        val result = repository.syncWhitelistStatus(context, packIdentifier)

        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val isWhitelisted = (result as StickerResult.Success<Boolean>).data
        assertTrue("Expected isWhitelisted = true", isWhitelisted)

        val updatedPack = packDao.getPackByIdentifier(packIdentifier)
        assertNotNull(updatedPack)
        assertTrue("Pack in database should be updated to whitelisted", updatedPack!!.pack.isWhitelisted)
    }

    @Test
    fun syncWhitelistStatusUpdatesDatabaseAndReturnsFalseWhenNotWhitelisted() = runTest(testDispatcher) {
        val context: Context = RuntimeEnvironment.getApplication()
        val packIdentifier = "sync-test-pack-2"
        val packEntity = StickerPackEntity(
            identifier = packIdentifier,
            name = "Sync Whitelist Pack 2",
            publisher = "Tester",
            trayImageRelativePath = "tray.webp",
            isAnimated = false,
            isWhitelisted = true,
            createdAt = 1000L
        )
        packDao.insertPackWithStickers(packEntity, emptyList())

        // Mock whitelist provider returns 0 (not whitelisted)
        val cursor = MatrixCursor(arrayOf(WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN)).apply {
            addRow(arrayOf(0))
        }
        MockWhitelistContentProvider.cursorToReturn = cursor

        val result = repository.syncWhitelistStatus(context, packIdentifier)

        assertTrue("Expected Success, got $result", result is StickerResult.Success<*>)
        val isWhitelisted = (result as StickerResult.Success<Boolean>).data
        assertFalse("Expected isWhitelisted = false", isWhitelisted)

        val updatedPack = packDao.getPackByIdentifier(packIdentifier)
        assertNotNull(updatedPack)
        assertFalse("Pack in database should be updated to not whitelisted", updatedPack!!.pack.isWhitelisted)
    }

    @Test
    fun syncWhitelistStatusReturnsFailureWhenDatabaseUpdateThrows() = runTest(testDispatcher) {
        val context: Context = RuntimeEnvironment.getApplication()
        val packIdentifier = "sync-test-pack-error"

        val throwingDao = object : StickerPackDao by packDao {
            override suspend fun updateWhitelisted(identifier: String, isWhitelisted: Boolean) {
                throw RuntimeException("Database write error")
            }
        }

        val errorRepo = DefaultStickerPackRepository(
            stickerPackDao = throwingDao,
            stickerDao = stickerDao,
            storageManager = storageManager,
            emoteBridge = emoteBridge,
            mediaPipeline = mediaPipeline,
            dispatcher = testDispatcher
        )

        val result = errorRepo.syncWhitelistStatus(context, packIdentifier)

        assertTrue("Expected Failure on db error, got $result", result is StickerResult.Failure)
        val error = (result as StickerResult.Failure).error
        assertTrue("Expected StorageIoError, got $error", error is StickerError.StorageIoError)
    }

    @Test
    fun syncWhitelistStatusRethrowsCancellationException() = runTest(testDispatcher) {
        val context: Context = RuntimeEnvironment.getApplication()
        val packIdentifier = "sync-test-pack-cancel"

        val throwingDao = object : StickerPackDao by packDao {
            override suspend fun updateWhitelisted(identifier: String, isWhitelisted: Boolean) {
                throw CancellationException("Sync cancelled")
            }
        }

        val errorRepo = DefaultStickerPackRepository(
            stickerPackDao = throwingDao,
            stickerDao = stickerDao,
            storageManager = storageManager,
            emoteBridge = emoteBridge,
            mediaPipeline = mediaPipeline,
            dispatcher = testDispatcher
        )

        try {
            errorRepo.syncWhitelistStatus(context, packIdentifier)
            fail("CancellationException should be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Sync cancelled", e.message)
        }
    }

    @Test
    fun mergePacksCombinesStickersAndDeletesSourcePacks() = runTest(testDispatcher) {
        val pack1Entity = StickerPackEntity(
            identifier = "merge-pack-1",
            name = "Pack 1",
            publisher = "Author",
            trayImageRelativePath = "sticker_packs/merge-pack-1/tray_icon.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        val s1 = StickerEntity("s1", "merge-pack-1", "e1", "Emote1", "sticker_packs/merge-pack-1/sticker_s1.webp", listOf("😄"), 100L, 0)
        val s2 = StickerEntity("s2", "merge-pack-1", "e2", "Emote2", "sticker_packs/merge-pack-1/sticker_s2.webp", listOf("😆"), 100L, 1)

        val pack2Entity = StickerPackEntity(
            identifier = "merge-pack-2",
            name = "Pack 2",
            publisher = "Author",
            trayImageRelativePath = "sticker_packs/merge-pack-2/tray_icon.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 2000L
        )
        val s3 = StickerEntity("s3", "merge-pack-2", "e3", "Emote3", "sticker_packs/merge-pack-2/sticker_s3.webp", listOf("🔥"), 100L, 0)
        val s4 = StickerEntity("s4", "merge-pack-2", "e4", "Emote4", "sticker_packs/merge-pack-2/sticker_s4.webp", listOf("😎"), 100L, 1)

        packDao.insertPackWithStickers(pack1Entity, listOf(s1, s2))
        packDao.insertPackWithStickers(pack2Entity, listOf(s3, s4))

        val dummyBytes = byteArrayOf(1, 2, 3, 4)
        storageManager.saveStickerFile("merge-pack-1", "s1", dummyBytes)
        storageManager.saveStickerFile("merge-pack-1", "s2", dummyBytes)
        storageManager.saveStickerFile("merge-pack-2", "s3", dummyBytes)
        storageManager.saveStickerFile("merge-pack-2", "s4", dummyBytes)

        val result = repository.mergePacks(listOf("merge-pack-1", "merge-pack-2"), "Combined Pack")
        assertTrue("Expected merge success, got $result", result is StickerResult.Success)

        val mergedPacks = (result as StickerResult.Success).data
        assertEquals(1, mergedPacks.size)
        assertEquals("Combined Pack", mergedPacks[0].name)
        assertEquals(4, mergedPacks[0].stickers.size)

        // Verify old packs were deleted
        assertNull(packDao.getPackByIdentifier("merge-pack-1"))
        assertNull(packDao.getPackByIdentifier("merge-pack-2"))
    }

    @Test
    fun mergePacksDeduplicatesIdenticalEmotesAcrossPacks() = runTest(testDispatcher) {
        val pack1Entity = StickerPackEntity(
            identifier = "dup-pack-1",
            name = "Pack 1",
            publisher = "Author",
            trayImageRelativePath = "sticker_packs/dup-pack-1/tray_icon.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        val s1 = StickerEntity("s1", "dup-pack-1", "e1", "Emote1", "sticker_packs/dup-pack-1/sticker_s1.webp", listOf("😄"), 100L, 0)
        val s2 = StickerEntity("s2", "dup-pack-1", "e2", "Emote2", "sticker_packs/dup-pack-1/sticker_s2.webp", listOf("😆"), 100L, 1)

        val pack2Entity = StickerPackEntity(
            identifier = "dup-pack-2",
            name = "Pack 2",
            publisher = "Author",
            trayImageRelativePath = "sticker_packs/dup-pack-2/tray_icon.webp",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 2000L
        )
        val s3 = StickerEntity("s3", "dup-pack-2", "e1", "Emote1", "sticker_packs/dup-pack-2/sticker_s3.webp", listOf("😄"), 100L, 0)
        val s4 = StickerEntity("s4", "dup-pack-2", "e3", "Emote3", "sticker_packs/dup-pack-2/sticker_s4.webp", listOf("😎"), 100L, 1)
        val s5 = StickerEntity("s5", "dup-pack-2", "e4", "emote2", "sticker_packs/dup-pack-2/sticker_s5.webp", listOf("🔥"), 100L, 2)

        packDao.insertPackWithStickers(pack1Entity, listOf(s1, s2))
        packDao.insertPackWithStickers(pack2Entity, listOf(s3, s4, s5))

        val dummyBytes = byteArrayOf(1, 2, 3, 4)
        storageManager.saveStickerFile("dup-pack-1", "s1", dummyBytes)
        storageManager.saveStickerFile("dup-pack-1", "s2", dummyBytes)
        storageManager.saveStickerFile("dup-pack-2", "s3", dummyBytes)
        storageManager.saveStickerFile("dup-pack-2", "s4", dummyBytes)
        storageManager.saveStickerFile("dup-pack-2", "s5", dummyBytes)

        val result = repository.mergePacks(listOf("dup-pack-1", "dup-pack-2"), "Deduplicated Pack")
        assertTrue("Expected merge success, got $result", result is StickerResult.Success)

        val mergedPacks = (result as StickerResult.Success).data
        assertEquals(1, mergedPacks.size)
        assertEquals("Deduplicated Pack", mergedPacks[0].name)
        // Originally 5 stickers total across the 2 packs.
        // s3 is a duplicate by emoteId ("e1") and s5 is a duplicate by emoteName ("emote2").
        // Exactly 3 unique stickers remain: Emote1, Emote2, Emote3
        assertEquals(3, mergedPacks[0].stickers.size)
        val emoteNames = mergedPacks[0].stickers.map { it.emoteName }
        assertEquals(listOf("Emote1", "Emote2", "Emote3"), emoteNames)

        assertNull(packDao.getPackByIdentifier("dup-pack-1"))
        assertNull(packDao.getPackByIdentifier("dup-pack-2"))
    }

    @Test
    fun addEmotesToPackRejectsMixedAnimatedAndStaticEmotes() = runTest(testDispatcher) {
        val staticPackEntity = StickerPackEntity(
            identifier = "static-pack",
            name = "Static Pack",
            publisher = "Tester",
            trayImageRelativePath = "tray.png",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L
        )
        val animPackEntity = StickerPackEntity(
            identifier = "anim-pack",
            name = "Anim Pack",
            publisher = "Tester",
            trayImageRelativePath = "tray.png",
            isAnimated = true,
            isWhitelisted = false,
            createdAt = 1000L
        )
        packDao.insertPackWithStickers(staticPackEntity, emptyList())
        packDao.insertPackWithStickers(animPackEntity, emptyList())

        val animEmote = createSampleEmote("anim_1", "AnimEmote", isAnimated = true)
        val staticEmote = createSampleEmote("static_1", "StaticEmote", isAnimated = false)

        // Try adding animated emote to static pack
        val result1 = repository.addEmotesToPack("static-pack", listOf(animEmote))
        assertTrue("Should reject animated emote in static pack", result1 is StickerResult.Failure)
        assertTrue("Error should be UnsupportedFormat", (result1 as StickerResult.Failure).error is StickerError.UnsupportedFormat)

        // Try adding static emote to animated pack
        val result2 = repository.addEmotesToPack("anim-pack", listOf(staticEmote))
        assertTrue("Should reject static emote in animated pack", result2 is StickerResult.Failure)
        assertTrue("Error should be UnsupportedFormat", (result2 as StickerResult.Failure).error is StickerError.UnsupportedFormat)
    }

    @Test
    fun addEmotesToPackAddsEmotesInParallelAndPreservesOrder() = runTest(testDispatcher) {
        val sampleBytes = createSamplePngBytes(100, 100)
        val packEntity = StickerPackEntity(
            identifier = "add-pack-1",
            name = "Add Test Pack",
            publisher = "Tester",
            trayImageRelativePath = "tray.png",
            isAnimated = false,
            isWhitelisted = true,
            createdAt = 1000L,
            imageDataVersion = 1L,
            lastSyncedVersion = 1L
        )
        val existingSticker = StickerEntity(
            id = "s0",
            packIdentifier = "add-pack-1",
            emoteId = "e0",
            emoteName = "Emote0",
            relativeFilePath = "s0.webp",
            emojis = listOf("😀"),
            fileLength = 100L,
            sortOrder = 0
        )
        packDao.insertPackWithStickers(packEntity, listOf(existingSticker))

        fakeSevenTvRepo.downloadHandler = { emote ->
            StickerResult.Success(
                DownloadedEmoteAsset(
                    emoteId = emote.id,
                    emoteName = emote.name,
                    bytes = sampleBytes,
                    isAnimated = false,
                    format = "PNG"
                )
            )
        }

        val newEmotes = (1..5).map { idx ->
            createSampleEmote("new_e_$idx", "NewEmote$idx", isAnimated = false)
        }

        val result = repository.addEmotesToPack("add-pack-1", newEmotes)
        assertTrue("Expected Success adding emotes, got $result", result is StickerResult.Success)

        val updatedPack = packDao.getPackByIdentifier("add-pack-1")
        assertNotNull(updatedPack)
        assertEquals(6, updatedPack!!.stickers.size)
        assertTrue("Pack whitelisted should remain true after adding emotes", updatedPack.pack.isWhitelisted)
        assertEquals(2L, updatedPack.pack.imageDataVersion)
        assertEquals(1L, updatedPack.pack.lastSyncedVersion)
        val domainPack = (repository.getPack("add-pack-1") as StickerResult.Success).data
        assertTrue("hasPendingChanges should be true after adding emotes", domainPack.hasPendingChanges)

        val sortedStickers = updatedPack.stickers.sortedBy { it.sortOrder }
        for ((idx, sticker) in sortedStickers.withIndex()) {
            assertEquals(idx, sticker.sortOrder)
            if (idx > 0) {
                assertEquals("new_e_$idx", sticker.emoteId)
                assertEquals("NewEmote$idx", sticker.emoteName)
            }
        }
    }

    @Test
    fun whatsappPackSyncLifecycle_tracksVersionAndPendingChangesEndToEnd() = runTest(testDispatcher) {
        val context: Context = RuntimeEnvironment.getApplication()
        val packId = "sync-lifecycle-pack"
        val sampleBytes = createSamplePngBytes(100, 100)
        val packEntity = StickerPackEntity(
            identifier = packId,
            name = "Lifecycle Pack",
            publisher = "7TV",
            trayImageRelativePath = "tray.png",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L,
            imageDataVersion = 1L,
            lastSyncedVersion = 0L
        )
        val s0 = StickerEntity(
            id = "s0",
            packIdentifier = packId,
            emoteId = "e0",
            emoteName = "Emote0",
            relativeFilePath = "s0.webp",
            emojis = listOf("😀"),
            fileLength = 100L,
            sortOrder = 0
        )
        packDao.insertPackWithStickers(packEntity, listOf(s0))

        // 1. Initial: not exported -> hasPendingChanges = false
        var domain = (repository.getPack(packId) as StickerResult.Success).data
        assertFalse(domain.isWhitelisted)
        assertFalse(domain.hasPendingChanges)

        // 2. Mark synced after export -> isWhitelisted = true, lastSyncedVersion = 1, hasPendingChanges = false
        assertTrue(repository.markPackSynced(packId) is StickerResult.Success)
        domain = (repository.getPack(packId) as StickerResult.Success).data
        assertTrue(domain.isWhitelisted)
        assertEquals(1L, domain.imageDataVersion)
        assertEquals(1L, domain.lastSyncedVersion)
        assertFalse(domain.hasPendingChanges)

        // 3. Add emote -> imageDataVersion increments to 2, hasPendingChanges becomes true
        fakeSevenTvRepo.downloadHandler = { emote ->
            StickerResult.Success(
                DownloadedEmoteAsset(emote.id, emote.name, sampleBytes, false, "PNG")
            )
        }
        assertTrue(repository.addEmotesToPack(packId, listOf(createSampleEmote("e1", "Emote1", false))) is StickerResult.Success)
        domain = (repository.getPack(packId) as StickerResult.Success).data
        assertEquals(2L, domain.imageDataVersion)
        assertEquals(1L, domain.lastSyncedVersion)
        assertTrue(domain.hasPendingChanges)

        // 4. syncWhitelistStatus returns true from WhatsApp -> MUST NOT clear hasPendingChanges
        MockWhitelistContentProvider.cursorToReturn = MatrixCursor(arrayOf(WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN)).apply {
            addRow(arrayOf(1))
        }
        val syncTrueRes = repository.syncWhitelistStatus(context, packId)
        assertTrue((syncTrueRes as StickerResult.Success).data)
        domain = (repository.getPack(packId) as StickerResult.Success).data
        assertTrue(domain.isWhitelisted)
        assertEquals(2L, domain.imageDataVersion)
        assertEquals(1L, domain.lastSyncedVersion)
        assertTrue("syncWhitelistStatus must NOT overwrite lastSyncedVersion", domain.hasPendingChanges)

        // 5. Update sticker emojis & delete sticker & rename -> each increments imageDataVersion
        assertTrue(repository.updateStickerEmojis("s0", listOf("🔥")) is StickerResult.Success)
        assertTrue(repository.deleteStickerFromPack(packId, "s0") is StickerResult.Success)
        assertTrue(repository.renamePack(packId, "Renamed Pack") is StickerResult.Success)
        domain = (repository.getPack(packId) as StickerResult.Success).data
        assertEquals(5L, domain.imageDataVersion)
        assertEquals(1L, domain.lastSyncedVersion)
        assertTrue(domain.hasPendingChanges)

        // 6. Re-export to WhatsApp succeeds -> markPackSynced sets lastSyncedVersion = 5
        assertTrue(repository.markPackSynced(packId) is StickerResult.Success)
        domain = (repository.getPack(packId) as StickerResult.Success).data
        assertTrue(domain.isWhitelisted)
        assertEquals(5L, domain.imageDataVersion)
        assertEquals(5L, domain.lastSyncedVersion)
        assertFalse(domain.hasPendingChanges)

        // 7. WhatsApp reports pack removed (false) -> resets isWhitelisted = false and lastSyncedVersion = 0
        MockWhitelistContentProvider.cursorToReturn = MatrixCursor(arrayOf(WhatsAppConstants.WHITELIST_QUERY_RESULT_COLUMN)).apply {
            addRow(arrayOf(0))
        }
        val syncFalseRes = repository.syncWhitelistStatus(context, packId)
        assertFalse((syncFalseRes as StickerResult.Success).data)
        domain = (repository.getPack(packId) as StickerResult.Success).data
        assertFalse(domain.isWhitelisted)
        assertEquals(0L, domain.lastSyncedVersion)
        assertFalse(domain.hasPendingChanges)
    }

    @Test
    fun checkForPackUpdatesReturnsOnlyNewEmotes() = runTest(testDispatcher) {
        val packEntity = StickerPackEntity(
            identifier = "sync-pack-1",
            name = "Streamer Pack",
            publisher = "Streamer",
            trayImageRelativePath = "tray.png",
            isAnimated = false,
            isWhitelisted = false,
            createdAt = 1000L,
            originSetId = "set-123",
            originStreamerName = "streamer"
        )
        val existingSticker = StickerEntity(
            id = "s1",
            packIdentifier = "sync-pack-1",
            emoteId = "e1",
            emoteName = "Emote1",
            relativeFilePath = "s1.webp",
            emojis = listOf("😀"),
            fileLength = 100L,
            sortOrder = 0
        )
        packDao.insertPackWithStickers(packEntity, listOf(existingSticker))

        val remoteEmote1 = createSampleEmote("e1", "Emote1", isAnimated = false)
        val remoteEmote2 = createSampleEmote("e2", "Emote2", isAnimated = false)
        val remoteEmote3 = createSampleEmote("e3", "Emote3", isAnimated = false)

        fakeSevenTvRepo.emoteSetHandler = { setId ->
            assertEquals("set-123", setId)
            StickerResult.Success(
                SevenTvEmoteSet(
                    id = "set-123",
                    name = "Streamer Set",
                    ownerName = "Streamer",
                    emotes = listOf(remoteEmote1, remoteEmote2, remoteEmote3)
                )
            )
        }

        val result = repository.checkForPackUpdates("sync-pack-1")
        assertTrue("Expected Success, got $result", result is StickerResult.Success)
        val newEmotes = (result as StickerResult.Success).data
        assertEquals(2, newEmotes.size)
        assertEquals(listOf("e2", "e3"), newEmotes.map { it.id })
    }

    private class FakeSevenTvRepository : SevenTvRepository {
        var downloadHandler: (suspend (SevenTvEmote) -> StickerResult<DownloadedEmoteAsset>)? = null
        var emoteSetHandler: (suspend (String) -> StickerResult<SevenTvEmoteSet>)? = null

        override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> {
            return downloadHandler?.invoke(emote)
                ?: StickerResult.Failure(StickerError.ResourceNotFound)
        }

        override suspend fun searchEmotes(
            query: String,
            page: Int,
            limit: Int,
            category: String?
        ): StickerResult<List<SevenTvEmote>> {
            return StickerResult.Success(emptyList())
        }

        override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }

        override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> {
            return emoteSetHandler?.invoke(setId) ?: StickerResult.Failure(StickerError.ResourceNotFound)
        }
    }
}

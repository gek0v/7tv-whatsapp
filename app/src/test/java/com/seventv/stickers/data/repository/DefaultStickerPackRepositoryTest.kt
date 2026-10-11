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
            dispatcher = testDispatcher
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

    private class FakeSevenTvRepository : SevenTvRepository {
        var downloadHandler: (suspend (SevenTvEmote) -> StickerResult<DownloadedEmoteAsset>)? = null

        override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> {
            return downloadHandler?.invoke(emote)
                ?: StickerResult.Failure(StickerError.ResourceNotFound)
        }

        override suspend fun searchEmotes(query: String, page: Int, limit: Int): StickerResult<List<SevenTvEmote>> {
            return StickerResult.Success(emptyList())
        }

        override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }

        override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }
    }
}

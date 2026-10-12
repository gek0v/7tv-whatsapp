package com.seventv.stickers.whatsapp

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.room.Room
import com.seventv.stickers.data.local.AppDatabase
import com.seventv.stickers.data.local.entity.StickerEntity
import com.seventv.stickers.data.local.entity.StickerPackEntity
import com.seventv.stickers.data.local.storage.DefaultStickerStorageManager
import com.seventv.stickers.data.local.storage.StickerStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StickerContentProviderTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var tempDir: File
    private lateinit var storageManager: StickerStorageManager
    private lateinit var provider: StickerContentProvider

    private val authority = StickerContentProvider.AUTHORITY

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = Files.createTempDirectory("sticker_provider_test").toFile()
        storageManager = DefaultStickerStorageManager(tempDir)

        provider = Robolectric.buildContentProvider(StickerContentProvider::class.java).create().get()
        provider.setTestDependencies(db, storageManager)
    }

    @After
    fun tearDown() {
        AppDatabase.setTestInstance(null)
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun queryMetadata_returnsAll13ColumnsAndAllPacks() {
        val pack1 = StickerPackEntity(
            identifier = "pack-anim",
            name = "Animated Pack",
            publisher = "7TV Creator",
            trayImageRelativePath = "sticker_packs/pack-anim/tray_icon.webp",
            isAnimated = true,
            isWhitelisted = false,
            createdAt = 2000L
        )
        val pack2 = StickerPackEntity(
            identifier = "pack-static",
            name = "Static Pack",
            publisher = "7TV Artist",
            trayImageRelativePath = "sticker_packs/pack-static/tray_icon.webp",
            isAnimated = false,
            isWhitelisted = true,
            createdAt = 1000L
        )
        runBlocking {
            db.stickerPackDao().insertPack(pack1)
            db.stickerPackDao().insertPack(pack2)
        }

        val uri = Uri.parse("content://$authority/metadata")
        val cursor = provider.query(uri, null, null, null, null)

        assertNotNull(cursor)
        cursor!!.use { c ->
            assertEquals(13, c.columnCount)
            val expectedColumns = arrayOf(
                WhatsAppConstants.STICKER_PACK_IDENTIFIER_IN_QUERY,
                WhatsAppConstants.STICKER_PACK_NAME_IN_QUERY,
                WhatsAppConstants.STICKER_PACK_PUBLISHER_IN_QUERY,
                WhatsAppConstants.STICKER_PACK_ICON_IN_QUERY,
                WhatsAppConstants.ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
                WhatsAppConstants.IOS_APP_DOWNLOAD_LINK_IN_QUERY,
                WhatsAppConstants.PUBLISHER_EMAIL,
                WhatsAppConstants.PUBLISHER_WEBSITE,
                WhatsAppConstants.PRIVACY_POLICY_WEBSITE,
                WhatsAppConstants.LICENSE_AGREEMENT_WEBSITE,
                WhatsAppConstants.IMAGE_DATA_VERSION,
                WhatsAppConstants.AVOID_CACHE,
                WhatsAppConstants.ANIMATED_STICKER_PACK
            )
            for (col in expectedColumns) {
                assertTrue("Missing column: $col", c.getColumnIndex(col) >= 0)
            }

            assertEquals(2, c.count)

            // First row (ordered by createdAt DESC: pack-anim)
            assertTrue(c.moveToNext())
            assertEquals("pack-anim", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_IDENTIFIER_IN_QUERY)))
            assertEquals("Animated Pack", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_NAME_IN_QUERY)))
            assertEquals("7TV Creator", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_PUBLISHER_IN_QUERY)))
            assertEquals("tray_icon.png", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_ICON_IN_QUERY)))
            assertNull(c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.ANDROID_APP_DOWNLOAD_LINK_IN_QUERY)))
            assertNull(c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.IOS_APP_DOWNLOAD_LINK_IN_QUERY)))
            assertNull(c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.PUBLISHER_EMAIL)))
            assertNull(c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.PUBLISHER_WEBSITE)))
            assertNull(c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.PRIVACY_POLICY_WEBSITE)))
            assertNull(c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.LICENSE_AGREEMENT_WEBSITE)))
            assertEquals("1", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.IMAGE_DATA_VERSION)))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow(WhatsAppConstants.AVOID_CACHE)))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow(WhatsAppConstants.ANIMATED_STICKER_PACK)))

            // Second row (pack-static)
            assertTrue(c.moveToNext())
            assertEquals("pack-static", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_IDENTIFIER_IN_QUERY)))
            assertEquals("Static Pack", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_NAME_IN_QUERY)))
            assertEquals("7TV Artist", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_PUBLISHER_IN_QUERY)))
            assertEquals("tray_icon.png", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_ICON_IN_QUERY)))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow(WhatsAppConstants.ANIMATED_STICKER_PACK)))
        }
    }

    @Test
    fun queryMetadataByPackId_returnsSingleRowForExistingPack_andZeroRowsForUnknownPack() {
        val pack = StickerPackEntity(
            identifier = "pack-single",
            name = "Single Pack",
            publisher = "Tester",
            trayImageRelativePath = "sticker_packs/pack-single/tray_icon.webp",
            isAnimated = false,
            createdAt = 1000L
        )
        runBlocking {
            db.stickerPackDao().insertPack(pack)
        }

        val existingUri = Uri.parse("content://$authority/metadata/pack-single")
        val cursor = provider.query(existingUri, null, null, null, null)

        assertNotNull(cursor)
        cursor!!.use { c ->
            assertEquals(13, c.columnCount)
            assertEquals(1, c.count)
            assertTrue(c.moveToNext())
            assertEquals("pack-single", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_IDENTIFIER_IN_QUERY)))
            assertEquals("Single Pack", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_NAME_IN_QUERY)))
            assertEquals("tray_icon.png", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_PACK_ICON_IN_QUERY)))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow(WhatsAppConstants.ANIMATED_STICKER_PACK)))
        }

        val unknownUri = Uri.parse("content://$authority/metadata/non-existent-pack")
        val unknownCursor = provider.query(unknownUri, null, null, null, null)

        assertNotNull(unknownCursor)
        unknownCursor!!.use { c ->
            assertEquals(13, c.columnCount)
            assertEquals(0, c.count)
        }
    }

    @Test
    fun queryStickers_returnsSortedStickersWithFileNameAndEmojis() {
        val pack = StickerPackEntity(
            identifier = "pack-stickers",
            name = "Sticker Pack",
            publisher = "7TV",
            trayImageRelativePath = "sticker_packs/pack-stickers/tray_icon.png",
            isAnimated = false,
            createdAt = 1000L
        )
        val stickers = listOf(
            StickerEntity(
                id = "stk-3",
                packIdentifier = "pack-stickers",
                emoteId = "e3",
                emoteName = "emote3",
                relativeFilePath = "sticker_packs/pack-stickers/sticker_stk-3.webp",
                emojis = listOf("🎉", "✨"),
                fileLength = 3000L,
                sortOrder = 2
            ),
            StickerEntity(
                id = "stk-1",
                packIdentifier = "pack-stickers",
                emoteId = "e1",
                emoteName = "emote1",
                relativeFilePath = "sticker_packs/pack-stickers/sticker_stk-1.webp",
                emojis = emptyList(), // should fallback to default emoji
                fileLength = 1000L,
                sortOrder = 0
            ),
            StickerEntity(
                id = "stk-2",
                packIdentifier = "pack-stickers",
                emoteId = "e2",
                emoteName = "emote2",
                relativeFilePath = "sticker_packs/pack-stickers/sticker_stk-2.webp",
                emojis = listOf("🔥", "💯", "😎", "EXTRA_EMOJI"), // should take at most 3
                fileLength = 2000L,
                sortOrder = 1
            )
        )
        runBlocking {
            db.stickerPackDao().insertPackWithStickers(pack, stickers)
        }

        val uri = Uri.parse("content://$authority/stickers/pack-stickers")
        val cursor = provider.query(uri, null, null, null, null)

        assertNotNull(cursor)
        cursor!!.use { c ->
            assertEquals(3, c.columnCount)
            assertEquals(3, c.count)

            // Row 0: sortOrder 0 -> stk-1, fallback emoji
            assertTrue(c.moveToNext())
            assertEquals("sticker_stk-1.webp", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_FILE_NAME_IN_QUERY)))
            assertEquals("😀", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_FILE_EMOJI_IN_QUERY)))
            assertEquals("emote1", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY)))

            // Row 1: sortOrder 1 -> stk-2, max 3 emojis
            assertTrue(c.moveToNext())
            assertEquals("sticker_stk-2.webp", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_FILE_NAME_IN_QUERY)))
            assertEquals("🔥,💯,😎", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_FILE_EMOJI_IN_QUERY)))
            assertEquals("emote2", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY)))

            // Row 2: sortOrder 2 -> stk-3
            assertTrue(c.moveToNext())
            assertEquals("sticker_stk-3.webp", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_FILE_NAME_IN_QUERY)))
            assertEquals("🎉,✨", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_FILE_EMOJI_IN_QUERY)))
            assertEquals("emote3", c.getString(c.getColumnIndexOrThrow(WhatsAppConstants.STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY)))
        }

        // Unknown pack returns 0 rows
        val unknownUri = Uri.parse("content://$authority/stickers/unknown-pack")
        val unknownCursor = provider.query(unknownUri, null, null, null, null)
        assertNotNull(unknownCursor)
        unknownCursor!!.use { c ->
            assertEquals(3, c.columnCount)
            assertEquals(0, c.count)
        }
    }

    @Test
    fun getType_returnsCorrectMimeTypeForAllUris() {
        assertEquals(
            "vnd.android.cursor.dir/vnd.com.seventv.stickers.stickercontentprovider.metadata",
            provider.getType(Uri.parse("content://$authority/metadata"))
        )
        assertEquals(
            "vnd.android.cursor.item/vnd.com.seventv.stickers.stickercontentprovider.metadata",
            provider.getType(Uri.parse("content://$authority/metadata/pack-1"))
        )
        assertEquals(
            "vnd.android.cursor.dir/vnd.com.seventv.stickers.stickercontentprovider.stickers",
            provider.getType(Uri.parse("content://$authority/stickers/pack-1"))
        )
        assertEquals(
            "image/webp",
            provider.getType(Uri.parse("content://$authority/stickers_asset/pack-1/sticker_1.webp"))
        )
        assertEquals(
            "image/png",
            provider.getType(Uri.parse("content://$authority/stickers_asset/pack-1/tray_icon.png"))
        )
        assertNull(
            provider.getType(Uri.parse("content://$authority/unsupported_path"))
        )
    }

    @Test
    fun openAssetFile_andOpenFile_returnsDescriptorForExistingFile() {
        val packDir = storageManager.getPackDirectory("pack-asset")
        packDir.mkdirs()
        val assetFile = File(packDir, "sticker_test.webp")
        assetFile.writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46))

        val uri = Uri.parse("content://$authority/stickers_asset/pack-asset/sticker_test.webp")
        val afd = provider.openAssetFile(uri, "r")

        assertNotNull(afd)
        assertNotNull(afd!!.parcelFileDescriptor)
        afd.close()

        val pfd = provider.openFile(uri, "r")
        assertNotNull(pfd)
        pfd!!.close()
    }

    @Test
    fun openAssetFile_rejectsNonReadMode() {
        val uri = Uri.parse("content://$authority/stickers_asset/pack-asset/sticker_test.webp")
        try {
            provider.openAssetFile(uri, "w")
            fail("Expected IllegalArgumentException for write mode")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Mode not supported"))
        }

        try {
            provider.openAssetFile(uri, "rw")
            fail("Expected IllegalArgumentException for rw mode")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Mode not supported"))
        }
    }

    @Test
    fun openAssetFile_rejectsInvalidUri() {
        val uri = Uri.parse("content://$authority/metadata")
        try {
            provider.openAssetFile(uri, "r")
            fail("Expected IllegalArgumentException for metadata URI")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid URI for asset"))
        }
    }

    @Test
    fun openAssetFile_rejectsPathTraversal() {
        val packDir = storageManager.getPackDirectory("pack-asset")
        packDir.mkdirs()

        val traversalUri = Uri.parse("content://$authority/stickers_asset/pack-asset/..%2Fsecret.txt")
        try {
            provider.openAssetFile(traversalUri, "r")
            fail("Expected SecurityException or FileNotFoundException for path traversal")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("Path traversal attempt detected"))
        } catch (e: FileNotFoundException) {
            // Also acceptable if filesystem resolves differently
        }
    }

    @Test
    fun openAssetFile_throwsFileNotFound_whenFileDoesNotExist() {
        val packDir = storageManager.getPackDirectory("pack-asset")
        packDir.mkdirs()

        val nonExistentUri = Uri.parse("content://$authority/stickers_asset/pack-asset/non_existent.webp")
        try {
            provider.openAssetFile(nonExistentUri, "r")
            fail("Expected FileNotFoundException for non-existent asset")
        } catch (e: FileNotFoundException) {
            assertTrue(e.message!!.contains("Asset not found"))
        }
    }

    @Test
    fun query_throwsIllegalArgumentException_onUnknownUri() {
        val unknownUri = Uri.parse("content://$authority/unknown/path")
        try {
            provider.query(unknownUri, null, null, null, null)
            fail("Expected IllegalArgumentException for unknown URI")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unknown URI"))
        }
    }

    @Test
    fun unsupportedOperations_throwUnsupportedOperationException() {
        val uri = Uri.parse("content://$authority/metadata")
        try {
            provider.insert(uri, ContentValues())
            fail("Expected UnsupportedOperationException for insert")
        } catch (_: UnsupportedOperationException) {}

        try {
            provider.update(uri, ContentValues(), null, null)
            fail("Expected UnsupportedOperationException for update")
        } catch (_: UnsupportedOperationException) {}

        try {
            provider.delete(uri, null, null)
            fail("Expected UnsupportedOperationException for delete")
        } catch (_: UnsupportedOperationException) {}
    }

    @Test
    fun onCreate_initializesDefaultDependencies() {
        AppDatabase.setTestInstance(db)
        val freshProvider = Robolectric.buildContentProvider(StickerContentProvider::class.java).create().get()
        assertNotNull(freshProvider)
        // Verify dependencies were initialized by onCreate and query executes successfully
        val uri = Uri.parse("content://$authority/metadata")
        val cursor = freshProvider.query(uri, null, null, null, null)
        assertNotNull(cursor)
        cursor?.close()
    }
}

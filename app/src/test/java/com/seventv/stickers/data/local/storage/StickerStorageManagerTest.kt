package com.seventv.stickers.data.local.storage

import android.content.Context
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FilenameFilter

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StickerStorageManagerTest {

    private lateinit var baseDir: File
    private lateinit var storageManager: StickerStorageManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        baseDir = File(context.filesDir, "test_storage_${System.currentTimeMillis()}")
        baseDir.mkdirs()
        storageManager = DefaultStickerStorageManager(
            baseDir = baseDir,
            dispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        baseDir.deleteRecursively()
    }

    @Test
    fun saveStickerFileWritesBytesAtomically() = runTest(testDispatcher) {
        val packIdentifier = "pack_alpha"
        val stickerId = "emote_123"
        val sampleBytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x10, 0x00, 0x00, 0x00)

        val file = storageManager.saveStickerFile(packIdentifier, stickerId, sampleBytes)

        assertTrue("File should exist on disk", file.exists())
        assertEquals("sticker_emote_123.webp", file.name)
        assertArrayEquals("File content should match input bytes", sampleBytes, file.readBytes())

        val relativePath = file.relativeTo(baseDir).invariantSeparatorsPath
        assertEquals("sticker_packs/pack_alpha/sticker_emote_123.webp", relativePath)
        assertEquals(file.canonicalPath, storageManager.getAbsoluteFile(relativePath).canonicalPath)
    }

    @Test
    fun saveTrayIconWritesTrayFile() = runTest(testDispatcher) {
        val packIdentifier = "pack_alpha"
        val trayBytes = byteArrayOf(0x11, 0x22, 0x33, 0x44)

        val trayFile = storageManager.saveTrayIcon(packIdentifier, trayBytes)

        assertTrue("Tray icon file should exist on disk", trayFile.exists())
        assertEquals("tray_icon.webp", trayFile.name)
        assertArrayEquals("Tray icon content should match input bytes", trayBytes, trayFile.readBytes())

        val relativePath = trayFile.relativeTo(baseDir).invariantSeparatorsPath
        assertEquals("sticker_packs/pack_alpha/tray_icon.webp", relativePath)
        assertEquals(trayFile.canonicalPath, storageManager.getAbsoluteFile(relativePath).canonicalPath)
    }

    @Test
    fun deletePackDirectoryRemovesAllFiles() = runTest(testDispatcher) {
        val packIdentifier = "pack_cleanup"
        val bytes = byteArrayOf(1, 2, 3)

        storageManager.saveTrayIcon(packIdentifier, bytes)
        storageManager.saveStickerFile(packIdentifier, "s1", bytes)
        storageManager.saveStickerFile(packIdentifier, "s2", bytes)

        val packDir = storageManager.getPackDirectory(packIdentifier)
        assertTrue("Pack directory should exist before deletion", packDir.exists())
        assertEquals(3, packDir.listFiles()?.size)

        val deleted = storageManager.deletePackDirectory(packIdentifier)

        assertTrue("deletePackDirectory should return true", deleted)
        assertFalse("Pack directory should no longer exist", packDir.exists())
    }

    @Test
    fun deleteStickerFileRemovesSingleFile() = runTest(testDispatcher) {
        val packIdentifier = "pack_selective"
        val bytes = byteArrayOf(1, 2, 3)

        val trayFile = storageManager.saveTrayIcon(packIdentifier, bytes)
        val sticker1File = storageManager.saveStickerFile(packIdentifier, "s1", bytes)
        val sticker2File = storageManager.saveStickerFile(packIdentifier, "s2", bytes)

        val deleted = storageManager.deleteStickerFile(packIdentifier, "s1")

        assertTrue("deleteStickerFile should return true", deleted)
        assertFalse("Sticker 1 file should be deleted", sticker1File.exists())
        assertTrue("Sticker 2 file should still exist", sticker2File.exists())
        assertTrue("Tray icon file should still exist", trayFile.exists())
    }

    @Test
    fun getAbsoluteFileResolvesCorrectly() {
        val relativePath = "sticker_packs/pack_gamma/tray_icon.webp"
        val resolved = storageManager.getAbsoluteFile(relativePath)

        val expected = File(baseDir, relativePath)
        assertEquals(expected.canonicalPath, resolved.canonicalPath)

        val absoluteTarget = File(baseDir, "custom/absolute_file.webp")
        val resolvedAbsolute = storageManager.getAbsoluteFile(absoluteTarget.absolutePath)
        assertEquals(absoluteTarget.canonicalPath, resolvedAbsolute.canonicalPath)

        val unixPath = "/data/user/0/com.seventv.stickers/files/test.webp"
        val resolvedUnix = storageManager.getAbsoluteFile(unixPath)
        assertEquals(File(unixPath).path, resolvedUnix.path)
    }

    @Test
    fun saveStickerFileOverwritesExistingAtomically() = runTest(testDispatcher) {
        val packIdentifier = "pack_overwrite"
        val stickerId = "s1"
        val initialBytes = byteArrayOf(1, 2, 3)
        val updatedBytes = byteArrayOf(4, 5, 6, 7)

        val initialFile = storageManager.saveStickerFile(packIdentifier, stickerId, initialBytes)
        assertArrayEquals(initialBytes, initialFile.readBytes())

        val updatedFile = storageManager.saveStickerFile(packIdentifier, stickerId, updatedBytes)
        assertEquals(initialFile.canonicalPath, updatedFile.canonicalPath)
        assertArrayEquals(updatedBytes, updatedFile.readBytes())

        val packDir = storageManager.getPackDirectory(packIdentifier)
        val tmpFiles = packDir.listFiles(FilenameFilter { _, name -> name.endsWith(".tmp") }) ?: emptyArray()
        assertTrue("No temporary files should be left over", tmpFiles.isEmpty())
    }

    @Test
    fun deleteNonExistentTargetReturnsFalse() = runTest(testDispatcher) {
        val deletedPack = storageManager.deletePackDirectory("non_existent_pack")
        assertFalse("Deleting non-existent pack directory should return false", deletedPack)

        val deletedSticker = storageManager.deleteStickerFile("non_existent_pack", "non_existent_sticker")
        assertFalse("Deleting non-existent sticker file should return false", deletedSticker)
    }
}

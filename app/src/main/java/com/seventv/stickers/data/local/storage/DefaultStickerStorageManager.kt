package com.seventv.stickers.data.local.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class DefaultStickerStorageManager(
    private val baseDir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : StickerStorageManager {

    companion object {
        const val STICKER_PACKS_DIR_NAME = "sticker_packs"
        const val TRAY_ICON_FILE_NAME = "tray_icon.png"
        const val STICKER_FILE_PREFIX = "sticker_"
        const val WEBP_EXTENSION = ".webp"
    }

    override fun getPackDirectory(packIdentifier: String): File {
        return File(File(baseDir, STICKER_PACKS_DIR_NAME), packIdentifier)
    }

    override suspend fun saveStickerFile(
        packIdentifier: String,
        stickerId: String,
        bytes: ByteArray
    ): File = withContext(dispatcher) {
        val packDir = getPackDirectory(packIdentifier)
        val fileName = "${STICKER_FILE_PREFIX}${stickerId}${WEBP_EXTENSION}"
        val targetFile = File(packDir, fileName)
        writeAtomically(packDir, fileName, targetFile, bytes)
    }

    override suspend fun saveTrayIcon(
        packIdentifier: String,
        bytes: ByteArray
    ): File = withContext(dispatcher) {
        val packDir = getPackDirectory(packIdentifier)
        val targetFile = File(packDir, TRAY_ICON_FILE_NAME)
        writeAtomically(packDir, TRAY_ICON_FILE_NAME, targetFile, bytes)
    }

    private fun writeAtomically(
        packDir: File,
        fileName: String,
        targetFile: File,
        bytes: ByteArray
    ): File {
        if (!packDir.exists()) {
            packDir.mkdirs()
        }
        val tempFile = File(packDir, "$fileName.${UUID.randomUUID()}.tmp")
        try {
            tempFile.outputStream().use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            }
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: UnsupportedOperationException) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            return targetFile
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    override suspend fun deletePackDirectory(packIdentifier: String): Boolean = withContext(dispatcher) {
        val packDir = getPackDirectory(packIdentifier)
        if (!packDir.exists()) {
            false
        } else {
            packDir.deleteRecursively()
        }
    }

    override suspend fun deleteStickerFile(
        packIdentifier: String,
        stickerId: String
    ): Boolean = withContext(dispatcher) {
        val packDir = getPackDirectory(packIdentifier)
        val file = File(packDir, "${STICKER_FILE_PREFIX}${stickerId}${WEBP_EXTENSION}")
        if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    override fun getAbsoluteFile(relativePath: String): File {
        val file = File(relativePath)
        return if (file.isAbsolute || relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            file
        } else {
            File(baseDir, relativePath)
        }
    }
}

package com.seventv.stickers.data.local.storage

import java.io.File

interface StickerStorageManager {
    fun getPackDirectory(packIdentifier: String): File
    suspend fun saveStickerFile(packIdentifier: String, stickerId: String, bytes: ByteArray): File
    suspend fun saveTrayIcon(packIdentifier: String, bytes: ByteArray): File
    suspend fun deletePackDirectory(packIdentifier: String): Boolean
    suspend fun deleteStickerFile(packIdentifier: String, stickerId: String): Boolean
    fun getAbsoluteFile(relativePath: String): File
}

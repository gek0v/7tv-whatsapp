package com.seventv.stickers.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.VisibleForTesting
import com.seventv.stickers.data.local.AppDatabase
import com.seventv.stickers.data.local.storage.DefaultStickerStorageManager
import com.seventv.stickers.data.local.storage.StickerStorageManager
import java.io.File
import java.io.FileNotFoundException

class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.seventv.stickers.stickercontentprovider"

        private const val METADATA_CODE = 1
        private const val METADATA_PACK_CODE = 2
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET_CODE = 4

        private val METADATA_COLUMNS = arrayOf(
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

        private val STICKER_COLUMNS = arrayOf(
            WhatsAppConstants.STICKER_FILE_NAME_IN_QUERY,
            WhatsAppConstants.STICKER_FILE_EMOJI_IN_QUERY,
            WhatsAppConstants.STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY
        )

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "metadata", METADATA_CODE)
            addURI(AUTHORITY, "metadata/*", METADATA_PACK_CODE)
            addURI(AUTHORITY, "stickers/*", STICKERS_CODE)
            addURI(AUTHORITY, "stickers_asset/*/*", STICKERS_ASSET_CODE)
        }
    }

    private lateinit var database: AppDatabase
    private lateinit var storageManager: StickerStorageManager
    private val trayGenerationLock = Any()

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        if (!::database.isInitialized) {
            database = AppDatabase.getInstance(ctx)
        }
        if (!::storageManager.isInitialized) {
            storageManager = DefaultStickerStorageManager(ctx.filesDir)
        }
        return true
    }

    @Synchronized
    private fun ensureInitialized() {
        if (!::database.isInitialized || !::storageManager.isInitialized) {
            val ctx = context ?: throw IllegalStateException("Context is null in StickerContentProvider")
            if (!::database.isInitialized) {
                database = AppDatabase.getInstance(ctx)
            }
            if (!::storageManager.isInitialized) {
                storageManager = DefaultStickerStorageManager(ctx.filesDir)
            }
        }
    }

    @VisibleForTesting
    fun setTestDependencies(testDb: AppDatabase, testStorage: StickerStorageManager) {
        database = testDb
        storageManager = testStorage
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        ensureInitialized()
        val cursor: MatrixCursor = when (uriMatcher.match(uri)) {
            METADATA_CODE -> {
                val c = MatrixCursor(METADATA_COLUMNS)
                val packs = database.stickerPackDao().getAllPacksDirect()
                for (pack in packs) {
                    c.addRow(
                        arrayOf(
                            pack.pack.identifier,
                            pack.pack.name.take(128).ifBlank { "Sticker Pack" },
                            pack.pack.publisher.take(128).ifBlank { "7TV" },
                            "tray_icon.png",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            pack.pack.imageDataVersion.toString(),
                            0,
                            if (pack.pack.isAnimated) 1 else 0
                        )
                    )
                }
                c
            }
            METADATA_PACK_CODE -> {
                val c = MatrixCursor(METADATA_COLUMNS)
                val packId = uri.lastPathSegment ?: ""
                val pack = database.stickerPackDao().getPackByIdentifierDirect(packId)
                if (pack != null) {
                    c.addRow(
                        arrayOf(
                            pack.pack.identifier,
                            pack.pack.name.take(128).ifBlank { "Sticker Pack" },
                            pack.pack.publisher.take(128).ifBlank { "7TV" },
                            "tray_icon.png",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            pack.pack.imageDataVersion.toString(),
                            0,
                            if (pack.pack.isAnimated) 1 else 0
                        )
                    )
                }
                c
            }
            STICKERS_CODE -> {
                val c = MatrixCursor(STICKER_COLUMNS)
                val packId = uri.lastPathSegment ?: ""
                val pack = try {
                    database.stickerPackDao().getPackByIdentifierDirect(packId)
                } catch (_: Exception) {
                    null
                }
                if (pack != null) {
                    val packDir = storageManager.getPackDirectory(packId)
                    val sortedStickers = pack.stickers.sortedBy { it.sortOrder }
                    val validStickers = mutableListOf<Triple<String, String, String>>()
                    for (sticker in sortedStickers) {
                        val fileName = File(sticker.relativeFilePath).name
                        val stickerFile = File(packDir, fileName)
                        if (!stickerFile.exists() || stickerFile.length() <= 0L) {
                            continue
                        }
                        val validEmojis = sticker.emojis.filter { it.isNotBlank() }
                        val emojisString = if (validEmojis.isNotEmpty()) {
                            validEmojis.take(3).joinToString(",")
                        } else {
                            "😀"
                        }
                        val maxA11y = if (pack.pack.isAnimated) 255 else 125
                        val a11yText = sticker.emoteName.take(maxA11y)
                        validStickers.add(Triple(fileName, emojisString, a11yText))
                    }
                    for ((fileName, emojis, a11y) in validStickers) {
                        c.addRow(arrayOf(fileName, emojis, a11y))
                    }
                }
                c
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        context?.contentResolver?.let { cursor.setNotificationUri(it, uri) }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        ensureInitialized()
        val file = resolveAssetFile(uri, mode)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        ensureInitialized()
        val file = resolveAssetFile(uri, mode)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    private fun resolveAssetFile(uri: Uri, mode: String): File {
        val match = uriMatcher.match(uri)
        if (match != STICKERS_ASSET_CODE) {
            throw IllegalArgumentException("Invalid URI for asset: $uri")
        }
        if (mode != "r") {
            throw IllegalArgumentException("Mode not supported: $mode. Only 'r' is allowed.")
        }
        val pathSegments = uri.pathSegments
        if (pathSegments.size < 3) {
            throw IllegalArgumentException("Malformed asset URI: $uri")
        }
        val packIdentifier = pathSegments[1]
        val fileName = pathSegments[2]

        // Strict input sanitization preventing path traversal attacks
        if (!packIdentifier.matches(Regex("^[a-zA-Z0-9_-]+$")) || packIdentifier.contains("..")) {
            throw SecurityException("Path traversal attempt detected in pack identifier: $packIdentifier")
        }
        if (!fileName.matches(Regex("^[a-zA-Z0-9_.-]+$")) || fileName.contains("..")) {
            throw SecurityException("Path traversal attempt detected: $fileName")
        }

        val packDir = storageManager.getPackDirectory(packIdentifier)
        val baseDir = storageManager.getPackDirectory("anchor").parentFile
        if (baseDir != null && !packDir.canonicalPath.startsWith(baseDir.canonicalPath)) {
            throw SecurityException("Pack directory traversal attempt detected: $packIdentifier")
        }

        val file = File(packDir, fileName)
        if (file.canonicalFile.parentFile?.canonicalPath != packDir.canonicalPath) {
            throw SecurityException("Path traversal attempt detected: $fileName")
        }
        if (!file.exists()) {
            if (fileName == "tray_icon.png") {
                synchronized(trayGenerationLock) {
                    if (!file.exists()) {
                        generateTrayIconSafely(packDir, file)
                    }
                }
            }
        }
        if (!file.exists()) {
            throw FileNotFoundException("Asset not found: $fileName in pack $packIdentifier")
        }
        return file
    }

    private fun generateTrayIconSafely(packDir: File, file: File) {
        val legacyWebp = File(packDir, "tray_icon.webp")
        var trayGenerated = false
        if (legacyWebp.exists() && legacyWebp.length() > 0) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(legacyWebp.absolutePath)
            if (bitmap != null) {
                var scaled: android.graphics.Bitmap? = null
                try {
                    scaled = if (bitmap.width != 96 || bitmap.height != 96) {
                        android.graphics.Bitmap.createScaledBitmap(bitmap, 96, 96, true)
                    } else bitmap

                    val tempFile = File.createTempFile("tray_", ".tmp", packDir)
                    try {
                        java.io.FileOutputStream(tempFile).use { out ->
                            scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }
                        if (tempFile.length() > 0L) {
                            if (!tempFile.renameTo(file)) {
                                tempFile.copyTo(file, overwrite = true)
                                tempFile.delete()
                            }
                            trayGenerated = file.exists() && file.length() > 0L
                        } else {
                            tempFile.delete()
                        }
                    } catch (_: Exception) {
                        tempFile.delete()
                    }
                } finally {
                    if (scaled != null && scaled !== bitmap && !scaled.isRecycled) {
                        scaled.recycle()
                    }
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }
        if (!trayGenerated) {
            val stickerFiles = packDir.listFiles { _, name -> name.startsWith("sticker_") && name.endsWith(".webp") }
            val firstSticker = stickerFiles?.firstOrNull { it.length() > 0 }
            if (firstSticker != null) {
                val extractor = com.seventv.stickers.core.media.animated.DefaultFrameExtractor()
                val framesResult = extractor.extractFrames(firstSticker.readBytes())
                if (framesResult is com.seventv.stickers.core.common.result.StickerResult.Success && framesResult.data.isNotEmpty()) {
                    val frames = framesResult.data
                    try {
                        val bmp = frames.first().bitmap
                        val normalizer = com.seventv.stickers.core.media.normalizer.CanvasStickerNormalizer()
                        val trayBmp = normalizer.normalizeToTrayIcon(bmp)
                        try {
                            val tempFile = File.createTempFile("tray_", ".tmp", packDir)
                            try {
                                java.io.FileOutputStream(tempFile).use { out ->
                                    trayBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                }
                                if (tempFile.length() > 0L) {
                                    if (!tempFile.renameTo(file)) {
                                        tempFile.copyTo(file, overwrite = true)
                                        tempFile.delete()
                                    }
                                } else {
                                    tempFile.delete()
                                }
                            } catch (_: Exception) {
                                tempFile.delete()
                            }
                        } finally {
                            if (trayBmp !== bmp && !trayBmp.isRecycled) {
                                trayBmp.recycle()
                            }
                        }
                    } finally {
                        for (frame in frames) {
                            if (!frame.bitmap.isRecycled) {
                                frame.bitmap.recycle()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
            METADATA_PACK_CODE -> "vnd.android.cursor.item/vnd.$AUTHORITY.metadata"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
            STICKERS_ASSET_CODE -> {
                val last = uri.lastPathSegment ?: ""
                if (last.endsWith(".png", ignoreCase = true)) "image/png" else "image/webp"
            }
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert is not supported on StickerContentProvider")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Update is not supported on StickerContentProvider")
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Delete is not supported on StickerContentProvider")
    }
}

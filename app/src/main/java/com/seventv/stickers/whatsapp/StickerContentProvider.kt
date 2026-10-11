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
            WhatsAppConstants.STICKER_FILE_EMOJI_IN_QUERY
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
        return when (uriMatcher.match(uri)) {
            METADATA_CODE -> {
                val cursor = MatrixCursor(METADATA_COLUMNS)
                val packs = database.stickerPackDao().getAllPacksDirect()
                for (pack in packs) {
                    cursor.addRow(
                        arrayOf(
                            pack.pack.identifier,
                            pack.pack.name,
                            pack.pack.publisher,
                            "tray_icon.webp",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "1",
                            0,
                            if (pack.pack.isAnimated) 1 else 0
                        )
                    )
                }
                cursor
            }
            METADATA_PACK_CODE -> {
                val cursor = MatrixCursor(METADATA_COLUMNS)
                val packId = uri.lastPathSegment ?: ""
                val pack = database.stickerPackDao().getPackByIdentifierDirect(packId)
                if (pack != null) {
                    cursor.addRow(
                        arrayOf(
                            pack.pack.identifier,
                            pack.pack.name,
                            pack.pack.publisher,
                            "tray_icon.webp",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "1",
                            0,
                            if (pack.pack.isAnimated) 1 else 0
                        )
                    )
                }
                cursor
            }
            STICKERS_CODE -> {
                val cursor = MatrixCursor(STICKER_COLUMNS)
                val packId = uri.lastPathSegment ?: ""
                val pack = database.stickerPackDao().getPackByIdentifierDirect(packId)
                if (pack != null) {
                    val sortedStickers = pack.stickers.sortedBy { it.sortOrder }
                    for (sticker in sortedStickers) {
                        cursor.addRow(
                            arrayOf(
                                File(sticker.relativeFilePath).name,
                                sticker.emojis.joinToString(",")
                            )
                        )
                    }
                }
                cursor
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
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
        val packDir = storageManager.getPackDirectory(packIdentifier)
        val file = File(packDir, fileName)
        if (file.canonicalFile.parentFile != packDir.canonicalFile) {
            throw SecurityException("Path traversal attempt detected: $fileName")
        }
        if (!file.exists()) {
            throw FileNotFoundException("Asset not found: $fileName in pack $packIdentifier")
        }
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
            METADATA_PACK_CODE -> "vnd.android.cursor.item/vnd.$AUTHORITY.metadata"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
            STICKERS_ASSET_CODE -> "image/webp"
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

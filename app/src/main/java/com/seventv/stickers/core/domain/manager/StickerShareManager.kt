package com.seventv.stickers.core.domain.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.data.repository.EmoteStickerBridge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages direct sharing of individual stickers and emotes without requiring pack creation.
 * Uses AndroidX FileProvider to grant safe read access to WhatsApp and other messaging applications.
 */
class StickerShareManager(
    private val context: Context,
    private val emoteStickerBridge: EmoteStickerBridge,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val FILE_PROVIDER_AUTHORITY = "com.seventv.stickers.fileprovider"
        const val MIME_TYPE_WEBP = "image/webp"
        const val QUICK_SHARE_DIR = "quick_share"
    }

    /**
     * Prepares a remote 7TV emote for direct sharing.
     * Checks if a cached WebP exists in [QUICK_SHARE_DIR]; if not, downloads and converts it
     * into a WhatsApp-compliant 512x512 WebP sticker.
     *
     * @param emote The emote to prepare.
     * @return [StickerResult.Success] containing the content [Uri], or [StickerResult.Failure].
     */
    suspend fun prepareEmoteForShare(emote: SevenTvEmote): StickerResult<Uri> = withContext(dispatcher) {
        val quickShareDir = File(context.cacheDir, QUICK_SHARE_DIR).apply { mkdirs() }
        val cachedFile = File(quickShareDir, "${emote.id}.webp")

        if (cachedFile.exists() && cachedFile.length() > 0) {
            return@withContext try {
                val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, cachedFile)
                StickerResult.Success(uri)
            } catch (e: Exception) {
                StickerResult.Failure(StickerError.StorageIoError(e))
            }
        }

        when (val convertResult = emoteStickerBridge.convertEmoteToSticker(emote, quickShareDir, allowStaticFallback = true)) {
            is StickerResult.Success -> {
                try {
                    val file = convertResult.data.file
                    val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
                    StickerResult.Success(uri)
                } catch (e: Exception) {
                    StickerResult.Failure(StickerError.StorageIoError(e))
                }
            }
            is StickerResult.Failure -> convertResult
        }
    }

    /**
     * Resolves a content [Uri] for an existing local pack sticker using FileProvider.
     *
     * @param sticker The local pack sticker.
     * @return [StickerResult.Success] with the content [Uri], or [StickerResult.Failure].
     */
    fun getShareUriForSticker(sticker: PackSticker): StickerResult<Uri> {
        val candidate = File(sticker.filePath)
        val file = if (candidate.isAbsolute && candidate.exists()) {
            candidate
        } else {
            File(context.filesDir, sticker.filePath)
        }

        if (!file.exists() || file.length() == 0L) {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }

        return try {
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
            StickerResult.Success(uri)
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    /**
     * Creates an [Intent.ACTION_SEND] intent configured for WhatsApp or general messaging apps,
     * attaching the sticker as [MIME_TYPE_WEBP] with URI read permissions.
     *
     * @param uri The content URI provided by FileProvider.
     * @param targetPackage Optional package name (e.g. "com.whatsapp") to target specifically.
     * @return Configured share [Intent].
     */
    fun createShareIntent(
        uri: Uri,
        targetPackage: String? = null
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE_WEBP
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
    }

    /**
     * Creates a chooser [Intent] wrapping the share intent, allowing the user to select
     * their preferred messaging app.
     */
    fun createChooserIntent(
        uri: Uri,
        title: String = "Enviar sticker",
        targetPackage: String? = null
    ): Intent {
        val baseIntent = createShareIntent(uri, targetPackage).apply {
            // Clear package constraint so chooser displays all matching apps
            setPackage(null)
        }
        return Intent.createChooser(baseIntent, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Attempts to launch direct share to WhatsApp, falling back gracefully to the system chooser
     * if WhatsApp is not installed or fails to handle the intent.
     */
    fun launchShare(
        context: Context,
        uri: Uri,
        targetPackage: String? = "com.whatsapp",
        chooserTitle: String = "Enviar sticker"
    ): Boolean {
        val shareIntent = createShareIntent(uri, targetPackage)
        return try {
            context.startActivity(shareIntent)
            true
        } catch (e: Exception) {
            try {
                val chooser = createChooserIntent(uri, chooserTitle)
                context.startActivity(chooser)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}

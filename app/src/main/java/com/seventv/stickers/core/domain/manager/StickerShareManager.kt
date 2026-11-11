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
 * Media payload ready for sharing with external applications.
 */
data class ShareMedia(
    val uri: Uri,
    val mimeType: String,
    val isAnimated: Boolean
)

/**
 * Manages direct sharing of individual stickers and emotes without requiring pack creation.
 * Uses AndroidX FileProvider to grant safe read access to WhatsApp and other messaging applications.
 * For animated emotes, prepares and shares animated GIFs so messaging apps render playback natively.
 */
class StickerShareManager(
    private val context: Context,
    private val emoteStickerBridge: EmoteStickerBridge,
    private val sevenTvRepository: com.seventv.stickers.data.repository.SevenTvRepository? = null,
    private val uriResolver: (Context, String, File) -> Uri = { ctx, auth, file ->
        FileProvider.getUriForFile(ctx, auth, file)
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val FILE_PROVIDER_AUTHORITY = "com.seventv.stickers.fileprovider"
        const val MIME_TYPE_WEBP = "image/webp"
        const val MIME_TYPE_GIF = "image/gif"
        const val QUICK_SHARE_DIR = "quick_share"
    }

    /**
     * Prepares a remote 7TV emote for direct sharing.
     * For animated emotes, downloads the official GIF asset so WhatsApp/messaging apps play it natively.
     * For static emotes, checks or converts into a 512x512 WebP image.
     *
     * @param emote The emote to prepare.
     * @return [StickerResult.Success] containing the [ShareMedia], or [StickerResult.Failure].
     */
    suspend fun prepareEmoteForShare(emote: SevenTvEmote): StickerResult<ShareMedia> = withContext(dispatcher) {
        val quickShareDir = File(context.cacheDir, QUICK_SHARE_DIR).apply { mkdirs() }

        if (emote.isAnimated) {
            val cachedGif = File(quickShareDir, "${emote.id}.gif")
            if (cachedGif.exists() && cachedGif.length() > 0) {
                return@withContext try {
                    val uri = uriResolver(context, FILE_PROVIDER_AUTHORITY, cachedGif)
                    StickerResult.Success(ShareMedia(uri = uri, mimeType = MIME_TYPE_GIF, isAnimated = true))
                } catch (e: Exception) {
                    StickerResult.Failure(StickerError.StorageIoError(e))
                }
            }

            // Attempt to download official 7TV GIF
            val gifResult = sevenTvRepository?.downloadEmoteGif(emote)
            if (gifResult is StickerResult.Success && gifResult.data.isNotEmpty()) {
                return@withContext try {
                    cachedGif.writeBytes(gifResult.data)
                    val uri = uriResolver(context, FILE_PROVIDER_AUTHORITY, cachedGif)
                    StickerResult.Success(ShareMedia(uri = uri, mimeType = MIME_TYPE_GIF, isAnimated = true))
                } catch (e: Exception) {
                    StickerResult.Failure(StickerError.StorageIoError(e))
                }
            }

            // Fallback if GIF download is unavailable
            when (val convertResult = emoteStickerBridge.convertEmoteToSticker(emote, quickShareDir, allowStaticFallback = true)) {
                is StickerResult.Success -> {
                    try {
                        val file = convertResult.data.file
                        val uri = uriResolver(context, FILE_PROVIDER_AUTHORITY, file)
                        StickerResult.Success(ShareMedia(uri = uri, mimeType = MIME_TYPE_WEBP, isAnimated = false))
                    } catch (e: Exception) {
                        StickerResult.Failure(StickerError.StorageIoError(e))
                    }
                }
                is StickerResult.Failure -> convertResult
            }
        } else {
            val cachedWebp = File(quickShareDir, "${emote.id}.webp")
            if (cachedWebp.exists() && cachedWebp.length() > 0) {
                return@withContext try {
                    val uri = uriResolver(context, FILE_PROVIDER_AUTHORITY, cachedWebp)
                    StickerResult.Success(ShareMedia(uri = uri, mimeType = MIME_TYPE_WEBP, isAnimated = false))
                } catch (e: Exception) {
                    StickerResult.Failure(StickerError.StorageIoError(e))
                }
            }

            when (val convertResult = emoteStickerBridge.convertEmoteToSticker(emote, quickShareDir, allowStaticFallback = false)) {
                is StickerResult.Success -> {
                    try {
                        val file = convertResult.data.file
                        val uri = uriResolver(context, FILE_PROVIDER_AUTHORITY, file)
                        StickerResult.Success(ShareMedia(uri = uri, mimeType = MIME_TYPE_WEBP, isAnimated = false))
                    } catch (e: Exception) {
                        StickerResult.Failure(StickerError.StorageIoError(e))
                    }
                }
                is StickerResult.Failure -> convertResult
            }
        }
    }

    /**
     * Prepares an existing local pack sticker for sharing.
     * If animated, checks or downloads the corresponding GIF asset; otherwise shares the local WebP file.
     */
    suspend fun prepareStickerForShare(
        sticker: PackSticker,
        isAnimated: Boolean
    ): StickerResult<ShareMedia> = withContext(dispatcher) {
        val quickShareDir = File(context.cacheDir, QUICK_SHARE_DIR).apply { mkdirs() }

        if (isAnimated) {
            val cachedGif = File(quickShareDir, "${sticker.emoteId}.gif")
            if (cachedGif.exists() && cachedGif.length() > 0) {
                return@withContext try {
                    val uri = uriResolver(context, FILE_PROVIDER_AUTHORITY, cachedGif)
                    StickerResult.Success(ShareMedia(uri = uri, mimeType = MIME_TYPE_GIF, isAnimated = true))
                } catch (e: Exception) {
                    StickerResult.Failure(StickerError.StorageIoError(e))
                }
            }

            val dummyEmote = SevenTvEmote(
                id = sticker.emoteId,
                name = sticker.emoteName,
                isAnimated = true,
                isZeroWidth = false,
                isNsfw = false,
                bestAssetUrl = "",
                availableScales = emptyList()
            )
            val gifResult = sevenTvRepository?.downloadEmoteGif(dummyEmote)
            if (gifResult is StickerResult.Success && gifResult.data.isNotEmpty()) {
                return@withContext try {
                    cachedGif.writeBytes(gifResult.data)
                    val uri = uriResolver(context, FILE_PROVIDER_AUTHORITY, cachedGif)
                    StickerResult.Success(ShareMedia(uri = uri, mimeType = MIME_TYPE_GIF, isAnimated = true))
                } catch (e: Exception) {
                    StickerResult.Failure(StickerError.StorageIoError(e))
                }
            }
        }

        val candidate = File(sticker.filePath)
        val file = if (candidate.isAbsolute && candidate.exists()) {
            candidate
        } else {
            File(context.filesDir, sticker.filePath)
        }

        if (!file.exists() || file.length() == 0L) {
            return@withContext StickerResult.Failure(StickerError.ResourceNotFound)
        }

        try {
            val uri = uriResolver(context, FILE_PROVIDER_AUTHORITY, file)
            StickerResult.Success(ShareMedia(uri = uri, mimeType = MIME_TYPE_WEBP, isAnimated = isAnimated))
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    /**
     * Resolves a content [Uri] for an existing local pack sticker using FileProvider.
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
            val uri = uriResolver(context, FILE_PROVIDER_AUTHORITY, file)
            StickerResult.Success(uri)
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }

    /**
     * Creates an [Intent.ACTION_SEND] intent configured for WhatsApp or general messaging apps,
     * attaching the file with specified MIME type and URI read permissions.
     */
    fun createShareIntent(
        uri: Uri,
        mimeType: String = MIME_TYPE_WEBP,
        targetPackage: String? = null
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
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
        mimeType: String = MIME_TYPE_WEBP,
        title: String = "Compartir imagen WebP",
        targetPackage: String? = null
    ): Intent {
        val baseIntent = createShareIntent(uri, mimeType, targetPackage).apply {
            setPackage(null)
        }
        return Intent.createChooser(baseIntent, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Launches the system share chooser for a [ShareMedia] item.
     */
    fun launchShare(
        context: Context,
        shareMedia: ShareMedia,
        chooserTitle: String? = null
    ): Boolean {
        val title = chooserTitle ?: if (shareMedia.isAnimated) "Compartir GIF animado" else "Compartir imagen WebP"
        return try {
            val chooser = createChooserIntent(shareMedia.uri, shareMedia.mimeType, title)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launches the system share chooser for a given [Uri].
     */
    fun launchShare(
        context: Context,
        uri: Uri,
        mimeType: String = MIME_TYPE_WEBP,
        chooserTitle: String = "Compartir"
    ): Boolean {
        return try {
            val chooser = createChooserIntent(uri, mimeType, chooserTitle)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }
}


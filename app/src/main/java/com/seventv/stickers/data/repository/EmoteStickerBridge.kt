package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.StickerMediaPipeline
import com.seventv.stickers.core.media.model.ProcessedSticker
import com.seventv.stickers.core.media.model.RawMediaInput
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.UnifiedEmote
import com.seventv.stickers.core.network.model.toSevenTvEmote
import com.seventv.stickers.core.network.model.toUnifiedEmote
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Bridge between emote repositories/downloader and StickerMediaPipeline.
 * Coordinates downloading emote binary assets from any supported provider (7TV, BTTV, FFZ, Twitch)
 * and processing them into compliant WhatsApp stickers, with optional static fallback for oversized animated emotes.
 */
class EmoteStickerBridge(
    private val repository: SevenTvRepository,
    private val mediaPipeline: StickerMediaPipeline,
    private val emoteDownloader: EmoteDownloader? = null
) {

    suspend fun convertEmoteToSticker(
        emote: SevenTvEmote,
        outputDir: File,
        allowStaticFallback: Boolean = true
    ): StickerResult<ProcessedSticker> {
        return convertEmoteToSticker(
            emote = emote.toUnifiedEmote(),
            outputDir = outputDir,
            allowStaticFallback = allowStaticFallback
        )
    }

    suspend fun convertEmoteToSticker(
        emote: UnifiedEmote,
        outputDir: File,
        allowStaticFallback: Boolean = true
    ): StickerResult<ProcessedSticker> {
        return try {
            val downloadResult = if (emote.provider != EmoteProvider.SEVENTV && emoteDownloader != null) {
                emoteDownloader.downloadEmote(emote)
            } else {
                repository.downloadEmote(emote.toSevenTvEmote())
            }

            val asset = when (downloadResult) {
                is StickerResult.Success -> downloadResult.data
                is StickerResult.Failure -> return downloadResult
            }

            val mimeType = when (asset.format.uppercase()) {
                "GIF" -> "image/gif"
                "PNG" -> "image/png"
                "JPEG", "JPG" -> "image/jpeg"
                else -> "image/webp"
            }

            val rawInput = RawMediaInput(
                bytes = asset.bytes,
                isAnimated = asset.isAnimated,
                mimeType = mimeType
            )

            val processResult = mediaPipeline.processSticker(rawInput, outputDir)
            if (processResult is StickerResult.Failure && allowStaticFallback && asset.isAnimated) {
                mediaPipeline.processAsStaticFallback(rawInput, outputDir)
            } else {
                processResult
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StickerResult.Failure(StickerError.StorageIoError(e))
        }
    }
}

package com.seventv.stickers.data.repository

import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.StickerMediaPipeline
import com.seventv.stickers.core.media.model.ProcessedSticker
import com.seventv.stickers.core.media.model.RawMediaInput
import com.seventv.stickers.core.network.model.SevenTvEmote
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Bridge between SevenTvRepository and StickerMediaPipeline.
 * Coordinates downloading emote binary assets and processing them into compliant WhatsApp stickers,
 * with optional static fallback for oversized animated emotes.
 */
class EmoteStickerBridge(
    private val repository: SevenTvRepository,
    private val mediaPipeline: StickerMediaPipeline
) {

    /**
     * Downloads and converts a 7TV emote into a WhatsApp-compliant sticker.
     *
     * @param emote The emote domain model to download and convert.
     * @param outputDir Destination directory for the generated sticker WebP file.
     * @param allowStaticFallback If true, an animated emote failing size constraints will fall back to a static sticker.
     * @return [StickerResult.Success] containing the [ProcessedSticker], or [StickerResult.Failure].
     */
    suspend fun convertEmoteToSticker(
        emote: SevenTvEmote,
        outputDir: File,
        allowStaticFallback: Boolean = true
    ): StickerResult<ProcessedSticker> {
        return try {
            val downloadResult = repository.downloadEmote(emote)
            val asset = when (downloadResult) {
                is StickerResult.Success -> downloadResult.data
                is StickerResult.Failure -> return downloadResult
            }

            val mimeType = when (asset.format.uppercase()) {
                "GIF" -> "image/gif"
                "PNG" -> "image/png"
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

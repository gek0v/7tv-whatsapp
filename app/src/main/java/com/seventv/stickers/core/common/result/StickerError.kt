package com.seventv.stickers.core.common.result

/**
 * Domain error hierarchy representing failure modes across the sticker media pipeline.
 */
sealed interface StickerError {
    data object CorruptedSourceData : StickerError
    data class UnsupportedFormat(val mimeType: String) : StickerError
    data class ExceedsSizeLimit(
        val actualBytes: Long = 0L,
        val maxBytes: Long = 0L,
        val canFallbackToStatic: Boolean = false
    ) : StickerError
    data class NativeEncodingFailed(val details: String) : StickerError
    data class StorageIoError(val cause: Throwable) : StickerError
}

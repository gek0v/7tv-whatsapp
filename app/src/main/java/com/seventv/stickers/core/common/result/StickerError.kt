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
    data class StorageIoError(val cause: Throwable) : StickerError {
        constructor(message: String) : this(Exception(message))
    }

    // Network & API error cases
    data class NetworkError(val statusCode: Int? = null, val message: String) : StickerError
    data object ResourceNotFound : StickerError
    data object RateLimited : StickerError

    // Pack & Persistence error cases
    data class InsufficientStickers(val actual: Int, val min: Int = 3) : StickerError
    data class ExceedsPackLimit(val count: Int, val max: Int = 30) : StickerError
    data class PackNotFound(val identifier: String) : StickerError
}

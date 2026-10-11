package com.seventv.stickers.core.media.model

/**
 * Raw media payload received from the network or local storage before processing.
 */
data class RawMediaInput(
    val bytes: ByteArray,
    val isAnimated: Boolean = false,
    val mimeType: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawMediaInput

        if (!bytes.contentEquals(other.bytes)) return false
        if (isAnimated != other.isAnimated) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + isAnimated.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }
}

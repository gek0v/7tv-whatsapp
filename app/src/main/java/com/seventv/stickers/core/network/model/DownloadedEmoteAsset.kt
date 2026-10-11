package com.seventv.stickers.core.network.model

/**
 * Domain model representing a downloaded raw emote binary asset.
 */
data class DownloadedEmoteAsset(
    val emoteId: String,
    val emoteName: String,
    val bytes: ByteArray,
    val isAnimated: Boolean,
    val format: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DownloadedEmoteAsset

        if (emoteId != other.emoteId) return false
        if (emoteName != other.emoteName) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (isAnimated != other.isAnimated) return false
        if (format != other.format) return false

        return true
    }

    override fun hashCode(): Int {
        var result = emoteId.hashCode()
        result = 31 * result + emoteName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + isAnimated.hashCode()
        result = 31 * result + format.hashCode()
        return result
    }
}

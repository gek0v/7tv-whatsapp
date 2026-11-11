package com.seventv.stickers.core.network.mapper

import com.seventv.stickers.core.network.dto.SevenTvEmoteDto
import com.seventv.stickers.core.network.dto.SevenTvEmoteSetDto
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet

/**
 * Mapper transforming 7TV network DTOs into clean domain models.
 */
object SevenTvMapper {

    private const val FLAG_ZERO_WIDTH = 1
    private const val FLAG_NSFW = 1 shl 16

    /**
     * Maps a [SevenTvEmoteDto] to domain [SevenTvEmote] using a single pass over host files.
     */
    fun toDomain(dto: SevenTvEmoteDto): SevenTvEmote {
        val host = dto.host ?: dto.data?.host
        val files = host?.files.orEmpty()
        val availableScales = ArrayList<String>(files.size)

        // Priority ranks: higher index wins
        // Best WEBP: 4x > 3x > 2x > 1x
        var bestWebpRank = 0
        var bestWebpName: String? = null
        // Preview WEBP: 2x > 1x > 3x > 4x
        var previewWebpRank = 0
        var previewWebpName: String? = null
        // Best GIF: 4x > 3x > 2x > 1x
        var bestGifRank = 0
        var bestGifName: String? = null

        var firstFileName: String? = null
        for (i in files.indices) {
            val fileName = files[i].name
            availableScales.add(fileName)
            if (firstFileName == null) firstFileName = fileName

            when {
                fileName.equals("4x.webp", ignoreCase = true) -> {
                    if (bestWebpRank < 4) { bestWebpRank = 4; bestWebpName = fileName }
                    if (previewWebpRank < 1) { previewWebpRank = 1; previewWebpName = fileName }
                }
                fileName.equals("3x.webp", ignoreCase = true) -> {
                    if (bestWebpRank < 3) { bestWebpRank = 3; bestWebpName = fileName }
                    if (previewWebpRank < 2) { previewWebpRank = 2; previewWebpName = fileName }
                }
                fileName.equals("2x.webp", ignoreCase = true) -> {
                    if (bestWebpRank < 2) { bestWebpRank = 2; bestWebpName = fileName }
                    if (previewWebpRank < 4) { previewWebpRank = 4; previewWebpName = fileName }
                }
                fileName.equals("1x.webp", ignoreCase = true) -> {
                    if (bestWebpRank < 1) { bestWebpRank = 1; bestWebpName = fileName }
                    if (previewWebpRank < 3) { previewWebpRank = 3; previewWebpName = fileName }
                }
                fileName.equals("4x.gif", ignoreCase = true) -> {
                    if (bestGifRank < 4) { bestGifRank = 4; bestGifName = fileName }
                }
                fileName.equals("3x.gif", ignoreCase = true) -> {
                    if (bestGifRank < 3) { bestGifRank = 3; bestGifName = fileName }
                }
                fileName.equals("2x.gif", ignoreCase = true) -> {
                    if (bestGifRank < 2) { bestGifRank = 2; bestGifName = fileName }
                }
                fileName.equals("1x.gif", ignoreCase = true) -> {
                    if (bestGifRank < 1) { bestGifRank = 1; bestGifName = fileName }
                }
            }
        }

        val targetFileName = bestWebpName ?: firstFileName ?: "4x.webp"
        val preferredPreviewScale = previewWebpName ?: targetFileName

        val isZeroWidth = ((dto.flags and FLAG_ZERO_WIDTH) != 0) ||
            (((dto.data?.flags ?: 0) and FLAG_ZERO_WIDTH) != 0)

        val isNsfw = ((dto.flags and FLAG_NSFW) != 0) ||
            (((dto.data?.flags ?: 0) and FLAG_NSFW) != 0)

        val isAnimated = dto.animated || (dto.data?.animated == true)

        val rawHostUrl = host?.url
        val normalizedBase = if (rawHostUrl.isNullOrBlank()) null else normalizeBaseUrl(rawHostUrl)

        val bestAssetUrl = if (normalizedBase == null) "" else "$normalizedBase/$targetFileName"
        val previewUrl = if (normalizedBase == null) "" else "$normalizedBase/$preferredPreviewScale"
        val gifUrl = if (isAnimated && normalizedBase != null) {
            val gifFile = bestGifName ?: "4x.gif"
            "$normalizedBase/$gifFile"
        } else {
            null
        }

        val emoteId = dto.id.ifEmpty { dto.data?.id.orEmpty() }
        val emoteName = dto.name.ifEmpty { dto.data?.name.orEmpty() }

        return SevenTvEmote(
            id = emoteId,
            name = emoteName,
            isAnimated = isAnimated,
            isZeroWidth = isZeroWidth,
            isNsfw = isNsfw,
            bestAssetUrl = bestAssetUrl,
            availableScales = availableScales,
            previewUrl = previewUrl,
            gifUrl = gifUrl
        )
    }

    /**
     * Maps a [SevenTvEmoteSetDto] to domain [SevenTvEmoteSet].
     */
    fun toDomain(dto: SevenTvEmoteSetDto): SevenTvEmoteSet {
        val emotes = dto.emotes
        val mapped = ArrayList<SevenTvEmote>(emotes.size)
        for (i in emotes.indices) {
            mapped.add(toDomain(emotes[i]))
        }
        return SevenTvEmoteSet(
            id = dto.id,
            name = dto.name,
            ownerName = dto.ownerName,
            emotes = mapped
        )
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = if (baseUrl.startsWith("//")) "https:$baseUrl" else baseUrl
        return trimmed.trimEnd('/')
    }

    /**
     * Normalizes a CDN URL by ensuring the https protocol and joining the file name safely.
     */
    fun normalizeCdnUrl(baseUrl: String, fileName: String): String {
        if (baseUrl.isBlank()) return ""

        val normalizedBase = normalizeBaseUrl(baseUrl)
        val cleanFileName = fileName.trim().trimStart('/')
        return if (cleanFileName.isEmpty()) {
            normalizedBase
        } else {
            "$normalizedBase/$cleanFileName"
        }
    }
}

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
    private val PREFERRED_SCALES = listOf("4x.webp", "3x.webp", "2x.webp", "1x.webp")
    private val PREFERRED_PREVIEW_SCALES = listOf("2x.webp", "1x.webp", "3x.webp", "4x.webp")

    /**
     * Maps a [SevenTvEmoteDto] to domain [SevenTvEmote].
     */
    fun toDomain(dto: SevenTvEmoteDto): SevenTvEmote {
        val host = dto.host ?: dto.data?.host
        val files = host?.files.orEmpty()
        val availableScales = files.map { it.name }

        val preferredScale = PREFERRED_SCALES.firstOrNull { preferred ->
            files.any { it.name.equals(preferred, ignoreCase = true) }
        }
        val targetFileName = preferredScale ?: files.firstOrNull()?.name ?: "4x.webp"

        val bestAssetUrl = if (host?.url.isNullOrBlank()) {
            ""
        } else {
            normalizeCdnUrl(host.url, targetFileName)
        }

        val preferredPreviewScale = PREFERRED_PREVIEW_SCALES.firstOrNull { preferred ->
            files.any { it.name.equals(preferred, ignoreCase = true) }
        } ?: targetFileName

        val previewUrl = if (host?.url.isNullOrBlank()) {
            ""
        } else {
            normalizeCdnUrl(host.url, preferredPreviewScale)
        }

        val isZeroWidth = ((dto.flags and FLAG_ZERO_WIDTH) != 0) ||
            (((dto.data?.flags ?: 0) and FLAG_ZERO_WIDTH) != 0)

        val isNsfw = ((dto.flags and FLAG_NSFW) != 0) ||
            (((dto.data?.flags ?: 0) and FLAG_NSFW) != 0)

        val isAnimated = dto.animated || (dto.data?.animated == true)

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
            previewUrl = previewUrl
        )
    }

    /**
     * Maps a [SevenTvEmoteSetDto] to domain [SevenTvEmoteSet].
     */
    fun toDomain(dto: SevenTvEmoteSetDto): SevenTvEmoteSet {
        return SevenTvEmoteSet(
            id = dto.id,
            name = dto.name,
            ownerName = dto.ownerName,
            emotes = dto.emotes.map { toDomain(it) }
        )
    }

    /**
     * Normalizes a CDN URL by ensuring the https protocol and joining the file name safely.
     */
    fun normalizeCdnUrl(baseUrl: String, fileName: String): String {
        if (baseUrl.isBlank()) return ""

        val normalizedBase = if (baseUrl.startsWith("//")) {
            "https:$baseUrl"
        } else {
            baseUrl
        }.trimEnd('/')

        val cleanFileName = fileName.trim().trimStart('/')
        return if (cleanFileName.isEmpty()) {
            normalizedBase
        } else {
            "$normalizedBase/$cleanFileName"
        }
    }
}

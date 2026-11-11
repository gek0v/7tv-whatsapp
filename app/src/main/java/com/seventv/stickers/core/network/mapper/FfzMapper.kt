package com.seventv.stickers.core.network.mapper

import com.seventv.stickers.core.network.dto.FfzEmoticonDto
import com.seventv.stickers.core.network.dto.FfzGlobalResponseDto
import com.seventv.stickers.core.network.dto.FfzRoomResponseDto
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote

object FfzMapper {

    private const val CDN_BASE = "https://cdn.frankerfacez.com/emoticon"

    fun toDomain(dto: FfzEmoticonDto, ownerFallback: String? = null): UnifiedEmote {
        val isAnimated = !dto.animated.isNullOrEmpty()
        val rawBest = if (isAnimated) {
            dto.animated?.get("4")
                ?: dto.animated?.get("2")
                ?: dto.animated?.get("1")
                ?: "$CDN_BASE/${dto.id}/animated/4"
        } else {
            dto.urls["4"]
                ?: dto.urls["2"]
                ?: dto.urls["1"]
                ?: "$CDN_BASE/${dto.id}/4"
        }

        val rawPreview = if (isAnimated) {
            dto.animated?.get("2")
                ?: dto.animated?.get("1")
                ?: rawBest
        } else {
            dto.urls["2"]
                ?: dto.urls["1"]
                ?: rawBest
        }

        val owner = dto.owner?.displayName?.ifBlank { null }
            ?: dto.owner?.name?.ifBlank { null }
            ?: ownerFallback?.ifBlank { null }

        return UnifiedEmote(
            id = dto.id.toString(),
            name = dto.name,
            provider = EmoteProvider.FRANKERFACEZ,
            isAnimated = isAnimated,
            isZeroWidth = dto.modifier,
            isNsfw = false,
            bestAssetUrl = normalizeUrl(rawBest),
            previewUrl = normalizeUrl(rawPreview),
            gifUrl = null,
            originalOwner = owner
        )
    }

    fun toRoomEmotes(response: FfzRoomResponseDto): List<UnifiedEmote> {
        val roomOwner = response.room?.displayName
        val primarySetId = response.room?.set?.toString()
        val orderedSets = buildList {
            if (primarySetId != null) {
                response.sets[primarySetId]?.let { add(it) }
            }
            response.sets.forEach { (key, set) ->
                if (key != primarySetId) add(set)
            }
        }
        return orderedSets
            .flatMap { set -> set.emoticons }
            .filterNot { it.hidden }
            .map { toDomain(it, roomOwner) }
            .distinctBy { it.id }
    }

    fun toGlobalEmotes(response: FfzGlobalResponseDto): List<UnifiedEmote> {
        val targetKeys = if (response.defaultSets.isNotEmpty()) {
            response.defaultSets.map { it.toString() }
        } else {
            response.sets.keys.toList()
        }
        return targetKeys
            .mapNotNull { key -> response.sets[key] }
            .flatMap { it.emoticons }
            .filterNot { it.hidden }
            .map { toDomain(it) }
            .distinctBy { it.id }
    }

    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> trimmed
        }
    }
}

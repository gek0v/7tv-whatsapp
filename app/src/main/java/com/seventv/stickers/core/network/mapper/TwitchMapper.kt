package com.seventv.stickers.core.network.mapper

import com.seventv.stickers.core.network.dto.TwitchChannelEmotesResponseDto
import com.seventv.stickers.core.network.dto.TwitchEmoteDto
import com.seventv.stickers.core.network.dto.TwitchEmoteSetDto
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote

object TwitchMapper {

    private const val CDN_BASE = "https://static-cdn.jtvnw.net/emoticons/v2"

    fun toDomain(dto: TwitchEmoteDto, ownerFallback: String? = null): UnifiedEmote {
        val isAnimated = dto.assetType.equals("ANIMATED", ignoreCase = true)
        val formatSegment = if (isAnimated) "animated" else "default"
        return UnifiedEmote(
            id = dto.id,
            name = dto.code,
            provider = EmoteProvider.TWITCH,
            isAnimated = isAnimated,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "$CDN_BASE/${dto.id}/$formatSegment/dark/3.0",
            previewUrl = "$CDN_BASE/${dto.id}/$formatSegment/dark/2.0",
            gifUrl = null,
            originalOwner = ownerFallback?.ifBlank { null }
        )
    }

    fun toChannelEmotes(response: TwitchChannelEmotesResponseDto): List<UnifiedEmote> {
        val owner = response.channelName?.ifBlank { null } ?: response.channelLogin
        val allDtos = response.emoteSets.flatMap { it.emotes + it.emoteList } +
            response.subscriberEmotes.flatMap { it.emotes + it.emoteList } +
            response.subProducts.flatMap { it.emotes } +
            response.localEmotes.flatMap { it.emotes } +
            response.bitEmotes
        return allDtos
            .map { toDomain(it, owner) }
            .distinctBy { it.id }
    }

    fun toGlobalEmotes(sets: List<TwitchEmoteSetDto>): List<UnifiedEmote> {
        return sets
            .flatMap { it.emoteList + it.emotes }
            .map { toDomain(it, "Twitch") }
            .distinctBy { it.id }
    }
}

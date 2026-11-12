package com.seventv.stickers.core.network.mapper

import com.seventv.stickers.core.network.dto.BttvEmoteDto
import com.seventv.stickers.core.network.dto.BttvTrendingEmoteDto
import com.seventv.stickers.core.network.dto.BttvUserResponseDto
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.UnifiedEmote

object BttvMapper {

    private const val CDN_BASE = "https://cdn.betterttv.net/emote"

    fun toDomain(dto: BttvEmoteDto, ownerFallback: String? = null): UnifiedEmote {
        val isAnimated = dto.animated || dto.imageType.equals("gif", ignoreCase = true)
        val emoteCreator = dto.user?.displayName?.ifBlank { null }
            ?: dto.user?.name?.ifBlank { null }
            ?: ownerFallback?.ifBlank { null }

        return UnifiedEmote(
            id = dto.id,
            name = dto.code,
            provider = EmoteProvider.BETTERTTV,
            isAnimated = isAnimated,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "$CDN_BASE/${dto.id}/3x.webp",
            previewUrl = "$CDN_BASE/${dto.id}/2x.webp",
            gifUrl = if (isAnimated) "$CDN_BASE/${dto.id}/3x.gif" else null,
            originalOwner = emoteCreator
        )
    }

    fun toDomain(dto: BttvTrendingEmoteDto): UnifiedEmote = toDomain(dto.emote)

    fun toChannelEmotes(
        response: BttvUserResponseDto,
        channelOwner: String? = null
    ): List<UnifiedEmote> {
        val channelList = response.channelEmotes.map { toDomain(it, channelOwner) }
        val sharedList = response.sharedEmotes.map { toDomain(it, channelOwner) }
        return (channelList + sharedList).distinctBy { it.id }
    }
}

package com.seventv.stickers.core.network.mapper

import com.seventv.stickers.core.network.dto.TwitchChannelEmotesResponseDto
import com.seventv.stickers.core.network.dto.TwitchEmoteDto
import com.seventv.stickers.core.network.dto.TwitchEmoteSetDto
import com.seventv.stickers.core.network.dto.TwitchLocalEmoteGroupDto
import com.seventv.stickers.core.network.dto.TwitchSubProductDto
import com.seventv.stickers.core.network.model.EmoteProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchMapperTest {

    @Test
    fun toDomain_mapsStaticAndAnimatedTwitchEmotes() {
        val staticDto = TwitchEmoteDto(id = "25", code = "Kappa", assetType = "STATIC")
        val animatedDto = TwitchEmoteDto(id = "emotesv2_abc", code = "rubiusDance", assetType = "ANIMATED")

        val staticEmote = TwitchMapper.toDomain(staticDto, "Twitch")
        val animatedEmote = TwitchMapper.toDomain(animatedDto, "Rubius")

        assertEquals(EmoteProvider.TWITCH, staticEmote.provider)
        assertFalse(staticEmote.isAnimated)
        assertEquals("https://static-cdn.jtvnw.net/emoticons/v2/25/default/dark/3.0", staticEmote.bestAssetUrl)
        assertEquals("https://static-cdn.jtvnw.net/emoticons/v2/25/default/dark/2.0", staticEmote.previewUrl)

        assertEquals(EmoteProvider.TWITCH, animatedEmote.provider)
        assertTrue(animatedEmote.isAnimated)
        assertEquals("https://static-cdn.jtvnw.net/emoticons/v2/emotesv2_abc/animated/dark/3.0", animatedEmote.bestAssetUrl)
        assertEquals("Rubius", animatedEmote.originalOwner)
    }

    @Test
    fun toChannelEmotes_combinesAllSetsAndDeduplicates() {
        val response = TwitchChannelEmotesResponseDto(
            channelName = "IlloJuan",
            emoteSets = listOf(
                TwitchEmoteSetDto(
                    setID = "s1",
                    emotes = listOf(TwitchEmoteDto(id = "1", code = "illo1"))
                )
            ),
            subscriberEmotes = listOf(
                TwitchEmoteSetDto(
                    setID = "s2",
                    emotes = listOf(
                        TwitchEmoteDto(id = "1", code = "illo1_dup"),
                        TwitchEmoteDto(id = "2", code = "illo2", assetType = "ANIMATED")
                    )
                )
            ),
            bitEmotes = listOf(TwitchEmoteDto(id = "3", code = "illoBit"))
        )

        val result = TwitchMapper.toChannelEmotes(response)
        assertEquals(3, result.size)
        assertEquals(listOf("1", "2", "3"), result.map { it.id })
        assertTrue(result.all { it.originalOwner == "IlloJuan" })
    }

    @Test
    fun toChannelEmotes_parsesIvrSubProductsAndLocalEmotes() {
        val response = TwitchChannelEmotesResponseDto(
            channelName = "xQc",
            channelLogin = "xqc",
            subProducts = listOf(
                TwitchSubProductDto(
                    id = "1000",
                    tier = "1000",
                    emotes = listOf(
                        TwitchEmoteDto(id = "300", code = "xqcL", assetType = "STATIC")
                    )
                )
            ),
            localEmotes = listOf(
                TwitchLocalEmoteGroupDto(
                    id = "follower",
                    type = "FOLLOWER",
                    emotes = listOf(
                        TwitchEmoteDto(id = "301", code = "xqcDance", assetType = "ANIMATED"),
                        TwitchEmoteDto(id = "300", code = "xqcL_dup", assetType = "STATIC")
                    )
                )
            ),
            bitEmotes = listOf(
                TwitchEmoteDto(id = "302", code = "xqcBit", assetType = "STATIC")
            )
        )

        val result = TwitchMapper.toChannelEmotes(response)
        assertEquals(listOf("300", "301", "302"), result.map { it.id })
        assertTrue(result[1].isAnimated)
        assertTrue(result.all { it.originalOwner == "xQc" })
    }

    @Test
    fun toGlobalEmotes_parsesEmoteListFromIvrSetsEndpoint() {
        val sets = listOf(
            TwitchEmoteSetDto(
                setID = "0",
                emoteList = listOf(
                    TwitchEmoteDto(id = "25", code = "Kappa", assetType = "STATIC"),
                    TwitchEmoteDto(id = "425618", code = "LUL", assetType = "STATIC")
                )
            )
        )

        val result = TwitchMapper.toGlobalEmotes(sets)
        assertEquals(listOf("25", "425618"), result.map { it.id })
        assertTrue(result.all { it.originalOwner == "Twitch" })
    }
}

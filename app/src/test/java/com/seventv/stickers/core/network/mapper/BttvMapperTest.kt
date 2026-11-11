package com.seventv.stickers.core.network.mapper

import com.seventv.stickers.core.network.dto.BttvEmoteDto
import com.seventv.stickers.core.network.dto.BttvTrendingEmoteDto
import com.seventv.stickers.core.network.dto.BttvUserRefDto
import com.seventv.stickers.core.network.dto.BttvUserResponseDto
import com.seventv.stickers.core.network.model.EmoteProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BttvMapperTest {

    @Test
    fun toDomain_mapsStaticEmoteCorrectly() {
        val dto = BttvEmoteDto(
            id = "54fa8f1401e468494b85b537",
            code = "SourPls",
            imageType = "png",
            animated = false,
            user = BttvUserRefDto(name = "bttvuser", displayName = "BttvUser")
        )

        val result = BttvMapper.toDomain(dto)

        assertEquals("54fa8f1401e468494b85b537", result.id)
        assertEquals("SourPls", result.name)
        assertEquals(EmoteProvider.BETTERTTV, result.provider)
        assertFalse(result.isAnimated)
        assertFalse(result.isZeroWidth)
        assertFalse(result.isNsfw)
        assertEquals("https://cdn.betterttv.net/emote/54fa8f1401e468494b85b537/3x.webp", result.bestAssetUrl)
        assertEquals("https://cdn.betterttv.net/emote/54fa8f1401e468494b85b537/2x.webp", result.previewUrl)
        assertNull(result.gifUrl)
        assertEquals("BttvUser", result.originalOwner)
    }

    @Test
    fun toDomain_mapsAnimatedGifEmoteCorrectly() {
        val dto = BttvEmoteDto(
            id = "5f1b0186cf6d2144653d2970",
            code = "catJAM",
            imageType = "gif",
            animated = true
        )

        val result = BttvMapper.toDomain(dto, ownerFallback = "illojuan")

        assertTrue(result.isAnimated)
        assertEquals(EmoteProvider.BETTERTTV, result.provider)
        assertEquals("https://cdn.betterttv.net/emote/5f1b0186cf6d2144653d2970/3x.webp", result.bestAssetUrl)
        assertEquals("https://cdn.betterttv.net/emote/5f1b0186cf6d2144653d2970/2x.webp", result.previewUrl)
        assertEquals("https://cdn.betterttv.net/emote/5f1b0186cf6d2144653d2970/3x.gif", result.gifUrl)
        assertEquals("illojuan", result.originalOwner)
    }

    @Test
    fun toDomain_detectsAnimatedWhenImageTypeIsGifEvenIfFlagIsFalse() {
        val dto = BttvEmoteDto(
            id = "abc123",
            code = "pepeD",
            imageType = "GIF",
            animated = false
        )

        val result = BttvMapper.toDomain(dto)
        assertTrue(result.isAnimated)
        assertEquals("https://cdn.betterttv.net/emote/abc123/3x.gif", result.gifUrl)
    }

    @Test
    fun toDomain_mapsTrendingWrapper() {
        val trending = BttvTrendingEmoteDto(
            emote = BttvEmoteDto(id = "t1", code = "KEKW", imageType = "png", animated = false),
            total = 900
        )

        val result = BttvMapper.toDomain(trending)
        assertEquals("t1", result.id)
        assertEquals("KEKW", result.name)
        assertEquals(EmoteProvider.BETTERTTV, result.provider)
    }

    @Test
    fun toChannelEmotes_combinesAndDeduplicatesChannelAndSharedEmotes() {
        val response = BttvUserResponseDto(
            id = "u1",
            channelEmotes = listOf(
                BttvEmoteDto(id = "e1", code = "ChannelOne", imageType = "png"),
                BttvEmoteDto(id = "shared1", code = "Overlap", imageType = "gif", animated = true)
            ),
            sharedEmotes = listOf(
                BttvEmoteDto(id = "shared1", code = "OverlapShared", imageType = "gif", animated = true),
                BttvEmoteDto(id = "e2", code = "SharedTwo", imageType = "png", user = BttvUserRefDto(name = "creator"))
            )
        )

        val emotes = BttvMapper.toChannelEmotes(response, channelOwner = "ibai")

        assertEquals(3, emotes.size)
        assertEquals(listOf("e1", "shared1", "e2"), emotes.map { it.id })
        assertEquals("ibai", emotes[0].originalOwner)
        assertEquals("creator", emotes[2].originalOwner)
    }
}

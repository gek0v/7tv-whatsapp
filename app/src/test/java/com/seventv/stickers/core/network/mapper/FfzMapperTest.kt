package com.seventv.stickers.core.network.mapper

import com.seventv.stickers.core.network.dto.FfzEmoteSetDto
import com.seventv.stickers.core.network.dto.FfzEmoticonDto
import com.seventv.stickers.core.network.dto.FfzGlobalResponseDto
import com.seventv.stickers.core.network.dto.FfzOwnerDto
import com.seventv.stickers.core.network.dto.FfzRoomDto
import com.seventv.stickers.core.network.dto.FfzRoomResponseDto
import com.seventv.stickers.core.network.model.EmoteProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfzMapperTest {

    @Test
    fun toDomain_mapsStaticEmoteAndNormalizesProtocolRelativeUrls() {
        val dto = FfzEmoticonDto(
            id = 254624L,
            name = "KEKW",
            urls = mapOf(
                "1" to "//cdn.frankerfacez.com/emoticon/254624/1",
                "2" to "//cdn.frankerfacez.com/emoticon/254624/2",
                "4" to "//cdn.frankerfacez.com/emoticon/254624/4"
            ),
            owner = FfzOwnerDto(name = "author", displayName = "Author")
        )

        val result = FfzMapper.toDomain(dto)

        assertEquals("254624", result.id)
        assertEquals("KEKW", result.name)
        assertEquals(EmoteProvider.FRANKERFACEZ, result.provider)
        assertFalse(result.isAnimated)
        assertFalse(result.isZeroWidth)
        assertEquals("https://cdn.frankerfacez.com/emoticon/254624/4", result.bestAssetUrl)
        assertEquals("https://cdn.frankerfacez.com/emoticon/254624/2", result.previewUrl)
        assertEquals("Author", result.originalOwner)
    }

    @Test
    fun toDomain_fallsBackToDefaultCdnUrlWhenUrlsMapEmpty() {
        val dto = FfzEmoticonDto(
            id = 999L,
            name = "LULW",
            urls = emptyMap()
        )

        val result = FfzMapper.toDomain(dto, ownerFallback = "forsen")

        assertEquals("https://cdn.frankerfacez.com/emoticon/999/4", result.bestAssetUrl)
        assertEquals("forsen", result.originalOwner)
    }

    @Test
    fun toDomain_mapsAnimatedAndModifierEmote() {
        val dto = FfzEmoticonDto(
            id = 412345L,
            name = "modCheck",
            modifier = true,
            animated = mapOf(
                "1" to "//cdn.frankerfacez.com/emoticon/412345/animated/1.webp",
                "4" to "//cdn.frankerfacez.com/emoticon/412345/animated/4.webp"
            ),
            urls = mapOf("4" to "//cdn.frankerfacez.com/emoticon/412345/4")
        )

        val result = FfzMapper.toDomain(dto)

        assertTrue(result.isAnimated)
        assertTrue(result.isZeroWidth)
        assertEquals("https://cdn.frankerfacez.com/emoticon/412345/animated/4.webp", result.bestAssetUrl)
        assertEquals("https://cdn.frankerfacez.com/emoticon/412345/animated/1.webp", result.previewUrl)
    }

    @Test
    fun toRoomEmotes_prioritizesPrimarySetFiltersHiddenAndDeduplicates() {
        val response = FfzRoomResponseDto(
            room = FfzRoomDto(id = "rubius", internalId = 1L, twitchId = 123L, displayName = "Rubius", set = 20L),
            sets = mapOf(
                "10" to FfzEmoteSetDto(
                    id = 10L,
                    emoticons = listOf(
                        FfzEmoticonDto(id = 1L, name = "Secondary", hidden = false),
                        FfzEmoticonDto(id = 2L, name = "Primary", hidden = false)
                    )
                ),
                "20" to FfzEmoteSetDto(
                    id = 20L,
                    emoticons = listOf(
                        FfzEmoticonDto(id = 2L, name = "Primary", hidden = false),
                        FfzEmoticonDto(id = 3L, name = "HiddenEmote", hidden = true)
                    )
                )
            )
        )

        val emotes = FfzMapper.toRoomEmotes(response)

        assertEquals(2, emotes.size)
        assertEquals(listOf("2", "1"), emotes.map { it.id })
        assertEquals("Rubius", emotes[0].originalOwner)
    }

    @Test
    fun toGlobalEmotes_usesDefaultSets() {
        val response = FfzGlobalResponseDto(
            defaultSets = listOf(3L),
            sets = mapOf(
                "3" to FfzEmoteSetDto(
                    id = 3L,
                    emoticons = listOf(FfzEmoticonDto(id = 100L, name = "YooHoo"))
                ),
                "99" to FfzEmoteSetDto(
                    id = 99L,
                    emoticons = listOf(FfzEmoticonDto(id = 200L, name = "Ignored"))
                )
            )
        )

        val emotes = FfzMapper.toGlobalEmotes(response)
        assertEquals(1, emotes.size)
        assertEquals("YooHoo", emotes[0].name)
    }
}

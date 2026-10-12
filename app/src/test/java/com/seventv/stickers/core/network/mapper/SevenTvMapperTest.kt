package com.seventv.stickers.core.network.mapper

import com.seventv.stickers.core.network.dto.SevenTvEmoteDataDto
import com.seventv.stickers.core.network.dto.SevenTvEmoteDto
import com.seventv.stickers.core.network.dto.SevenTvEmoteSetDto
import com.seventv.stickers.core.network.dto.SevenTvFileDto
import com.seventv.stickers.core.network.dto.SevenTvHostDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SevenTvMapperTest {

    @Test
    fun mapsDtoToDomainWithCdnUrlNormalization() {
        val dto = SevenTvEmoteDto(
            id = "60ccf4479f59298d1a7b4698",
            name = "catJAM",
            flags = 1,
            animated = true,
            host = SevenTvHostDto(
                url = "//cdn.7tv.app/emote/60ccf4479f59298d1a7b4698",
                files = listOf(
                    SevenTvFileDto("1x.webp", "WEBP", 32, 32, 100),
                    SevenTvFileDto("4x.webp", "WEBP", 128, 128, 400)
                )
            )
        )
        val domain = SevenTvMapper.toDomain(dto)
        assertEquals("60ccf4479f59298d1a7b4698", domain.id)
        assertEquals("catJAM", domain.name)
        assertTrue(domain.isAnimated)
        assertTrue(domain.isZeroWidth)
        assertFalse(domain.isNsfw)
        assertEquals("https://cdn.7tv.app/emote/60ccf4479f59298d1a7b4698/4x.webp", domain.bestAssetUrl)
        assertEquals("https://cdn.7tv.app/emote/60ccf4479f59298d1a7b4698/1x.webp", domain.previewUrl)
        assertEquals(listOf("1x.webp", "4x.webp"), domain.availableScales)
    }

    @Test
    fun selects2xPreviewScaleWhenAvailableOver4x() {
        val dto = SevenTvEmoteDto(
            id = "e_scale",
            name = "ScaleEmote",
            host = SevenTvHostDto(
                url = "https://cdn.7tv.app/emote/e_scale",
                files = listOf(
                    SevenTvFileDto("1x.webp", "WEBP", 32, 32, 100),
                    SevenTvFileDto("2x.webp", "WEBP", 64, 64, 200),
                    SevenTvFileDto("3x.webp", "WEBP", 96, 96, 300),
                    SevenTvFileDto("4x.webp", "WEBP", 128, 128, 400)
                )
            )
        )
        val domain = SevenTvMapper.toDomain(dto)
        assertEquals("https://cdn.7tv.app/emote/e_scale/4x.webp", domain.bestAssetUrl)
        assertEquals("https://cdn.7tv.app/emote/e_scale/2x.webp", domain.previewUrl)
    }

    @Test
    fun handlesProtocolRelativeUrlWithoutCrashing() {
        val url = SevenTvMapper.normalizeCdnUrl("//cdn.7tv.app/emote/test", "4x.webp")
        assertEquals("https://cdn.7tv.app/emote/test/4x.webp", url)
    }

    @Test
    fun mapsEmoteSetDtoToDomain() {
        val dto = SevenTvEmoteSetDto(
            id = "set1",
            name = "Global Set",
            ownerName = "7tv",
            emotes = listOf(SevenTvEmoteDto(id = "e1", name = "TestEmote", host = SevenTvHostDto("//cdn.7tv.app/e1")))
        )
        val domain = SevenTvMapper.toDomain(dto)
        assertEquals("set1", domain.id)
        assertEquals("Global Set", domain.name)
        assertEquals("7tv", domain.ownerName)
        assertEquals(1, domain.emotes.size)
    }

    @Test
    fun selectsHighestAvailableScaleInPriorityOrder() {
        val dto3x = SevenTvEmoteDto(
            id = "e3",
            name = "Emote3x",
            host = SevenTvHostDto(
                url = "//cdn.7tv.app/emote/e3",
                files = listOf(
                    SevenTvFileDto("1x.webp", "WEBP", 32, 32, 100),
                    SevenTvFileDto("3x.webp", "WEBP", 96, 96, 300)
                )
            )
        )
        val domain3x = SevenTvMapper.toDomain(dto3x)
        assertEquals("https://cdn.7tv.app/emote/e3/3x.webp", domain3x.bestAssetUrl)

        val dto2x = SevenTvEmoteDto(
            id = "e2",
            name = "Emote2x",
            host = SevenTvHostDto(
                url = "//cdn.7tv.app/emote/e2",
                files = listOf(
                    SevenTvFileDto("1x.webp", "WEBP", 32, 32, 100),
                    SevenTvFileDto("2x.webp", "WEBP", 64, 64, 200)
                )
            )
        )
        val domain2x = SevenTvMapper.toDomain(dto2x)
        assertEquals("https://cdn.7tv.app/emote/e2/2x.webp", domain2x.bestAssetUrl)

        val dto1x = SevenTvEmoteDto(
            id = "e1",
            name = "Emote1x",
            host = SevenTvHostDto(
                url = "//cdn.7tv.app/emote/e1",
                files = listOf(
                    SevenTvFileDto("1x.webp", "WEBP", 32, 32, 100)
                )
            )
        )
        val domain1x = SevenTvMapper.toDomain(dto1x)
        assertEquals("https://cdn.7tv.app/emote/e1/1x.webp", domain1x.bestAssetUrl)
    }

    @Test
    fun extractsFlagsAndHostFromNestedDataDto() {
        val nsfwFlag = 1 shl 16
        val dto = SevenTvEmoteDto(
            id = "nested1",
            name = "NestedEmote",
            flags = 0,
            animated = false,
            data = SevenTvEmoteDataDto(
                flags = nsfwFlag or 1,
                animated = true,
                host = SevenTvHostDto(
                    url = "https://cdn.7tv.app/emote/nested1",
                    files = listOf(SevenTvFileDto("4x.webp", "WEBP", 128, 128, 400))
                )
            )
        )
        val domain = SevenTvMapper.toDomain(dto)
        assertTrue(domain.isAnimated)
        assertTrue(domain.isZeroWidth)
        assertTrue(domain.isNsfw)
        assertEquals("https://cdn.7tv.app/emote/nested1/4x.webp", domain.bestAssetUrl)
    }

    @Test
    fun handlesNullHostGracefully() {
        val dto = SevenTvEmoteDto(
            id = "no_host",
            name = "NoHost",
            host = null,
            data = null
        )
        val domain = SevenTvMapper.toDomain(dto)
        assertEquals("", domain.bestAssetUrl)
        assertTrue(domain.availableScales.isEmpty())
    }

    @Test
    fun normalizesTrailingSlashesInBaseUrl() {
        val url = SevenTvMapper.normalizeCdnUrl("//cdn.7tv.app/emote/test/", "/4x.webp")
        assertEquals("https://cdn.7tv.app/emote/test/4x.webp", url)
    }
}

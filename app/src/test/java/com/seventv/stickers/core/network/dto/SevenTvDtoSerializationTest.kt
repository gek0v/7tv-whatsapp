package com.seventv.stickers.core.network.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SevenTvDtoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parseEmoteDtoWithNestedHostAndFiles() {
        val payload = """
        {
            "id": "60ccf4479f59298d1a7b4698",
            "name": "catJAM",
            "flags": 1,
            "animated": true,
            "host": {
                "url": "//cdn.7tv.app/emote/60ccf4479f59298d1a7b4698",
                "files": [
                    {"name": "1x.webp", "format": "WEBP", "width": 32, "height": 32, "size": 1200},
                    {"name": "4x.webp", "format": "WEBP", "width": 128, "height": 128, "size": 8400}
                ]
            }
        }
        """.trimIndent()
        val dto = json.decodeFromString<SevenTvEmoteDto>(payload)
        assertEquals("60ccf4479f59298d1a7b4698", dto.id)
        assertEquals("catJAM", dto.name)
        assertEquals(1, dto.flags)
        assertTrue(dto.animated)
        assertEquals("//cdn.7tv.app/emote/60ccf4479f59298d1a7b4698", dto.host?.url)
        assertEquals(2, dto.host?.files?.size)
        assertEquals("1x.webp", dto.host?.files?.get(0)?.name)
        assertEquals("WEBP", dto.host?.files?.get(0)?.format)
        assertEquals(32, dto.host?.files?.get(0)?.width)
        assertEquals(32, dto.host?.files?.get(0)?.height)
        assertEquals(1200L, dto.host?.files?.get(0)?.size)
    }

    @Test
    fun parseEmoteSetDtoWithEmoteList() {
        val payload = """
        {
            "id": "60b0e3be5b1f9f2571fa088b",
            "name": "Global Emotes",
            "flags": 0,
            "emotes": [
                {"id": "60ccf4479f59298d1a7b4698", "name": "catJAM", "animated": true}
            ]
        }
        """.trimIndent()
        val dto = json.decodeFromString<SevenTvEmoteSetDto>(payload)
        assertEquals("Global Emotes", dto.name)
        assertEquals(1, dto.emotes.size)
        assertEquals("60ccf4479f59298d1a7b4698", dto.emotes[0].id)
        assertEquals("catJAM", dto.emotes[0].name)
        assertTrue(dto.emotes[0].animated)
    }

    @Test
    fun parseEmoteDataDtoWithTagsAndHost() {
        val payload = """
        {
            "id": "data123",
            "name": "PepeHands",
            "flags": 0,
            "animated": false,
            "tags": ["frog", "sad"],
            "host": {
                "url": "//cdn.7tv.app/emote/data123",
                "files": []
            }
        }
        """.trimIndent()
        val dto = json.decodeFromString<SevenTvEmoteDataDto>(payload)
        assertEquals("data123", dto.id)
        assertEquals("PepeHands", dto.name)
        assertFalse(dto.animated)
        assertEquals(listOf("frog", "sad"), dto.tags)
        assertNotNull(dto.host)
        assertEquals("//cdn.7tv.app/emote/data123", dto.host?.url)
        assertTrue(dto.host?.files?.isEmpty() == true)
    }

    @Test
    fun parseEmoteSetDtoWithOwnerNameAndDefaults() {
        val payload = """
        {
            "id": "set123",
            "name": "Streamer Set",
            "capacity": 500,
            "owner_name": "forsen"
        }
        """.trimIndent()
        val dto = json.decodeFromString<SevenTvEmoteSetDto>(payload)
        assertEquals("set123", dto.id)
        assertEquals("Streamer Set", dto.name)
        assertEquals(500, dto.capacity)
        assertEquals("forsen", dto.ownerName)
        assertEquals(0, dto.flags)
        assertTrue(dto.emotes.isEmpty())
    }

    @Test
    fun parseWithUnknownKeysSucceeds() {
        val payload = """
        {
            "id": "extra123",
            "name": "ExtraKeys",
            "unexpected_field": "should be ignored",
            "nested_extra": {"foo": "bar"}
        }
        """.trimIndent()
        val dto = json.decodeFromString<SevenTvEmoteDto>(payload)
        assertEquals("extra123", dto.id)
        assertEquals("ExtraKeys", dto.name)
        assertNull(dto.data)
        assertNull(dto.host)
    }
}

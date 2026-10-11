package com.seventv.stickers.core.domain.model

import com.seventv.stickers.core.common.result.StickerError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelsTest {

    @Test
    fun stickerPackDefaultValues() {
        val before = System.currentTimeMillis()
        val pack = StickerPack(
            identifier = "pack-1",
            name = "Test Pack",
            publisher = "Test Publisher",
            trayImagePath = "/path/to/tray.png",
            isAnimated = true
        )
        val after = System.currentTimeMillis()

        assertEquals("pack-1", pack.identifier)
        assertEquals("Test Pack", pack.name)
        assertEquals("Test Publisher", pack.publisher)
        assertEquals("/path/to/tray.png", pack.trayImagePath)
        assertTrue(pack.isAnimated)
        assertFalse(pack.isWhitelisted)
        assertTrue(pack.stickers.isEmpty())
        assertTrue(pack.createdAt in before..after)
    }

    @Test
    fun stickerPackCustomValuesAndCopy() {
        val sticker = PackSticker(
            id = "st-1",
            packIdentifier = "pack-1",
            emoteId = "emote-1",
            emoteName = "catJam",
            filePath = "/path/to/catjam.webp"
        )
        val pack = StickerPack(
            identifier = "pack-1",
            name = "Test Pack",
            publisher = "Test Publisher",
            trayImagePath = "/path/to/tray.png",
            isAnimated = false,
            isWhitelisted = true,
            stickers = listOf(sticker),
            createdAt = 123456789L
        )

        assertEquals("pack-1", pack.identifier)
        assertEquals("Test Pack", pack.name)
        assertEquals("Test Publisher", pack.publisher)
        assertEquals("/path/to/tray.png", pack.trayImagePath)
        assertFalse(pack.isAnimated)
        assertTrue(pack.isWhitelisted)
        assertEquals(1, pack.stickers.size)
        assertEquals(sticker, pack.stickers.first())
        assertEquals(123456789L, pack.createdAt)

        val updatedPack = pack.copy(
            name = "Updated Pack",
            isWhitelisted = false
        )
        assertEquals("Updated Pack", updatedPack.name)
        assertFalse(updatedPack.isWhitelisted)
        assertEquals(pack.identifier, updatedPack.identifier)
        assertEquals(pack.stickers, updatedPack.stickers)
        assertNotEquals(pack, updatedPack)
    }

    @Test
    fun packStickerDefaultValues() {
        val sticker = PackSticker(
            id = "st-1",
            packIdentifier = "pack-1",
            emoteId = "emote-1",
            emoteName = "catJam",
            filePath = "/path/to/catjam.webp"
        )

        assertEquals("st-1", sticker.id)
        assertEquals("pack-1", sticker.packIdentifier)
        assertEquals("emote-1", sticker.emoteId)
        assertEquals("catJam", sticker.emoteName)
        assertEquals("/path/to/catjam.webp", sticker.filePath)
        assertEquals(listOf("💬"), sticker.emojis)
        assertEquals(0L, sticker.fileLength)
        assertEquals(0, sticker.sortOrder)
    }

    @Test
    fun packStickerCustomValuesAndCopy() {
        val sticker = PackSticker(
            id = "st-1",
            packIdentifier = "pack-1",
            emoteId = "emote-1",
            emoteName = "catJam",
            filePath = "/path/to/catjam.webp",
            emojis = listOf("🐱", "🎶"),
            fileLength = 1024L,
            sortOrder = 5
        )

        assertEquals(listOf("🐱", "🎶"), sticker.emojis)
        assertEquals(1024L, sticker.fileLength)
        assertEquals(5, sticker.sortOrder)

        val updated = sticker.copy(sortOrder = 10, emojis = listOf("🔥"))
        assertEquals(10, updated.sortOrder)
        assertEquals(listOf("🔥"), updated.emojis)
        assertEquals(sticker.id, updated.id)
        assertNotEquals(sticker, updated)
    }

    @Test
    fun stickerErrorInsufficientStickers() {
        val errorDefaultMin: StickerError = StickerError.InsufficientStickers(actual = 2)
        assertTrue(errorDefaultMin is StickerError.InsufficientStickers)
        val typed = errorDefaultMin as StickerError.InsufficientStickers
        assertEquals(2, typed.actual)
        assertEquals(3, typed.min)

        val errorCustomMin = StickerError.InsufficientStickers(actual = 1, min = 5)
        assertEquals(1, errorCustomMin.actual)
        assertEquals(5, errorCustomMin.min)
    }

    @Test
    fun stickerErrorPackNotFound() {
        val error: StickerError = StickerError.PackNotFound(identifier = "pack-999")
        assertTrue(error is StickerError.PackNotFound)
        assertEquals("pack-999", (error as StickerError.PackNotFound).identifier)
    }
}

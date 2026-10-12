package com.seventv.stickers.core.domain.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmoteEmojiMapperTest {

    @Test
    fun mapEmoteNameToEmojis_identifiesLaughEmotes() {
        val xdx = EmoteEmojiMapper.mapEmoteNameToEmojis("xdx")
        assertTrue(xdx.contains("😂") || xdx.contains("🤣"))

        val kekw = EmoteEmojiMapper.mapEmoteNameToEmojis("KEKW")
        assertTrue(kekw.contains("😂") || kekw.contains("🤣"))

        val lul = EmoteEmojiMapper.mapEmoteNameToEmojis("OMEGALUL")
        assertTrue(lul.contains("😂") || lul.contains("🤣"))
    }

    @Test
    fun mapEmoteNameToEmojis_identifiesSadEmotes() {
        val sadge = EmoteEmojiMapper.mapEmoteNameToEmojis("Sadge")
        assertTrue(sadge.contains("😭") || sadge.contains("😢"))

        val bibleThump = EmoteEmojiMapper.mapEmoteNameToEmojis("BibleThump")
        assertTrue(bibleThump.contains("😭") || bibleThump.contains("😢"))
    }

    @Test
    fun mapEmoteNameToEmojis_identifiesCatEmotes() {
        val catJam = EmoteEmojiMapper.mapEmoteNameToEmojis("catJam")
        assertTrue(catJam.contains("🐱") || catJam.contains("😺") || catJam.contains("🎉"))
    }

    @Test
    fun mapEmoteNameToEmojis_identifiesPogOrShockEmotes() {
        val pog = EmoteEmojiMapper.mapEmoteNameToEmojis("PogChamp")
        assertTrue(pog.contains("😮") || pog.contains("😲") || pog.contains("😱"))
    }

    @Test
    fun mapEmoteNameToEmojis_identifiesFireOrChad() {
        val gigaChad = EmoteEmojiMapper.mapEmoteNameToEmojis("GIGACHAD")
        assertTrue(gigaChad.contains("😎") || gigaChad.contains("🔥"))
    }

    @Test
    fun mapEmoteNameToEmojis_defaultsToChatBalloonWhenNoMatch() {
        val random = EmoteEmojiMapper.mapEmoteNameToEmojis("qwertyuiop123")
        assertEquals(listOf("💬"), random)
    }

    @Test
    fun mapEmoteNameToEmojis_neverExceedsThreeEmojis() {
        val emojis = EmoteEmojiMapper.mapEmoteNameToEmojis("super_laugh_cat_party_xd")
        assertTrue(emojis.size in 1..3)
    }
}

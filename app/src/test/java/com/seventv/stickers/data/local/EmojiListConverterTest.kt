package com.seventv.stickers.data.local

import com.seventv.stickers.data.local.converter.EmojiListConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmojiListConverterTest {

    private lateinit var converter: EmojiListConverter

    @Before
    fun setUp() {
        converter = EmojiListConverter()
    }

    @Test
    fun fromString_withCommaSeparatedString_returnsList() {
        val result = converter.fromString("😀,😃,😄")
        assertEquals(listOf("😀", "😃", "😄"), result)
    }

    @Test
    fun fromString_withWhitespaceAndEmptyTokens_trimsAndFilters() {
        val result = converter.fromString(" 😀 , , 😃 ,  ")
        assertEquals(listOf("😀", "😃"), result)
    }

    @Test
    fun fromString_withBlankString_returnsEmptyList() {
        assertTrue(converter.fromString("").isEmpty())
        assertTrue(converter.fromString("   ").isEmpty())
    }

    @Test
    fun toString_withList_returnsCommaSeparatedString() {
        val result = converter.toString(listOf("😀", "😃"))
        assertEquals("😀,😃", result)
    }

    @Test
    fun toString_withMoreThanThreeEmojis_capsAtThree() {
        val result = converter.toString(listOf("1", "2", "3", "4", "5"))
        assertEquals("1,2,3", result)
    }

    @Test
    fun toString_withEmptyList_returnsEmptyString() {
        val result = converter.toString(emptyList())
        assertEquals("", result)
    }
}

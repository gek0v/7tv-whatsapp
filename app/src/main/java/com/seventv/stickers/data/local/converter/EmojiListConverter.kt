package com.seventv.stickers.data.local.converter

import androidx.room.TypeConverter

class EmojiListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun toString(list: List<String>): String {
        return list.take(3).joinToString(",")
    }
}

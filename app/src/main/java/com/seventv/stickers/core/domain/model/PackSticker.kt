package com.seventv.stickers.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class PackSticker(
    val id: String,
    val packIdentifier: String,
    val emoteId: String,
    val emoteName: String,
    val filePath: String,
    val emojis: List<String> = listOf("💬"),
    val fileLength: Long = 0L,
    val sortOrder: Int = 0
)

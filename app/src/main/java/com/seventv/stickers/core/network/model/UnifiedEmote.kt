package com.seventv.stickers.core.network.model

import androidx.compose.runtime.Immutable

@Immutable
data class UnifiedEmote(
    val id: String,
    val name: String,
    val provider: EmoteProvider,
    val isAnimated: Boolean,
    val isZeroWidth: Boolean = false,
    val isNsfw: Boolean = false,
    val bestAssetUrl: String,
    val previewUrl: String = bestAssetUrl,
    val gifUrl: String? = null,
    val originalOwner: String? = null
)

fun UnifiedEmote.toSevenTvEmote(): SevenTvEmote = SevenTvEmote(
    id = id,
    name = name,
    isAnimated = isAnimated,
    isZeroWidth = isZeroWidth,
    isNsfw = isNsfw,
    bestAssetUrl = bestAssetUrl,
    availableScales = emptyList(),
    previewUrl = previewUrl,
    gifUrl = gifUrl,
    provider = provider,
    originalOwner = originalOwner
)

fun SevenTvEmote.toUnifiedEmote(): UnifiedEmote = UnifiedEmote(
    id = id,
    name = name,
    provider = provider,
    isAnimated = isAnimated,
    isZeroWidth = isZeroWidth,
    isNsfw = isNsfw,
    bestAssetUrl = bestAssetUrl,
    previewUrl = previewUrl,
    gifUrl = gifUrl,
    originalOwner = originalOwner
)

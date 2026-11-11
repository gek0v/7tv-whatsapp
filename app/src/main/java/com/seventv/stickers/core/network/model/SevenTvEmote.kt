package com.seventv.stickers.core.network.model

import androidx.compose.runtime.Immutable

/**
 * Domain model representing a 7TV (or bridged provider) emote with resolved flags and asset URLs.
 */
@Immutable
data class SevenTvEmote(
    val id: String,
    val name: String,
    val isAnimated: Boolean,
    val isZeroWidth: Boolean,
    val isNsfw: Boolean,
    val bestAssetUrl: String,
    val availableScales: List<String>,
    val previewUrl: String = bestAssetUrl,
    val gifUrl: String? = null,
    val provider: EmoteProvider = EmoteProvider.SEVENTV,
    val originalOwner: String? = null
)

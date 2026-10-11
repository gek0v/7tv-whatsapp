package com.seventv.stickers.core.network.model

/**
 * Domain model representing a 7TV emote with resolved flags and asset URLs.
 */
data class SevenTvEmote(
    val id: String,
    val name: String,
    val isAnimated: Boolean,
    val isZeroWidth: Boolean,
    val isNsfw: Boolean,
    val bestAssetUrl: String,
    val availableScales: List<String>
)

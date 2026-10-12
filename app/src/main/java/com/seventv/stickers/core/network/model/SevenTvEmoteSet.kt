package com.seventv.stickers.core.network.model

/**
 * Domain model representing a 7TV emote set with channel/owner info and contained emotes.
 */
data class SevenTvEmoteSet(
    val id: String,
    val name: String,
    val ownerName: String?,
    val emotes: List<SevenTvEmote>
)

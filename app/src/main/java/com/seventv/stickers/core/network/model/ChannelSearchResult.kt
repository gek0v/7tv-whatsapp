package com.seventv.stickers.core.network.model

/**
 * Summary metadata for an individual emote set belonging to a channel or streamer.
 */
data class ChannelEmoteSetSummary(
    val id: String,
    val name: String,
    val emoteCount: Int,
    val isActive: Boolean = false
)

/**
 * Domain model representing a channel/streamer search result containing all available
 * emote sets and the default active/primary emote set.
 */
data class ChannelSearchResult(
    val streamerName: String,
    val username: String,
    val avatarUrl: String? = null,
    val availableSets: List<ChannelEmoteSetSummary>,
    val activeSet: SevenTvEmoteSet,
    val twitchId: String? = null
)

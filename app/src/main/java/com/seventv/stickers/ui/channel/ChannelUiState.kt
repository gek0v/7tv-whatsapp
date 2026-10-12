package com.seventv.stickers.ui.channel

import com.seventv.stickers.core.domain.engine.SplitPlan
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet

enum class ChannelFilter {
    ALL,
    ANIMATED,
    STATIC
}

sealed interface ChannelLoadState {
    data object Idle : ChannelLoadState
    data class Loading(val query: String) : ChannelLoadState
    data class Success(
        val streamerName: String,
        val emoteSet: SevenTvEmoteSet,
        val allEmotes: List<SevenTvEmote>
    ) : ChannelLoadState
    data class Error(val message: String) : ChannelLoadState
}

sealed interface ChannelImportState {
    data object Idle : ChannelImportState
    data class Confirming(
        val streamerName: String,
        val splitPlans: List<SplitPlan>
    ) : ChannelImportState
    data class Importing(
        val currentPack: Int,
        val totalPacks: Int,
        val currentSticker: Int = 0,
        val totalStickers: Int = 0
    ) : ChannelImportState
    data class Success(
        val createdPacks: List<StickerPack>
    ) : ChannelImportState
    data class Error(val message: String) : ChannelImportState
}

data class ChannelUiState(
    val query: String = "",
    val activeFilter: ChannelFilter = ChannelFilter.ALL,
    val loadState: ChannelLoadState = ChannelLoadState.Idle,
    val importState: ChannelImportState = ChannelImportState.Idle,
    val selectedEmoteIds: Set<String> = emptySet(),
    val draftEmotes: List<SevenTvEmote> = emptyList()
)

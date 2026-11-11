package com.seventv.stickers.ui.channel

import com.seventv.stickers.core.domain.engine.SplitPlan
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.ChannelEmoteSetSummary
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet

enum class ChannelFilter {
    ALL,
    ANIMATED,
    STATIC
}

enum class ChannelProviderFilter(val displayName: String, val provider: EmoteProvider?) {
    ALL("Todos", null),
    SEVENTV("7TV", EmoteProvider.SEVENTV),
    BETTERTTV("BetterTTV", EmoteProvider.BETTERTTV),
    FRANKERFACEZ("FrankerFaceZ", EmoteProvider.FRANKERFACEZ),
    TWITCH("Twitch", EmoteProvider.TWITCH)
}

sealed interface ChannelLoadState {
    data object Idle : ChannelLoadState
    data class Loading(val query: String) : ChannelLoadState
    data class Success(
        val streamerName: String,
        val username: String = "",
        val avatarUrl: String? = null,
        val availableSets: List<ChannelEmoteSetSummary> = emptyList(),
        val currentSetId: String = emoteSet.id,
        val emoteSet: SevenTvEmoteSet,
        val allEmotes: List<SevenTvEmote>,
        val emotesByProvider: Map<EmoteProvider, List<SevenTvEmote>> = emptyMap(),
        val isLoadingSet: Boolean = false
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
    val activeProviderFilter: ChannelProviderFilter = ChannelProviderFilter.ALL,
    val loadState: ChannelLoadState = ChannelLoadState.Idle,
    val importState: ChannelImportState = ChannelImportState.Idle,
    val selectedEmoteIds: Set<String> = emptySet(),
    val draftEmotes: List<SevenTvEmote> = emptyList(),
    val existingPacks: List<StickerPack> = emptyList(),
    val isAddToExistingPackDialogOpen: Boolean = false,
    val emotesToAddToPack: List<SevenTvEmote> = emptyList(),
    val isAddingToPack: Boolean = false,
    val addToPackSuccessMessage: String? = null,
    val addToPackErrorMessage: String? = null
)

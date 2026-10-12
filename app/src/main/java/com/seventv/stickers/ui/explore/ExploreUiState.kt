package com.seventv.stickers.ui.explore

import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote

enum class EmoteFilter {
    ALL,
    ANIMATED,
    STATIC
}

sealed interface SearchResultsState {
    data object Idle : SearchResultsState
    data object Loading : SearchResultsState
    data class Success(val emotes: List<SevenTvEmote>) : SearchResultsState
    data class Empty(val query: String) : SearchResultsState
    data class Error(val message: String) : SearchResultsState
}

sealed interface PackCreationState {
    data object Idle : PackCreationState
    data object Creating : PackCreationState
    data class Success(val createdPacks: List<StickerPack>) : PackCreationState
    data class Error(val message: String) : PackCreationState
}

data class ExploreUiState(
    val query: String = "",
    val activeFilter: EmoteFilter = EmoteFilter.ALL,
    val searchResults: SearchResultsState = SearchResultsState.Idle,
    val draftEmotes: List<SevenTvEmote> = emptyList(),
    val packCreationState: PackCreationState = PackCreationState.Idle,
    val isSavePackDialogOpen: Boolean = false,
    val draftPackName: String = ""
) {
    val filteredEmotes: List<SevenTvEmote>
        get() {
            val all = when (val res = searchResults) {
                is SearchResultsState.Success -> res.emotes.distinctBy { it.id }
                else -> emptyList()
            }
            return when (activeFilter) {
                EmoteFilter.ALL -> all
                EmoteFilter.ANIMATED -> all.filter { it.isAnimated }
                EmoteFilter.STATIC -> all.filter { !it.isAnimated }
            }
        }

    val emotes: List<SevenTvEmote>
        get() = filteredEmotes
}

package com.seventv.stickers.ui.explore

import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.channel.ChannelProviderFilter

enum class EmoteFilter {
    ALL,
    ANIMATED,
    STATIC
}

sealed interface SearchResultsState {
    data object Idle : SearchResultsState
    data object Loading : SearchResultsState
    data class Success(
        val emotes: List<SevenTvEmote>,
        val emotesByProvider: Map<EmoteProvider, List<SevenTvEmote>> = emptyMap()
    ) : SearchResultsState
    data class Empty(val query: String) : SearchResultsState
    data class Error(val message: String) : SearchResultsState
}

sealed interface PackCreationState {
    data object Idle : PackCreationState
    data object Creating : PackCreationState
    data class Success(val createdPacks: List<StickerPack>) : PackCreationState
    data class Error(val message: String) : PackCreationState
}

enum class ExploreCategory(val apiValue: String, val label: String) {
    TRENDING("TRENDING_DAY", "Tendencias"),
    TOP("TOP", "Populares")
}

data class ExploreUiState(
    val query: String = "",
    val activeFilter: EmoteFilter = EmoteFilter.ALL,
    val activeProviderFilter: ChannelProviderFilter = ChannelProviderFilter.ALL,
    val selectedCategory: ExploreCategory = ExploreCategory.TRENDING,
    val searchResults: SearchResultsState = SearchResultsState.Idle,
    val draftEmotes: List<SevenTvEmote> = emptyList(),
    val packCreationState: PackCreationState = PackCreationState.Idle,
    val isSavePackDialogOpen: Boolean = false,
    val draftPackName: String = "",
    val existingPacks: List<StickerPack> = emptyList(),
    val isAddToExistingPackDialogOpen: Boolean = false,
    val emotesToAddToPack: List<SevenTvEmote> = emptyList(),
    val isAddingToPack: Boolean = false,
    val addToPackSuccessMessage: String? = null,
    val addToPackErrorMessage: String? = null,
    val currentPage: Int = 1,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true
) {
    val filteredEmotes: List<SevenTvEmote>
        get() {
            val all = when (val res = searchResults) {
                is SearchResultsState.Success -> res.emotes.distinctBy { "${it.provider.name}:${it.id}" }
                else -> emptyList()
            }
            val providerFiltered = when (val pFilter = activeProviderFilter) {
                ChannelProviderFilter.ALL -> all
                else -> {
                    val target = pFilter.provider
                    if (target != null) all.filter { it.provider == target } else all
                }
            }
            return when (activeFilter) {
                EmoteFilter.ALL -> providerFiltered
                EmoteFilter.ANIMATED -> providerFiltered.filter { it.isAnimated }
                EmoteFilter.STATIC -> providerFiltered.filter { !it.isAnimated }
            }
        }

    val emotes: List<SevenTvEmote>
        get() = filteredEmotes
}

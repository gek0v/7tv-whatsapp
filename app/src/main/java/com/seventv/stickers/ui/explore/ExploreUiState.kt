package com.seventv.stickers.ui.explore

import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.channel.ChannelProviderFilter
import java.util.concurrent.ConcurrentHashMap

enum class EmoteFilter {
    ALL,
    ANIMATED,
    STATIC
}

private data class EmoteDedupKey(val provider: EmoteProvider, val id: String)

sealed interface SearchResultsState {
    data object Idle : SearchResultsState
    data object Loading : SearchResultsState
    data class Success(
        val emotes: List<SevenTvEmote>,
        val emotesByProvider: Map<EmoteProvider, List<SevenTvEmote>> = emptyMap(),
        val isDeduplicated: Boolean = false
    ) : SearchResultsState {
        @Transient
        private val filterCache = ConcurrentHashMap<Int, List<SevenTvEmote>>()

        fun getFiltered(providerFilter: ChannelProviderFilter, emoteFilter: EmoteFilter): List<SevenTvEmote> {
            val key = (providerFilter.ordinal shl 4) or emoteFilter.ordinal
            return filterCache.computeIfAbsent(key) {
                val base = if (providerFilter == ChannelProviderFilter.ALL) {
                    if (isDeduplicated) emotes else deduplicateEmotes(emotes)
                } else {
                    val target = providerFilter.provider
                    if (target != null) {
                        val byProvider = emotesByProvider[target]
                        if (byProvider != null) {
                            if (isDeduplicated) byProvider else deduplicateEmotes(byProvider)
                        } else {
                            val filtered = emotes.filter { it.provider == target }
                            if (isDeduplicated) filtered else deduplicateEmotes(filtered)
                        }
                    } else {
                        if (isDeduplicated) emotes else deduplicateEmotes(emotes)
                    }
                }
                when (emoteFilter) {
                    EmoteFilter.ALL -> base
                    EmoteFilter.ANIMATED -> base.filter { it.isAnimated }
                    EmoteFilter.STATIC -> base.filter { !it.isAnimated }
                }
            }
        }

        private fun deduplicateEmotes(list: List<SevenTvEmote>): List<SevenTvEmote> {
            if (list.size <= 1) return list
            val seen = HashSet<EmoteDedupKey>(list.size * 2)
            val result = ArrayList<SevenTvEmote>(list.size)
            for (i in list.indices) {
                val e = list[i]
                if (seen.add(EmoteDedupKey(e.provider, e.id))) {
                    result.add(e)
                }
            }
            return result
        }
    }
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
    val draftEmoteIds: Set<String> by lazy(LazyThreadSafetyMode.NONE) {
        if (draftEmotes.isEmpty()) emptySet()
        else {
            val set = HashSet<String>(draftEmotes.size * 2)
            for (i in draftEmotes.indices) {
                set.add(draftEmotes[i].id)
            }
            set
        }
    }

    val filteredEmotes: List<SevenTvEmote>
        get() = when (val res = searchResults) {
            is SearchResultsState.Success -> res.getFiltered(activeProviderFilter, activeFilter)
            else -> emptyList()
        }

    val emotes: List<SevenTvEmote>
        get() = filteredEmotes
}

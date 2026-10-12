package com.seventv.stickers.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.engine.PackSplittingEngine
import com.seventv.stickers.core.domain.engine.SplitPlan
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import com.seventv.stickers.data.repository.SevenTvRepository
import com.seventv.stickers.data.repository.StickerPackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChannelViewModel(
    private val sevenTvRepository: SevenTvRepository,
    private val stickerPackRepository: StickerPackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    fun onSearchStreamer(query: String? = null) {
        val targetQuery = (query ?: _uiState.value.query).trim()
        if (targetQuery.isBlank()) return

        _uiState.update {
            it.copy(
                query = targetQuery,
                loadState = ChannelLoadState.Loading(targetQuery),
                selectedEmoteIds = emptySet(),
                importState = ChannelImportState.Idle
            )
        }

        viewModelScope.launch {
            // First attempt to fetch emote set directly
            val emoteSetResult = sevenTvRepository.getEmoteSet(targetQuery)
            if (emoteSetResult is StickerResult.Success) {
                val set = emoteSetResult.data
                _uiState.update {
                    it.copy(
                        loadState = ChannelLoadState.Success(
                            streamerName = set.name.ifBlank { targetQuery },
                            emoteSet = set,
                            allEmotes = set.emotes
                        )
                    )
                }
                return@launch
            }

            // Fallback: search emotes matching the channel name
            val searchResult = sevenTvRepository.searchEmotes(targetQuery, limit = 100)
            when (searchResult) {
                is StickerResult.Success -> {
                    val emotes = searchResult.data
                    if (emotes.isEmpty()) {
                        _uiState.update {
                            it.copy(loadState = ChannelLoadState.Error("No se encontraron emotes para '$targetQuery'"))
                        }
                    } else {
                        val syntheticSet = SevenTvEmoteSet(
                            id = targetQuery,
                            name = targetQuery,
                            ownerName = targetQuery,
                            emotes = emotes
                        )
                        _uiState.update {
                            it.copy(
                                loadState = ChannelLoadState.Success(
                                    streamerName = targetQuery,
                                    emoteSet = syntheticSet,
                                    allEmotes = emotes
                                )
                            )
                        }
                    }
                }
                is StickerResult.Failure -> {
                    _uiState.update {
                        it.copy(loadState = ChannelLoadState.Error("Error al consultar canal: ${searchResult.error}"))
                    }
                }
            }
        }
    }

    fun onFilterSelected(filter: ChannelFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun onToggleSelectEmote(emoteId: String) {
        _uiState.update { state ->
            val updated = state.selectedEmoteIds.toMutableSet()
            if (updated.contains(emoteId)) {
                updated.remove(emoteId)
            } else {
                updated.add(emoteId)
            }
            state.copy(selectedEmoteIds = updated)
        }
    }

    fun onSelectAllEmotes() {
        val currentEmotes = getFilteredEmotes()
        _uiState.update { it.copy(selectedEmoteIds = currentEmotes.map { e -> e.id }.toSet()) }
    }

    fun onDeselectAllEmotes() {
        _uiState.update { it.copy(selectedEmoteIds = emptySet()) }
    }

    fun getFilteredEmotes(): List<SevenTvEmote> {
        val state = _uiState.value
        val successState = state.loadState as? ChannelLoadState.Success ?: return emptyList()
        return when (state.activeFilter) {
            ChannelFilter.ALL -> successState.allEmotes
            ChannelFilter.ANIMATED -> successState.allEmotes.filter { it.isAnimated }
            ChannelFilter.STATIC -> successState.allEmotes.filter { !it.isAnimated }
        }
    }

    fun onStartChannelImport(useOnlySelected: Boolean = false) {
        val state = _uiState.value
        val successState = state.loadState as? ChannelLoadState.Success ?: return

        val targetEmotes = if (useOnlySelected && state.selectedEmoteIds.isNotEmpty()) {
            successState.allEmotes.filter { state.selectedEmoteIds.contains(it.id) }
        } else {
            getFilteredEmotes()
        }

        if (targetEmotes.size < 3) {
            _uiState.update {
                it.copy(
                    importState = ChannelImportState.Error(
                        "Se requieren al menos 3 emotes para crear un paquete de WhatsApp (seleccionados: ${targetEmotes.size})."
                    )
                )
            }
            return
        }

        val splitResult = PackSplittingEngine.splitEmotes(
            basePackName = successState.streamerName,
            emotes = targetEmotes
        )

        when (splitResult) {
            is StickerResult.Success -> {
                _uiState.update {
                    it.copy(
                        importState = ChannelImportState.Confirming(
                            streamerName = successState.streamerName,
                            splitPlans = splitResult.data
                        )
                    )
                }
            }
            is StickerResult.Failure -> {
                _uiState.update {
                    it.copy(importState = ChannelImportState.Error("Error al dividir el canal: ${splitResult.error}"))
                }
            }
        }
    }

    fun onDismissImportConfirmation() {
        _uiState.update { it.copy(importState = ChannelImportState.Idle) }
    }

    fun onConfirmImport(plans: List<SplitPlan>) {
        val state = _uiState.value
        val streamerName = (state.loadState as? ChannelLoadState.Success)?.streamerName ?: "7TV Channel"

        viewModelScope.launch {
            val allCreatedPacks = mutableListOf<StickerPack>()

            plans.forEachIndexed { index, plan ->
                _uiState.update {
                    it.copy(importState = ChannelImportState.Importing(currentPack = index + 1, totalPacks = plans.size))
                }

                val result = stickerPackRepository.createPacksFromEmotes(
                    basePackName = plan.packName,
                    publisher = streamerName,
                    emotes = plan.emotes
                )

                when (result) {
                    is StickerResult.Success -> {
                        allCreatedPacks.addAll(result.data)
                    }
                    is StickerResult.Failure -> {
                        _uiState.update {
                            it.copy(importState = ChannelImportState.Error("Error creando el pack '${plan.packName}': ${result.error}"))
                        }
                        return@launch
                    }
                }
            }

            _uiState.update {
                it.copy(
                    importState = ChannelImportState.Success(createdPacks = allCreatedPacks),
                    selectedEmoteIds = emptySet()
                )
            }
        }
    }

    class Factory(
        private val sevenTvRepository: SevenTvRepository,
        private val stickerPackRepository: StickerPackRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChannelViewModel(sevenTvRepository, stickerPackRepository) as T
        }
    }
}

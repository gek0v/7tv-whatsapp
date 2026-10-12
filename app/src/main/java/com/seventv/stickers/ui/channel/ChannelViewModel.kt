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
import com.seventv.stickers.core.domain.manager.PackImportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChannelViewModel(
    private val sevenTvRepository: SevenTvRepository,
    private val stickerPackRepository: StickerPackRepository,
    private val packImportManager: PackImportManager? = null
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
            when (val streamerResult = sevenTvRepository.getStreamerEmoteSet(targetQuery)) {
                is StickerResult.Success -> {
                    val set = streamerResult.data
                    _uiState.update {
                        it.copy(
                            loadState = ChannelLoadState.Success(
                                streamerName = set.name.ifBlank { targetQuery },
                                emoteSet = set,
                                allEmotes = set.emotes.distinctBy { emote -> emote.id }
                            )
                        )
                    }
                }
                is StickerResult.Failure -> {
                    _uiState.update {
                        it.copy(loadState = ChannelLoadState.Error("No se encontraron emotes para '$targetQuery'"))
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

    fun onToggleDraftEmote(emote: SevenTvEmote) {
        _uiState.update { state ->
            val current = state.draftEmotes
            val exists = current.any { it.id == emote.id }
            if (exists) {
                state.copy(draftEmotes = current.filterNot { it.id == emote.id })
            } else {
                if (current.size >= 30) {
                    state
                } else {
                    state.copy(draftEmotes = current + emote)
                }
            }
        }
    }

    fun onClearDraft() {
        _uiState.update { it.copy(draftEmotes = emptyList()) }
    }

    fun onCreatePackFromDraft(packName: String, publisher: String = "7TV") {
        val currentState = _uiState.value
        if (currentState.draftEmotes.size < 3) {
            _uiState.update {
                it.copy(
                    importState = ChannelImportState.Error("Se requieren al menos 3 stickers para crear un paquete.")
                )
            }
            return
        }

        val emotesToSave = currentState.draftEmotes
        val resolvedPublisher = (currentState.loadState as? ChannelLoadState.Success)?.streamerName ?: publisher

        viewModelScope.launch {
            when (val result = stickerPackRepository.createPacksFromEmotes(
                basePackName = packName.ifBlank { "7TV Pack" },
                publisher = resolvedPublisher,
                emotes = emotesToSave
            )) {
                is StickerResult.Success -> {
                    _uiState.update {
                        it.copy(
                            draftEmotes = emptyList(),
                            importState = ChannelImportState.Success(result.data)
                        )
                    }
                }
                is StickerResult.Failure -> {
                    _uiState.update {
                        it.copy(importState = ChannelImportState.Error("Error al crear pack: ${result.error}"))
                    }
                }
            }
        }
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

        // Dismiss the modal confirmation dialog immediately so user can freely navigate and use the app
        _uiState.update {
            it.copy(
                importState = ChannelImportState.Idle,
                selectedEmoteIds = emptySet()
            )
        }

        if (packImportManager != null) {
            packImportManager.startImport(streamerName, plans)
            return
        }

        // Inline fallback for standalone testing without PackImportManager
        viewModelScope.launch {
            val allCreatedPacks = mutableListOf<StickerPack>()

            plans.forEachIndexed { index, plan ->
                _uiState.update {
                    it.copy(
                        importState = ChannelImportState.Importing(
                            currentPack = index + 1,
                            totalPacks = plans.size,
                            currentSticker = 0,
                            totalStickers = plan.emotes.size
                        )
                    )
                }

                val result = stickerPackRepository.createPacksFromEmotes(
                    basePackName = plan.packName,
                    publisher = streamerName,
                    emotes = plan.emotes,
                    onProgress = { current, total ->
                        _uiState.update {
                            it.copy(
                                importState = ChannelImportState.Importing(
                                    currentPack = index + 1,
                                    totalPacks = plans.size,
                                    currentSticker = current,
                                    totalStickers = total
                                )
                            )
                        }
                    }
                )

                when (result) {
                    is StickerResult.Success -> {
                        allCreatedPacks.addAll(result.data)
                    }
                    is StickerResult.Failure -> {
                        val errorDetail = when (val err = result.error) {
                            is com.seventv.stickers.core.common.result.StickerError.ExceedsSizeLimit ->
                                "Uno de los stickers supera el tamaño permitido"
                            is com.seventv.stickers.core.common.result.StickerError.InsufficientStickers ->
                                "Se necesitan al menos ${err.min} stickers"
                            is com.seventv.stickers.core.common.result.StickerError.ExceedsPackLimit ->
                                "El paquete supera el límite de ${err.max} stickers"
                            is com.seventv.stickers.core.common.result.StickerError.NetworkError ->
                                err.message
                            is com.seventv.stickers.core.common.result.StickerError.NativeEncodingFailed ->
                                "Fallo en la codificación WebP: ${err.details}"
                            else -> err.toString()
                        }
                        _uiState.update {
                            it.copy(importState = ChannelImportState.Error("Error creando el pack '${plan.packName}': $errorDetail"))
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
        private val stickerPackRepository: StickerPackRepository,
        private val packImportManager: PackImportManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChannelViewModel(sevenTvRepository, stickerPackRepository, packImportManager) as T
        }
    }
}

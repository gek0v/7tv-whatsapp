package com.seventv.stickers.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.data.repository.SevenTvRepository
import com.seventv.stickers.data.repository.StickerPackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing emote exploration, search debouncing, type filtering, and active draft pack creation.
 */
class ExploreViewModel(
    private val sevenTvRepository: SevenTvRepository,
    private val stickerPackRepository: StickerPackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            stickerPackRepository.observeAllPacks().collect { packs ->
                _uiState.update { it.copy(existingPacks = packs) }
            }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
        const val MAX_DRAFT_STICKERS = 30
        const val MIN_DRAFT_STICKERS = 3
        const val DEFAULT_PACK_NAME = "7TV Pack"
        const val DEFAULT_PUBLISHER = "7TV"
    }

    /**
     * Updates the active search query and debounces 400ms before executing the search.
     * An empty or blank query immediately resets the search results state to [SearchResultsState.Idle].
     */
    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = SearchResultsState.Idle) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(searchResults = SearchResultsState.Loading) }

            when (val result = sevenTvRepository.searchEmotes(query.trim())) {
                is StickerResult.Success -> {
                    _uiState.update { state ->
                        val searchState = if (result.data.isEmpty()) {
                            SearchResultsState.Empty(query.trim())
                        } else {
                            SearchResultsState.Success(result.data)
                        }
                        state.copy(searchResults = searchState)
                    }
                }
                is StickerResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            searchResults = SearchResultsState.Error(
                                result.error.toSearchErrorMessage()
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Updates the active emote type filter.
     */
    fun onFilterSelected(filter: EmoteFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    /**
     * Toggles an emote in the active draft.
     * Enforces:
     * - No duplicates (toggling an already-drafted emote removes it).
     * - Ceiling of [MAX_DRAFT_STICKERS] (30 stickers max, additions rejected if at limit).
     */
    fun onToggleDraftEmote(emote: SevenTvEmote) {
        _uiState.update { state ->
            val current = state.draftEmotes
            val exists = current.any { it.id == emote.id }
            if (exists) {
                state.copy(draftEmotes = current.filterNot { it.id == emote.id })
            } else {
                if (current.size >= MAX_DRAFT_STICKERS) {
                    state
                } else {
                    state.copy(draftEmotes = current + emote)
                }
            }
        }
    }

    /**
     * Clears all emotes from the active draft.
     */
    fun onClearDraft() {
        _uiState.update { it.copy(draftEmotes = emptyList()) }
    }

    /**
     * Opens the save pack modal dialog, defaulting the pack name to [DEFAULT_PACK_NAME] if empty.
     */
    fun onOpenSavePackDialog() {
        _uiState.update { state ->
            state.copy(
                isSavePackDialogOpen = true,
                draftPackName = if (state.draftPackName.isBlank()) DEFAULT_PACK_NAME else state.draftPackName
            )
        }
    }

    /**
     * Closes the save pack modal dialog.
     */
    fun onDismissSavePackDialog() {
        _uiState.update { it.copy(isSavePackDialogOpen = false) }
    }

    /**
     * Updates the proposed pack name in the save dialog.
     */
    fun onDraftPackNameChanged(name: String) {
        _uiState.update { it.copy(draftPackName = name) }
    }

    /**
     * Creates a sticker pack from the active draft with the given pack name.
     */
    fun onCreatePackFromDraft(packName: String, publisher: String = DEFAULT_PUBLISHER) {
        onDraftPackNameChanged(packName)
        onConfirmSavePack(publisher)
    }

    /**
     * Confirms pack creation:
     * - Verifies that the draft contains at least [MIN_DRAFT_STICKERS] (3) stickers.
     * - Invokes [StickerPackRepository.createPacksFromEmotes].
     * - Clears the draft and emits [PackCreationState.Success] on completion.
     */
    fun onConfirmSavePack(publisher: String = DEFAULT_PUBLISHER) {
        val currentState = _uiState.value
        if (currentState.draftEmotes.size < MIN_DRAFT_STICKERS) {
            _uiState.update {
                it.copy(
                    packCreationState = PackCreationState.Error(
                        "Se requieren al menos $MIN_DRAFT_STICKERS stickers para crear un paquete"
                    )
                )
            }
            return
        }

        val packName = currentState.draftPackName.ifBlank { DEFAULT_PACK_NAME }
        val emotesToSave = currentState.draftEmotes

        _uiState.update {
            it.copy(
                isSavePackDialogOpen = false,
                packCreationState = PackCreationState.Creating
            )
        }

        viewModelScope.launch {
            when (val result = stickerPackRepository.createPacksFromEmotes(packName, publisher, emotesToSave)) {
                is StickerResult.Success -> {
                    _uiState.update {
                        it.copy(
                            draftEmotes = emptyList(),
                            packCreationState = PackCreationState.Success(result.data)
                        )
                    }
                }
                is StickerResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            packCreationState = PackCreationState.Error(
                                result.error.toUserFriendlyMessage()
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Resets the pack creation state back to [PackCreationState.Idle].
     */
    fun onDismissPackCreationState() {
        _uiState.update { it.copy(packCreationState = PackCreationState.Idle) }
    }

    /**
     * Opens the dialog to add emotes to an existing pack.
     * Defaults to the current active draft emotes if not specified.
     */
    fun onOpenAddToExistingPackDialog(emotes: List<SevenTvEmote> = _uiState.value.draftEmotes) {
        if (emotes.isEmpty()) return
        _uiState.update {
            it.copy(
                isAddToExistingPackDialogOpen = true,
                emotesToAddToPack = emotes
            )
        }
    }

    /**
     * Dismisses the dialog to add emotes to an existing pack.
     */
    fun onDismissAddToExistingPackDialog() {
        _uiState.update {
            it.copy(
                isAddToExistingPackDialogOpen = false,
                emotesToAddToPack = emptyList()
            )
        }
    }

    /**
     * Adds the selected emotes ([ExploreUiState.emotesToAddToPack]) to the specified existing pack.
     * On success, removes any successfully added emotes from the active draft, closes dialog,
     * and sets a success message.
     */
    fun onAddEmotesToPack(packIdentifier: String) {
        val emotesToAdd = _uiState.value.emotesToAddToPack
        if (emotesToAdd.isEmpty()) return

        val targetPack = _uiState.value.existingPacks.find { it.identifier == packIdentifier }
        val packName = targetPack?.name ?: "Pack"

        _uiState.update {
            it.copy(
                isAddingToPack = true,
                isAddToExistingPackDialogOpen = false
            )
        }

        viewModelScope.launch {
            when (val result = stickerPackRepository.addEmotesToPack(packIdentifier, emotesToAdd)) {
                is StickerResult.Success -> {
                    val addedIds = emotesToAdd.map { it.id }.toSet()
                    _uiState.update { state ->
                        state.copy(
                            isAddingToPack = false,
                            draftEmotes = state.draftEmotes.filterNot { it.id in addedIds },
                            emotesToAddToPack = emptyList(),
                            addToPackSuccessMessage = "¡${emotesToAdd.size} sticker${if (emotesToAdd.size > 1) "s" else ""} añadido${if (emotesToAdd.size > 1) "s" else ""} a \"$packName\"!",
                            addToPackErrorMessage = null
                        )
                    }
                }
                is StickerResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            isAddingToPack = false,
                            addToPackErrorMessage = result.error.toUserFriendlyMessage(),
                            addToPackSuccessMessage = null
                        )
                    }
                }
            }
        }
    }

    /**
     * Clears snackbar / notification messages related to adding stickers to packs.
     */
    fun onClearAddToPackMessages() {
        _uiState.update {
            it.copy(
                addToPackSuccessMessage = null,
                addToPackErrorMessage = null
            )
        }
    }

    private fun StickerError.toSearchErrorMessage(): String = when (this) {
        is StickerError.NetworkError -> message
        StickerError.RateLimited -> "Límite de peticiones de 7TV alcanzado"
        StickerError.ResourceNotFound -> "No se encontraron resultados"
        else -> "Error al buscar emotes en 7TV"
    }

    private fun StickerError.toUserFriendlyMessage(): String = when (this) {
        is StickerError.InsufficientStickers -> "Se necesitan al menos $min stickers (actual: $actual)"
        is StickerError.ExceedsPackLimit -> "El paquete supera el límite de $max stickers (actual: $count)"
        is StickerError.NetworkError -> message
        is StickerError.StorageIoError -> cause.message ?: "Error de almacenamiento al guardar los stickers"
        is StickerError.NativeEncodingFailed -> "Fallo en la codificación WebP: $details"
        is StickerError.ExceedsSizeLimit -> "El sticker supera el tamaño máximo permitido para WhatsApp"
        StickerError.CorruptedSourceData -> "Datos de imagen no válidos o dañados"
        is StickerError.UnsupportedFormat -> if (mimeType.contains(" ")) mimeType else "Formato no compatible: $mimeType"
        StickerError.ResourceNotFound -> "No se encontró el emote requerido"
        StickerError.RateLimited -> "Límite de peticiones de 7TV alcanzado"
        is StickerError.PackNotFound -> "Paquete no encontrado: $identifier"
    }

    class Factory(
        private val sevenTvRepository: SevenTvRepository,
        private val stickerPackRepository: StickerPackRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExploreViewModel(sevenTvRepository, stickerPackRepository) as T
        }
    }
}

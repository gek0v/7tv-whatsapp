package com.seventv.stickers.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.toSevenTvEmote
import com.seventv.stickers.data.repository.AggregatedEmoteCatalogRepository
import com.seventv.stickers.data.repository.SevenTvRepository
import com.seventv.stickers.data.repository.StickerPackRepository
import com.seventv.stickers.ui.channel.ChannelProviderFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing emote exploration, search debouncing, type filtering, and active draft pack creation.
 */
class ExploreViewModel(
    private val sevenTvRepository: SevenTvRepository,
    private val stickerPackRepository: StickerPackRepository,
    private val aggregatedRepository: AggregatedEmoteCatalogRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var lastQueryLoadedAllProviders: Boolean = false

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
     * Loads emotes for a category (TRENDING or TOP) when query is empty.
     */
    fun loadCategoryEmotes(category: ExploreCategory = _uiState.value.selectedCategory) {
        val targetProviders = _uiState.value.activeProviderFilter.provider?.let { setOf(it) }
        lastQueryLoadedAllProviders = targetProviders == null

        _uiState.update {
            it.copy(
                selectedCategory = category,
                searchResults = SearchResultsState.Loading,
                currentPage = 1,
                hasMorePages = true,
                isLoadingMore = false
            )
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (aggregatedRepository != null) {
                var receivedNonEmpty = false
                aggregatedRepository.searchAllProvidersProgressive(
                    query = "",
                    page = 1,
                    limit = 50,
                    targetProviders = targetProviders
                ).collect { result ->
                    when (result) {
                        is StickerResult.Success -> {
                            val mappedByProvider = result.data.mapValues { (_, list) -> list.map { it.toSevenTvEmote() } }
                            val interleaved = interleaveByProvider(mappedByProvider)
                            if (interleaved.isNotEmpty()) {
                                receivedNonEmpty = true
                                _uiState.update { state ->
                                    state.copy(
                                        searchResults = SearchResultsState.Success(interleaved, mappedByProvider),
                                        hasMorePages = mappedByProvider.values.any { it.size >= 50 }
                                    )
                                }
                            }
                        }
                        is StickerResult.Failure -> {
                            if (!receivedNonEmpty) {
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
                if (!receivedNonEmpty && _uiState.value.searchResults is SearchResultsState.Loading) {
                    _uiState.update {
                        it.copy(
                            searchResults = SearchResultsState.Empty(category.label),
                            hasMorePages = false
                        )
                    }
                }
            } else {
                when (val result = sevenTvRepository.searchEmotes(query = "", page = 1, limit = 100, category = category.apiValue)) {
                    is StickerResult.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                searchResults = if (result.data.isEmpty()) {
                                    SearchResultsState.Empty(category.label)
                                } else {
                                    SearchResultsState.Success(
                                        emotes = result.data,
                                        emotesByProvider = mapOf(EmoteProvider.SEVENTV to result.data)
                                    )
                                },
                                hasMorePages = result.data.size >= 100
                            )
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
    }

    /**
     * Updates the active category (TRENDING or TOP) and refreshes results.
     */
    fun onCategorySelected(category: ExploreCategory) {
        if (_uiState.value.selectedCategory == category && _uiState.value.searchResults !is SearchResultsState.Error) return
        _uiState.update { it.copy(selectedCategory = category) }
        val currentQuery = _uiState.value.query.trim()
        if (currentQuery.isNotEmpty()) {
            performSearch(currentQuery, null)
        } else {
            loadCategoryEmotes(category)
        }
    }

    /**
     * Updates the active search query and debounces 400ms before executing the search.
     * An empty or blank query reloads the currently selected category emotes.
     * Non-empty searches query globally across all 7TV emotes without category constraints.
     */
    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            loadCategoryEmotes(_uiState.value.selectedCategory)
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch(query.trim(), null)
        }
    }

    private fun performSearch(query: String, category: String?) {
        val targetProviders = _uiState.value.activeProviderFilter.provider?.let { setOf(it) }
        lastQueryLoadedAllProviders = targetProviders == null

        _uiState.update {
            it.copy(
                searchResults = SearchResultsState.Loading,
                currentPage = 1,
                hasMorePages = true,
                isLoadingMore = false
            )
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (aggregatedRepository != null && query.isNotBlank()) {
                var receivedNonEmpty = false
                aggregatedRepository.searchAllProvidersProgressive(
                    query = query,
                    page = 1,
                    limit = 50,
                    targetProviders = targetProviders
                ).collect { result ->
                    when (result) {
                        is StickerResult.Success -> {
                            val mappedByProvider = result.data.mapValues { (_, list) -> list.map { it.toSevenTvEmote() } }
                            val interleaved = interleaveByProvider(mappedByProvider)
                            if (interleaved.isNotEmpty()) {
                                receivedNonEmpty = true
                                _uiState.update { state ->
                                    state.copy(
                                        searchResults = SearchResultsState.Success(interleaved, mappedByProvider),
                                        hasMorePages = mappedByProvider.values.any { it.size >= 50 }
                                    )
                                }
                            }
                        }
                        is StickerResult.Failure -> {
                            if (!receivedNonEmpty) {
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
                if (!receivedNonEmpty && _uiState.value.searchResults is SearchResultsState.Loading) {
                    _uiState.update {
                        it.copy(
                            searchResults = SearchResultsState.Empty(query),
                            hasMorePages = false
                        )
                    }
                }
            } else {
                when (val result = sevenTvRepository.searchEmotes(query = query, page = 1, limit = 100, category = category)) {
                    is StickerResult.Success -> {
                        _uiState.update { state ->
                            val searchState = if (result.data.isEmpty()) {
                                SearchResultsState.Empty(query)
                            } else {
                                SearchResultsState.Success(
                                    emotes = result.data,
                                    emotesByProvider = mapOf(EmoteProvider.SEVENTV to result.data)
                                )
                            }
                            state.copy(
                                searchResults = searchState,
                                hasMorePages = result.data.size >= 100
                            )
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
    }

    /**
     * Loads the next page of emotes (for infinite scroll) and appends them to current results.
     */
    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.hasMorePages) return
        val currentResults = currentState.searchResults as? SearchResultsState.Success ?: return

        val nextPage = currentState.currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            val query = currentState.query.trim()
            val useAggregated = aggregatedRepository != null

            if (useAggregated) {
                val targetProviders = currentState.activeProviderFilter.provider?.let { setOf(it) }
                val result = aggregatedRepository.searchAllProvidersProgressive(
                    query = query,
                    page = nextPage,
                    limit = 50,
                    targetProviders = targetProviders
                ).last()
                when (result) {
                    is StickerResult.Success -> {
                        val mappedByProvider = result.data.mapValues { (_, list) -> list.map { it.toSevenTvEmote() } }
                        val newEmotes = interleaveByProvider(mappedByProvider)
                        _uiState.update { state ->
                            val combined = (currentResults.emotes + newEmotes).distinctBy { "${it.provider.name}:${it.id}" }
                            val mergedMap = combined.groupBy { it.provider }
                            state.copy(
                                searchResults = SearchResultsState.Success(combined, mergedMap),
                                currentPage = nextPage,
                                isLoadingMore = false,
                                hasMorePages = currentState.activeProviderFilter.provider?.let { target ->
                                    (mappedByProvider[target]?.size ?: 0) >= 50
                                } ?: mappedByProvider.values.any { it.size >= 50 }
                            )
                        }
                    }
                    is StickerResult.Failure -> {
                        _uiState.update { state -> state.copy(isLoadingMore = false) }
                    }
                }
            } else {
                val category = if (query.isBlank()) currentState.selectedCategory.apiValue else null
                when (val result = sevenTvRepository.searchEmotes(query = query, page = nextPage, limit = 100, category = category)) {
                    is StickerResult.Success -> {
                        val newEmotes = result.data
                        _uiState.update { state ->
                            val combined = (currentResults.emotes + newEmotes).distinctBy { "${it.provider.name}:${it.id}" }
                            state.copy(
                                searchResults = SearchResultsState.Success(combined, combined.groupBy { it.provider }),
                                currentPage = nextPage,
                                isLoadingMore = false,
                                hasMorePages = newEmotes.size >= 100
                            )
                        }
                    }
                    is StickerResult.Failure -> {
                        _uiState.update { state ->
                            state.copy(isLoadingMore = false)
                        }
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

    fun onProviderFilterSelected(filter: ChannelProviderFilter) {
        _uiState.update { it.copy(activeProviderFilter = filter) }
        if (aggregatedRepository != null) {
            val currentResults = _uiState.value.searchResults
            val needsReload = currentResults is SearchResultsState.Idle ||
                (!lastQueryLoadedAllProviders && (
                    filter == ChannelProviderFilter.ALL ||
                        (currentResults as? SearchResultsState.Success)?.emotesByProvider?.containsKey(filter.provider) != true
                    ))
            if (needsReload) {
                val currentQuery = _uiState.value.query.trim()
                if (currentQuery.isBlank()) {
                    loadCategoryEmotes(_uiState.value.selectedCategory)
                } else {
                    performSearch(currentQuery, null)
                }
            }
        }
    }

    private fun interleaveByProvider(
        map: Map<EmoteProvider, List<SevenTvEmote>>
    ): List<SevenTvEmote> {
        val queues = EmoteProvider.entries.mapNotNull { provider ->
            map[provider]?.takeIf { it.isNotEmpty() }?.iterator()
        }
        val result = mutableListOf<SevenTvEmote>()
        var addedAny: Boolean
        do {
            addedAny = false
            for (iterator in queues) {
                if (iterator.hasNext()) {
                    result.add(iterator.next())
                    addedAny = true
                }
            }
        } while (addedAny)
        return result.distinctBy { "${it.provider.name}:${it.id}" }
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
        is StickerError.ValidationError -> message
    }

    class Factory(
        private val sevenTvRepository: SevenTvRepository,
        private val stickerPackRepository: StickerPackRepository,
        private val aggregatedRepository: AggregatedEmoteCatalogRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExploreViewModel(sevenTvRepository, stickerPackRepository, aggregatedRepository) as T
        }
    }
}

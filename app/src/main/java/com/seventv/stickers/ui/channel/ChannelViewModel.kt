package com.seventv.stickers.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.engine.PackSplittingEngine
import com.seventv.stickers.core.domain.engine.SplitPlan
import com.seventv.stickers.core.domain.manager.PackImportManager
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import com.seventv.stickers.core.network.model.toSevenTvEmote
import com.seventv.stickers.data.repository.AggregatedEmoteCatalogRepository
import com.seventv.stickers.data.repository.SevenTvRepository
import com.seventv.stickers.data.repository.StickerPackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ChannelViewModel(
    private val sevenTvRepository: SevenTvRepository,
    private val stickerPackRepository: StickerPackRepository,
    private val packImportManager: PackImportManager? = null,
    private val aggregatedRepository: AggregatedEmoteCatalogRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelUiState())
    private var searchJob: Job? = null
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            stickerPackRepository.observeAllPacks().collect { packs ->
                _uiState.update { it.copy(existingPacks = packs) }
            }
        }
    }

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
                importState = ChannelImportState.Idle,
                activeProviderFilter = ChannelProviderFilter.ALL
            )
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (aggregatedRepository != null) {
                var emittedSuccess = false
                aggregatedRepository.getUnifiedChannelEmotesProgressive(targetQuery).collect { unifiedResult ->
                    when (unifiedResult) {
                        is StickerResult.Success -> {
                            emittedSuccess = true
                            val unified = unifiedResult.data
                            val emotesByProvider = unified.emotesByProvider.mapValues { (_, emotes) ->
                                emotes.map { it.toSevenTvEmote() }
                            }
                            val allEmotes = emotesByProvider.values.flatten().distinctBy { it.id }

                            val sevenTvSet = SevenTvEmoteSet(
                                id = unified.activeSevenTvSetId ?: targetQuery,
                                name = unified.channelName,
                                ownerName = unified.channelName,
                                emotes = emotesByProvider[EmoteProvider.SEVENTV].orEmpty()
                            )

                            _uiState.update {
                                it.copy(
                                    loadState = ChannelLoadState.Success(
                                        streamerName = unified.channelName,
                                        username = targetQuery,
                                        avatarUrl = unified.avatarUrl,
                                        availableSets = unified.sevenTvSets,
                                        currentSetId = sevenTvSet.id,
                                        emoteSet = sevenTvSet,
                                        allEmotes = allEmotes,
                                        emotesByProvider = emotesByProvider
                                    )
                                )
                            }
                        }
                        is StickerResult.Failure -> {
                            if (!emittedSuccess) {
                                _uiState.update {
                                    it.copy(loadState = ChannelLoadState.Error("No se encontraron emotes para '$targetQuery'"))
                                }
                            }
                        }
                    }
                }
            } else {
                when (val channelResult = sevenTvRepository.getChannelData(targetQuery)) {
                    is StickerResult.Success -> {
                        val channel = channelResult.data
                        val set = channel.activeSet
                        val dedupedEmotes = set.emotes.distinctBy { emote -> emote.id }
                        _uiState.update {
                            it.copy(
                                loadState = ChannelLoadState.Success(
                                    streamerName = channel.streamerName,
                                    username = channel.username,
                                    avatarUrl = channel.avatarUrl,
                                    availableSets = channel.availableSets,
                                    currentSetId = set.id,
                                    emoteSet = set,
                                    allEmotes = dedupedEmotes,
                                    emotesByProvider = mapOf(EmoteProvider.SEVENTV to dedupedEmotes)
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
    }

    fun onSelectEmoteSet(setId: String) {
        val currentState = _uiState.value
        val currentSuccess = currentState.loadState as? ChannelLoadState.Success ?: return
        if (currentSuccess.currentSetId == setId || currentSuccess.isLoadingSet) return

        val targetSummary = currentSuccess.availableSets.find { it.id == setId }
        val setName = targetSummary?.name ?: "Pack"

        _uiState.update { state ->
            state.copy(
                loadState = currentSuccess.copy(isLoadingSet = true),
                selectedEmoteIds = emptySet()
            )
        }

        viewModelScope.launch {
            when (val setResult = sevenTvRepository.getEmoteSet(setId)) {
                is StickerResult.Success -> {
                    val newSet = setResult.data.copy(
                        name = setName,
                        ownerName = currentSuccess.streamerName
                    )
                    _uiState.update { state ->
                        val success = state.loadState as? ChannelLoadState.Success ?: return@update state
                        val newSevenTvEmotes = newSet.emotes.distinctBy { it.id }
                        val updatedMap = success.emotesByProvider.toMutableMap()
                        updatedMap[EmoteProvider.SEVENTV] = newSevenTvEmotes
                        val all = updatedMap.values.flatten().distinctBy { it.id }
                        state.copy(
                            loadState = success.copy(
                                currentSetId = setId,
                                emoteSet = newSet,
                                allEmotes = all,
                                emotesByProvider = updatedMap,
                                isLoadingSet = false
                            )
                        )
                    }
                }
                is StickerResult.Failure -> {
                    _uiState.update { state ->
                        val success = state.loadState as? ChannelLoadState.Success ?: return@update state
                        state.copy(
                            loadState = success.copy(isLoadingSet = false),
                            addToPackErrorMessage = "Error al cargar el paquete: ${setResult.error.toUserFriendlyMessage()}"
                        )
                    }
                }
            }
        }
    }

    fun onFilterSelected(filter: ChannelFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun onProviderFilterSelected(filter: ChannelProviderFilter) {
        _uiState.update { it.copy(activeProviderFilter = filter) }
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
        val successState = currentState.loadState as? ChannelLoadState.Success
        val resolvedPublisher = successState?.streamerName ?: publisher
        val originSetId = successState?.currentSetId

        viewModelScope.launch {
            when (val result = stickerPackRepository.createPacksFromEmotes(
                basePackName = packName.ifBlank { "Sticker Pack" },
                publisher = resolvedPublisher,
                emotes = emotesToSave,
                originSetId = originSetId,
                originStreamerName = resolvedPublisher
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
        return _uiState.value.filteredEmotes
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
        val successState = state.loadState as? ChannelLoadState.Success
        val streamerName = successState?.streamerName ?: "Streaming Channel"
        val currentSetId = successState?.currentSetId

        _uiState.update {
            it.copy(
                importState = ChannelImportState.Idle,
                selectedEmoteIds = emptySet()
            )
        }

        if (packImportManager != null) {
            packImportManager.startImport(streamerName, plans, originSetId = currentSetId)
            return
        }

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
                    },
                    originSetId = currentSetId,
                    originStreamerName = streamerName
                )

                when (result) {
                    is StickerResult.Success -> {
                        allCreatedPacks.addAll(result.data)
                    }
                    is StickerResult.Failure -> {
                        val errorDetail = when (val err = result.error) {
                            is StickerError.ExceedsSizeLimit ->
                                "Uno de los stickers supera el tamaño permitido"
                            is StickerError.InsufficientStickers ->
                                "Se necesitan al menos ${err.min} stickers"
                            is StickerError.ExceedsPackLimit ->
                                "El paquete supera el límite de ${err.max} stickers"
                            is StickerError.NetworkError ->
                                err.message
                            is StickerError.NativeEncodingFailed ->
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

    fun onOpenAddToExistingPackDialog(emotes: List<SevenTvEmote> = _uiState.value.draftEmotes) {
        if (emotes.isEmpty()) return
        _uiState.update {
            it.copy(
                isAddToExistingPackDialogOpen = true,
                emotesToAddToPack = emotes
            )
        }
    }

    fun onDismissAddToExistingPackDialog() {
        _uiState.update {
            it.copy(
                isAddToExistingPackDialogOpen = false,
                emotesToAddToPack = emptyList()
            )
        }
    }

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

    fun onClearAddToPackMessages() {
        _uiState.update {
            it.copy(
                addToPackSuccessMessage = null,
                addToPackErrorMessage = null
            )
        }
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
        StickerError.RateLimited -> "Límite de peticiones alcanzado"
        is StickerError.PackNotFound -> "Paquete no encontrado: $identifier"
        is StickerError.ValidationError -> message
    }

    class Factory(
        private val sevenTvRepository: SevenTvRepository,
        private val stickerPackRepository: StickerPackRepository,
        private val packImportManager: PackImportManager? = null,
        private val aggregatedRepository: AggregatedEmoteCatalogRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChannelViewModel(
                sevenTvRepository = sevenTvRepository,
                stickerPackRepository = stickerPackRepository,
                packImportManager = packImportManager,
                aggregatedRepository = aggregatedRepository
            ) as T
        }
    }
}

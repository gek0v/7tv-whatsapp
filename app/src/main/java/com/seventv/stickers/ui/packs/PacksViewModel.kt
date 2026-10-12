package com.seventv.stickers.ui.packs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.data.repository.StickerPackRepository
import com.seventv.stickers.ui.components.ChatSimulatorMode
import com.seventv.stickers.whatsapp.WhatsAppIntentHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PacksViewModel(
    private val stickerPackRepository: StickerPackRepository,
    private val sevenTvRepository: com.seventv.stickers.data.repository.SevenTvRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PacksUiState())
    val uiState: StateFlow<PacksUiState> = _uiState.asStateFlow()

    init {
        observePacks()
    }

    private fun observePacks() {
        viewModelScope.launch {
            stickerPackRepository.observeAllPacks()
                .catch { e ->
                    _uiState.update { it.copy(loadState = PacksLoadState.Error(e.message ?: "Error cargando paquetes")) }
                }
                .collect { packs ->
                    _uiState.update { current ->
                        val updatedDetail = if (current.selectedPackDetail != null) {
                            packs.find { it.identifier == current.selectedPackDetail.identifier }
                        } else null
                        current.copy(
                            loadState = PacksLoadState.Success(packs),
                            selectedPackDetail = updatedDetail ?: current.selectedPackDetail
                        )
                    }
                }
        }
    }

    fun onSelectPackDetail(packIdentifier: String) {
        val currentPacks = (_uiState.value.loadState as? PacksLoadState.Success)?.packs
        val pack = currentPacks?.find { it.identifier == packIdentifier }
        if (pack != null) {
            _uiState.update { it.copy(selectedPackDetail = pack) }
        } else {
            viewModelScope.launch {
                val result = stickerPackRepository.getPack(packIdentifier)
                if (result is com.seventv.stickers.core.common.result.StickerResult.Success) {
                    _uiState.update { it.copy(selectedPackDetail = result.data) }
                }
            }
        }
    }

    fun onClearSelectedPackDetail() {
        _uiState.update { it.copy(selectedPackDetail = null) }
    }

    fun onSetChatSimulatorMode(mode: ChatSimulatorMode) {
        _uiState.update { it.copy(chatSimulatorMode = mode) }
    }

    // --- Single Pack Export ---
    fun onAddPackToWhatsApp(context: Context, pack: StickerPack) {
        val isInstalled = WhatsAppIntentHelper.isWhatsAppInstalled(context)
        if (!isInstalled) {
            _uiState.update { it.copy(exportEvent = WhatsAppExportEvent.WhatsAppNotInstalled) }
            return
        }

        _uiState.update { it.copy(exportEvent = WhatsAppExportEvent.LaunchIntent(pack)) }
    }

    fun syncWhitelist(context: Context, packIdentifier: String) {
        viewModelScope.launch {
            stickerPackRepository.syncWhitelistStatus(context, packIdentifier)
        }
    }

    // --- Single Pack Delete ---
    fun onOpenDeleteDialog(pack: StickerPack) {
        _uiState.update { it.copy(isDeleteConfirmDialogOpen = true, packToDelete = pack) }
    }

    fun onDismissDeleteDialog() {
        _uiState.update { it.copy(isDeleteConfirmDialogOpen = false, packToDelete = null) }
    }

    fun onConfirmDeletePack() {
        val pack = _uiState.value.packToDelete ?: return
        viewModelScope.launch {
            stickerPackRepository.deletePack(pack.identifier)
            _uiState.update {
                it.copy(
                    isDeleteConfirmDialogOpen = false,
                    packToDelete = null,
                    selectedPackDetail = if (it.selectedPackDetail?.identifier == pack.identifier) null else it.selectedPackDetail
                )
            }
        }
    }

    // --- Multi-Selection & Batch Actions ---
    fun onToggleSelectionMode() {
        _uiState.update {
            if (it.isSelectionMode) {
                it.copy(isSelectionMode = false, selectedPackIds = emptySet())
            } else {
                it.copy(isSelectionMode = true, selectedPackIds = emptySet())
            }
        }
    }

    fun onTogglePackSelection(packId: String) {
        _uiState.update { current ->
            val newSelection = if (packId in current.selectedPackIds) {
                current.selectedPackIds - packId
            } else {
                current.selectedPackIds + packId
            }
            current.copy(selectedPackIds = newSelection)
        }
    }

    fun onSelectAllPacks() {
        val packs = (_uiState.value.loadState as? PacksLoadState.Success)?.packs ?: return
        _uiState.update { it.copy(selectedPackIds = packs.map { pack -> pack.identifier }.toSet()) }
    }

    fun onClearSelection() {
        _uiState.update { it.copy(selectedPackIds = emptySet()) }
    }

    fun onOpenBulkDeleteDialog() {
        if (_uiState.value.selectedPackIds.isNotEmpty()) {
            _uiState.update { it.copy(isBulkDeleteConfirmDialogOpen = true) }
        }
    }

    fun onDismissBulkDeleteDialog() {
        _uiState.update { it.copy(isBulkDeleteConfirmDialogOpen = false) }
    }

    fun onConfirmBulkDelete() {
        val ids = _uiState.value.selectedPackIds.toList()
        if (ids.isEmpty()) {
            _uiState.update { it.copy(isBulkDeleteConfirmDialogOpen = false) }
            return
        }
        viewModelScope.launch {
            stickerPackRepository.deletePacks(ids)
            _uiState.update { current ->
                val detailRemoved = if (current.selectedPackDetail != null && current.selectedPackDetail.identifier in ids) null else current.selectedPackDetail
                current.copy(
                    isBulkDeleteConfirmDialogOpen = false,
                    isSelectionMode = false,
                    selectedPackIds = emptySet(),
                    selectedPackDetail = detailRemoved
                )
            }
        }
    }

    // --- Batch WhatsApp Add Chaining ---
    fun onStartBatchWhatsAppExport(context: Context) {
        val isInstalled = WhatsAppIntentHelper.isWhatsAppInstalled(context)
        if (!isInstalled) {
            _uiState.update { it.copy(exportEvent = WhatsAppExportEvent.WhatsAppNotInstalled) }
            return
        }

        val packs = (_uiState.value.loadState as? PacksLoadState.Success)?.packs ?: return
        val selectedPacks = packs.filter { it.identifier in _uiState.value.selectedPackIds }
        if (selectedPacks.isEmpty()) return

        val firstPack = selectedPacks.first()
        val remainingQueue = selectedPacks.drop(1)
        _uiState.update {
            it.copy(
                exportQueue = remainingQueue,
                exportEvent = WhatsAppExportEvent.LaunchIntent(firstPack)
            )
        }
    }

    fun onAdvanceExportQueue(context: Context) {
        val currentQueue = _uiState.value.exportQueue
        if (currentQueue.isNotEmpty()) {
            val nextPack = currentQueue.first()
            val remaining = currentQueue.drop(1)
            _uiState.update {
                it.copy(
                    exportQueue = remaining,
                    exportEvent = WhatsAppExportEvent.LaunchIntent(nextPack)
                )
            }
        } else if (_uiState.value.isSelectionMode) {
            _uiState.update {
                it.copy(
                    isSelectionMode = false,
                    selectedPackIds = emptySet()
                )
            }
        }
    }

    // --- Rename Pack ---
    fun onOpenRenameDialog(pack: StickerPack) {
        _uiState.update { it.copy(isRenameDialogOpen = true, packToRename = pack) }
    }

    fun onDismissRenameDialog() {
        _uiState.update { it.copy(isRenameDialogOpen = false, packToRename = null) }
    }

    fun onConfirmRename(newName: String) {
        val pack = _uiState.value.packToRename ?: return
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            stickerPackRepository.renamePack(pack.identifier, trimmed)
            _uiState.update { current ->
                val updatedDetail = if (current.selectedPackDetail?.identifier == pack.identifier) {
                    current.selectedPackDetail.copy(name = trimmed)
                } else current.selectedPackDetail
                current.copy(
                    isRenameDialogOpen = false,
                    packToRename = null,
                    selectedPackDetail = updatedDetail
                )
            }
        }
    }

    // --- Sticker Operations inside Detail ---
    fun onOpenDeleteStickerDialog(sticker: com.seventv.stickers.core.domain.model.PackSticker) {
        _uiState.update { it.copy(isDeleteStickerConfirmDialogOpen = true, stickerToDelete = sticker) }
    }

    fun onDismissDeleteStickerDialog() {
        _uiState.update { it.copy(isDeleteStickerConfirmDialogOpen = false, stickerToDelete = null) }
    }

    fun onConfirmDeleteSticker(packIdentifier: String) {
        val sticker = _uiState.value.stickerToDelete ?: return
        viewModelScope.launch {
            val result = stickerPackRepository.deleteStickerFromPack(packIdentifier, sticker.id)
            if (result is com.seventv.stickers.core.common.result.StickerResult.Success) {
                _uiState.update {
                    it.copy(
                        isDeleteStickerConfirmDialogOpen = false,
                        stickerToDelete = null,
                        userFeedbackMessage = "Sticker eliminado"
                    )
                }
                onSelectPackDetail(packIdentifier)
            } else {
                _uiState.update {
                    it.copy(
                        isDeleteStickerConfirmDialogOpen = false,
                        stickerToDelete = null,
                        userFeedbackMessage = "Error al eliminar el sticker"
                    )
                }
            }
        }
    }

    // --- Add Stickers Modal ---
    fun onOpenAddStickersDialog() {
        _uiState.update {
            it.copy(
                isAddStickersDialogOpen = true,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
        }
    }

    fun onDismissAddStickersDialog() {
        _uiState.update {
            it.copy(
                isAddStickersDialogOpen = false,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        val repo = sevenTvRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val result = repo.searchEmotes(query.trim())
            if (result is com.seventv.stickers.core.common.result.StickerResult.Success) {
                _uiState.update { it.copy(searchResults = result.data, isSearching = false) }
            } else {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            }
        }
    }

    fun onAddEmoteToPack(packIdentifier: String, emote: com.seventv.stickers.core.network.model.SevenTvEmote) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingStickers = true) }
            val result = stickerPackRepository.addEmotesToPack(packIdentifier, listOf(emote))
            when (result) {
                is com.seventv.stickers.core.common.result.StickerResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAddingStickers = false,
                            isAddStickersDialogOpen = false,
                            userFeedbackMessage = "Sticker añadido a ${it.selectedPackDetail?.name ?: "pack"}"
                        )
                    }
                    onSelectPackDetail(packIdentifier)
                }
                is com.seventv.stickers.core.common.result.StickerResult.Failure -> {
                    val msg = when (val error = result.error) {
                        is com.seventv.stickers.core.common.result.StickerError.ExceedsPackLimit ->
                            "El paquete no puede tener más de ${error.max} stickers"
                        is com.seventv.stickers.core.common.result.StickerError.UnsupportedFormat ->
                            "No puedes mezclar stickers animados y estáticos en el mismo pack"
                        else -> "Error al añadir el sticker"
                    }
                    _uiState.update {
                        it.copy(
                            isAddingStickers = false,
                            userFeedbackMessage = msg
                        )
                    }
                }
            }
        }
    }

    fun onClearFeedbackMessage() {
        _uiState.update { it.copy(userFeedbackMessage = null) }
    }

    fun onClearExportEvent() {
        _uiState.update { it.copy(exportEvent = null) }
    }

    class Factory(
        private val stickerPackRepository: StickerPackRepository,
        private val sevenTvRepository: com.seventv.stickers.data.repository.SevenTvRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PacksViewModel(stickerPackRepository, sevenTvRepository) as T
        }
    }
}

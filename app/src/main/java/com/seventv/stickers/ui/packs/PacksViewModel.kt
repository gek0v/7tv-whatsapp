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
    private val stickerPackRepository: StickerPackRepository
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

    fun onClearExportEvent() {
        _uiState.update { it.copy(exportEvent = null) }
    }

    class Factory(
        private val stickerPackRepository: StickerPackRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PacksViewModel(stickerPackRepository) as T
        }
    }
}

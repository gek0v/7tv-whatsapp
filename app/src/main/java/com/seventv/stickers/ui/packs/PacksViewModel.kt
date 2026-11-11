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
import com.seventv.stickers.core.domain.validator.StickerPackInspector
import java.io.File

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
                        val updatedDetail = if (!current.isDraggingSticker && current.selectedPackDetail != null) {
                            packs.find { it.identifier == current.selectedPackDetail.identifier }
                        } else null
                        val effectiveDetail = if (current.isDraggingSticker) current.selectedPackDetail else (updatedDetail ?: current.selectedPackDetail)
                        val report = if (updatedDetail != null) StickerPackInspector.inspect(updatedDetail) else current.packDiagnosticReport
                        val issues = if (updatedDetail != null && report != null) report.issues.associateBy { it.stickerId } else current.stickerIssues
                        current.copy(
                            loadState = PacksLoadState.Success(packs),
                            selectedPackDetail = effectiveDetail,
                            packDiagnosticReport = report,
                            stickerIssues = issues
                        )
                    }
                }
        }
    }

    fun onSelectPackDetail(packIdentifier: String) {
        val currentPacks = (_uiState.value.loadState as? PacksLoadState.Success)?.packs
        val pack = currentPacks?.find { it.identifier == packIdentifier }
        if (pack != null) {
            val report = StickerPackInspector.inspect(pack)
            _uiState.update {
                it.copy(
                    selectedPackDetail = pack,
                    packDiagnosticReport = report,
                    stickerIssues = report.issues.associateBy { issue -> issue.stickerId }
                )
            }
        } else {
            viewModelScope.launch {
                val result = stickerPackRepository.getPack(packIdentifier)
                if (result is com.seventv.stickers.core.common.result.StickerResult.Success) {
                    val loaded = result.data
                    val report = StickerPackInspector.inspect(loaded)
                    _uiState.update {
                        it.copy(
                            selectedPackDetail = loaded,
                            packDiagnosticReport = report,
                            stickerIssues = report.issues.associateBy { issue -> issue.stickerId }
                        )
                    }
                }
            }
        }
    }

    fun onClearSelectedPackDetail() {
        _uiState.update {
            it.copy(
                selectedPackDetail = null,
                packDiagnosticReport = null,
                stickerIssues = emptyMap(),
                isDiagnosticDialogOpen = false,
                isReorderMode = false,
                isDraggingSticker = false
            )
        }
    }

    fun onSetChatSimulatorMode(mode: ChatSimulatorMode) {
        _uiState.update { it.copy(chatSimulatorMode = mode) }
    }

    // --- Diagnostics & WhatsApp Validation ---
    fun onOpenDiagnosticDialog() {
        val detail = _uiState.value.selectedPackDetail
        if (detail != null) {
            val report = StickerPackInspector.inspect(detail)
            _uiState.update {
                it.copy(
                    packDiagnosticReport = report,
                    stickerIssues = report.issues.associateBy { issue -> issue.stickerId },
                    isDiagnosticDialogOpen = true
                )
            }
        } else {
            _uiState.update { it.copy(isDiagnosticDialogOpen = true) }
        }
    }

    fun onDismissDiagnosticDialog() {
        _uiState.update { it.copy(isDiagnosticDialogOpen = false) }
    }

    fun onWhatsAppPreviewValidationFailed(packId: String) {
        val pack = _uiState.value.selectedPackDetail
        if (pack != null && pack.identifier == packId) {
            val report = StickerPackInspector.inspect(pack)
            _uiState.update {
                it.copy(
                    packDiagnosticReport = report,
                    stickerIssues = report.issues.associateBy { issue -> issue.stickerId },
                    isDiagnosticDialogOpen = true
                )
            }
        } else {
            viewModelScope.launch {
                val res = stickerPackRepository.getPack(packId)
                if (res is com.seventv.stickers.core.common.result.StickerResult.Success) {
                    val report = StickerPackInspector.inspect(res.data)
                    _uiState.update {
                        it.copy(
                            selectedPackDetail = res.data,
                            packDiagnosticReport = report,
                            stickerIssues = report.issues.associateBy { issue -> issue.stickerId },
                            isDiagnosticDialogOpen = true
                        )
                    }
                }
            }
        }
    }

    fun onDeleteInvalidStickers(packIdentifier: String) {
        val report = _uiState.value.packDiagnosticReport ?: return
        val invalidStickerIds = report.issues.map { it.stickerId }.distinct()
        if (invalidStickerIds.isEmpty()) {
            _uiState.update { it.copy(isDiagnosticDialogOpen = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingInvalidStickers = true) }
            var deletedCount = 0
            for (stickerId in invalidStickerIds) {
                val res = stickerPackRepository.deleteStickerFromPack(packIdentifier, stickerId)
                if (res is com.seventv.stickers.core.common.result.StickerResult.Success) {
                    deletedCount++
                }
            }
            _uiState.update {
                it.copy(
                    isDeletingInvalidStickers = false,
                    isDiagnosticDialogOpen = false,
                    userFeedbackMessage = "Se eliminaron $deletedCount sticker(s) con errores."
                )
            }
            onSelectPackDetail(packIdentifier)
        }
    }

    // --- Single Pack Export ---
    fun onAddPackToWhatsApp(context: Context, pack: StickerPack) {
        val isInstalled = WhatsAppIntentHelper.isWhatsAppInstalled(context)
        if (!isInstalled) {
            _uiState.update { it.copy(exportEvent = WhatsAppExportEvent.WhatsAppNotInstalled) }
            return
        }

        if (pack.stickers.size < 3) {
            _uiState.update { it.copy(userFeedbackMessage = "WhatsApp requiere al menos 3 stickers por paquete.") }
            return
        }
        if (pack.stickers.size > 30) {
            _uiState.update { it.copy(userFeedbackMessage = "WhatsApp permite un máximo de 30 stickers por paquete.") }
            return
        }

        val report = StickerPackInspector.inspect(pack)
        if (!report.isValid) {
            _uiState.update {
                it.copy(
                    packDiagnosticReport = report,
                    stickerIssues = report.issues.associateBy { issue -> issue.stickerId },
                    isDiagnosticDialogOpen = true
                )
            }
            return
        }

        _uiState.update { it.copy(exportEvent = WhatsAppExportEvent.LaunchIntent(pack)) }
    }

    fun syncWhitelist(context: Context, packIdentifier: String) {
        viewModelScope.launch {
            stickerPackRepository.syncWhitelistStatus(context, packIdentifier)
        }
    }

    fun markPackSynced(packIdentifier: String) {
        viewModelScope.launch {
            stickerPackRepository.markPackSynced(packIdentifier)
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

        val invalidPack = selectedPacks.find { it.stickers.size !in 3..30 }
        if (invalidPack != null) {
            _uiState.update {
                it.copy(userFeedbackMessage = "El paquete '${invalidPack.name}' tiene ${invalidPack.stickers.size} stickers. WhatsApp requiere entre 3 y 30 stickers por paquete.")
            }
            return
        }

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

    // --- Merge Packs ---
    fun onOpenMergeDialog() {
        val packs = (_uiState.value.loadState as? PacksLoadState.Success)?.packs ?: return
        val selectedPacks = packs.filter { it.identifier in _uiState.value.selectedPackIds }
        if (selectedPacks.size < 2) {
            _uiState.update { it.copy(userFeedbackMessage = "Selecciona al menos 2 paquetes para combinarlos.") }
            return
        }

        val isAnimated = selectedPacks.first().isAnimated
        if (selectedPacks.any { it.isAnimated != isAnimated }) {
            _uiState.update {
                it.copy(userFeedbackMessage = "No se pueden combinar paquetes animados y estáticos juntos.")
            }
            return
        }

        _uiState.update { it.copy(isMergeDialogOpen = true) }
    }

    fun onDismissMergeDialog() {
        _uiState.update { it.copy(isMergeDialogOpen = false) }
    }

    fun onConfirmMergePacks(baseName: String) {
        val trimmed = baseName.trim()
        if (trimmed.isBlank()) return
        val selectedIds = _uiState.value.selectedPackIds.toList()
        if (selectedIds.size < 2) return

        viewModelScope.launch {
            _uiState.update { it.copy(isMergingPacks = true) }
            val result = stickerPackRepository.mergePacks(selectedIds, trimmed)
            when (result) {
                is com.seventv.stickers.core.common.result.StickerResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isMergeDialogOpen = false,
                            isMergingPacks = false,
                            isSelectionMode = false,
                            selectedPackIds = emptySet(),
                            userFeedbackMessage = "Paquetes combinados con éxito (${result.data.size} pack(s) creados)"
                        )
                    }
                }
                is com.seventv.stickers.core.common.result.StickerResult.Failure -> {
                    val msg = when (val error = result.error) {
                        is com.seventv.stickers.core.common.result.StickerError.UnsupportedFormat ->
                            "No se pueden combinar paquetes animados y estáticos juntos."
                        is com.seventv.stickers.core.common.result.StickerError.InsufficientStickers ->
                            "Se necesitan al menos 3 stickers en total para crear un paquete de WhatsApp."
                        else -> "Error al combinar los paquetes"
                    }
                    _uiState.update {
                        it.copy(
                            isMergingPacks = false,
                            userFeedbackMessage = msg
                        )
                    }
                }
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

    // --- Duplicate Pack ---
    fun onOpenDuplicateDialog(pack: StickerPack) {
        _uiState.update { it.copy(isDuplicateDialogOpen = true, packToDuplicate = pack) }
    }

    fun onDismissDuplicateDialog() {
        _uiState.update { it.copy(isDuplicateDialogOpen = false, packToDuplicate = null) }
    }

    fun onConfirmDuplicatePack(newName: String) {
        val pack = _uiState.value.packToDuplicate ?: return
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        _uiState.update {
            it.copy(
                isDuplicateDialogOpen = false,
                packToDuplicate = null,
                userFeedbackMessage = "Pack duplicado como '$trimmed'"
            )
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

    fun onUpdateStickerEmojis(packIdentifier: String, stickerId: String, emojis: List<String>) {
        viewModelScope.launch {
            val result = stickerPackRepository.updateStickerEmojis(stickerId, emojis)
            if (result is com.seventv.stickers.core.common.result.StickerResult.Success) {
                onSelectPackDetail(packIdentifier)
            }
        }
    }

    fun onToggleReorderMode() {
        _uiState.update { it.copy(isReorderMode = !it.isReorderMode) }
    }

    fun onSetReorderMode(enabled: Boolean) {
        _uiState.update { it.copy(isReorderMode = enabled) }
    }

    fun onDragStarted() {
        _uiState.update { it.copy(isDraggingSticker = true) }
    }

    fun onMoveSticker(packIdentifier: String, fromIndex: Int, toIndex: Int) {
        _uiState.update { current ->
            val detail = current.selectedPackDetail
            if (detail == null || detail.identifier != packIdentifier) return@update current
            val stickers = detail.stickers
            if (fromIndex !in stickers.indices || toIndex !in stickers.indices || fromIndex == toIndex) {
                return@update current
            }
            val mutable = stickers.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(toIndex, moved)
            val reindexed = mutable.mapIndexed { idx, sticker -> sticker.copy(sortOrder = idx) }
            val updatedDetail = detail.copy(stickers = reindexed)
            val updatedLoadState = (current.loadState as? PacksLoadState.Success)?.let { success ->
                PacksLoadState.Success(
                    success.packs.map { if (it.identifier == packIdentifier) updatedDetail else it }
                )
            } ?: current.loadState

            current.copy(
                loadState = updatedLoadState,
                selectedPackDetail = updatedDetail
            )
        }
    }

    fun onPersistStickerOrder(
        packIdentifier: String,
        orderedStickerIds: List<String>? = null
    ) {
        val detail = _uiState.value.selectedPackDetail
        val idsToPersist = orderedStickerIds ?: detail?.takeIf { it.identifier == packIdentifier }?.stickers?.map { it.id }
        _uiState.update { it.copy(isDraggingSticker = false) }
        if (idsToPersist.isNullOrEmpty()) return

        viewModelScope.launch {
            when (val result = stickerPackRepository.reorderStickers(packIdentifier, idsToPersist)) {
                is com.seventv.stickers.core.common.result.StickerResult.Success -> {
                    val refreshed = stickerPackRepository.getPack(packIdentifier)
                    if (refreshed is com.seventv.stickers.core.common.result.StickerResult.Success) {
                        val report = StickerPackInspector.inspect(refreshed.data)
                        _uiState.update {
                            it.copy(
                                selectedPackDetail = refreshed.data,
                                packDiagnosticReport = report,
                                stickerIssues = report.issues.associateBy { issue -> issue.stickerId }
                            )
                        }
                    }
                }
                is com.seventv.stickers.core.common.result.StickerResult.Failure -> {
                    val refreshed = stickerPackRepository.getPack(packIdentifier)
                    if (refreshed is com.seventv.stickers.core.common.result.StickerResult.Success) {
                        _uiState.update {
                            it.copy(
                                selectedPackDetail = refreshed.data,
                                userFeedbackMessage = "Error al guardar el orden: ${result.error}"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(userFeedbackMessage = "Error al guardar el orden: ${result.error}")
                        }
                    }
                }
            }
        }
    }

    fun onDragCancelled(packIdentifier: String) {
        _uiState.update { it.copy(isDraggingSticker = false) }
        viewModelScope.launch {
            val refreshed = stickerPackRepository.getPack(packIdentifier)
            if (refreshed is com.seventv.stickers.core.common.result.StickerResult.Success) {
                val report = StickerPackInspector.inspect(refreshed.data)
                _uiState.update {
                    it.copy(
                        selectedPackDetail = refreshed.data,
                        packDiagnosticReport = report,
                        stickerIssues = report.issues.associateBy { issue -> issue.stickerId }
                    )
                }
            }
        }
    }

    fun onSetStickerAsTrayIcon(packIdentifier: String, stickerId: String) {
        _uiState.update { current ->
            val detail = current.selectedPackDetail
            if (detail != null && detail.identifier == packIdentifier) {
                val target = detail.stickers.firstOrNull { it.id == stickerId }
                if (target != null) {
                    val reordered = (listOf(target) + detail.stickers.filter { it.id != stickerId })
                        .mapIndexed { idx, s -> s.copy(sortOrder = idx) }
                    val updatedDetail = detail.copy(stickers = reordered)
                    val updatedLoadState = (current.loadState as? PacksLoadState.Success)?.let { success ->
                        PacksLoadState.Success(
                            success.packs.map { if (it.identifier == packIdentifier) updatedDetail else it }
                        )
                    } ?: current.loadState
                    current.copy(
                        loadState = updatedLoadState,
                        selectedPackDetail = updatedDetail
                    )
                } else current
            } else current
        }

        viewModelScope.launch {
            when (val result = stickerPackRepository.setStickerAsTrayIcon(packIdentifier, stickerId)) {
                is com.seventv.stickers.core.common.result.StickerResult.Success -> {
                    val refreshed = stickerPackRepository.getPack(packIdentifier)
                    if (refreshed is com.seventv.stickers.core.common.result.StickerResult.Success) {
                        val report = StickerPackInspector.inspect(refreshed.data)
                        _uiState.update {
                            it.copy(
                                selectedPackDetail = refreshed.data,
                                packDiagnosticReport = report,
                                stickerIssues = report.issues.associateBy { issue -> issue.stickerId },
                                userFeedbackMessage = "Portada del pack actualizada"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(userFeedbackMessage = "Portada del pack actualizada")
                        }
                    }
                }
                is com.seventv.stickers.core.common.result.StickerResult.Failure -> {
                    onSelectPackDetail(packIdentifier)
                    _uiState.update {
                        it.copy(userFeedbackMessage = "Error al actualizar portada: ${result.error}")
                    }
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
                _uiState.update { it.copy(searchResults = result.data.distinctBy { e -> e.id }, isSearching = false) }
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

    fun onCheckPackUpdates(pack: StickerPack) {
        val originSetId = pack.originSetId
        if (originSetId.isNullOrBlank()) {
            _uiState.update { it.copy(syncState = PackSyncState.Error("Este paquete no está vinculado a un conjunto de emotes de 7TV.")) }
            return
        }
        val availableSlots = 30 - pack.stickers.size
        if (availableSlots <= 0) {
            _uiState.update {
                it.copy(syncState = PackSyncState.Error("El pack ya contiene el límite máximo de 30 stickers permitido por WhatsApp."))
            }
            return
        }

        _uiState.update { it.copy(syncState = PackSyncState.Checking) }

        viewModelScope.launch {
            when (val result = stickerPackRepository.checkForPackUpdates(pack.identifier)) {
                is com.seventv.stickers.core.common.result.StickerResult.Success -> {
                    val newEmotes = result.data
                    if (newEmotes.isEmpty()) {
                        _uiState.update { it.copy(syncState = PackSyncState.UpToDate) }
                    } else {
                        val initialSelected = newEmotes.take(availableSlots).map { it.id }.toSet()
                        _uiState.update {
                            it.copy(
                                syncState = PackSyncState.NewEmotesAvailable(
                                    packIdentifier = pack.identifier,
                                    streamerName = pack.originStreamerName ?: pack.publisher,
                                    newEmotes = newEmotes,
                                    availableSlots = availableSlots
                                ),
                                selectedSyncEmoteIds = initialSelected
                            )
                        }
                    }
                }
                is com.seventv.stickers.core.common.result.StickerResult.Failure -> {
                    _uiState.update {
                        it.copy(syncState = PackSyncState.Error("Error al comprobar novedades: ${result.error}"))
                    }
                }
            }
        }
    }

    fun onToggleSyncEmoteSelection(emoteId: String, availableSlots: Int) {
        _uiState.update { state ->
            val current = state.selectedSyncEmoteIds.toMutableSet()
            if (current.contains(emoteId)) {
                current.remove(emoteId)
            } else {
                if (current.size < availableSlots) {
                    current.add(emoteId)
                }
            }
            state.copy(selectedSyncEmoteIds = current)
        }
    }

    fun onApplySyncUpdates() {
        val sync = _uiState.value.syncState as? PackSyncState.NewEmotesAvailable ?: return
        val selectedIds = _uiState.value.selectedSyncEmoteIds
        val emotesToAdd = sync.newEmotes.filter { selectedIds.contains(it.id) }
        if (emotesToAdd.isEmpty()) return

        _uiState.update { it.copy(isApplyingSync = true) }

        viewModelScope.launch {
            when (val result = stickerPackRepository.addEmotesToPack(sync.packIdentifier, emotesToAdd)) {
                is com.seventv.stickers.core.common.result.StickerResult.Success -> {
                    _uiState.update {
                        it.copy(
                            syncState = PackSyncState.Idle,
                            selectedSyncEmoteIds = emptySet(),
                            isApplyingSync = false,
                            userFeedbackMessage = "¡Se han añadido ${emotesToAdd.size} nuevos stickers al pack!"
                        )
                    }
                }
                is com.seventv.stickers.core.common.result.StickerResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isApplyingSync = false,
                            syncState = PackSyncState.Error("Error al añadir stickers: ${result.error}")
                        )
                    }
                }
            }
        }
    }

    fun onDismissSyncDialog() {
        _uiState.update {
            it.copy(
                syncState = PackSyncState.Idle,
                selectedSyncEmoteIds = emptySet()
            )
        }
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

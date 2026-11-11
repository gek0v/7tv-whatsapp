package com.seventv.stickers.ui.packs

import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.ui.components.ChatSimulatorMode

sealed interface PacksLoadState {
    data object Loading : PacksLoadState
    data class Success(val packs: List<StickerPack>) : PacksLoadState
    data class Error(val message: String) : PacksLoadState
}

sealed interface WhatsAppExportEvent {
    data class LaunchIntent(val pack: StickerPack) : WhatsAppExportEvent
    data object WhatsAppNotInstalled : WhatsAppExportEvent
    data class Error(val message: String) : WhatsAppExportEvent
}

sealed interface PackSyncState {
    data object Idle : PackSyncState
    data object Checking : PackSyncState
    data object UpToDate : PackSyncState
    data class NewEmotesAvailable(
        val packIdentifier: String,
        val streamerName: String,
        val newEmotes: List<SevenTvEmote>,
        val availableSlots: Int
    ) : PackSyncState
    data class Error(val message: String) : PackSyncState
}

data class PacksUiState(
    val loadState: PacksLoadState = PacksLoadState.Loading,
    val selectedPackDetail: StickerPack? = null,
    val chatSimulatorMode: ChatSimulatorMode = ChatSimulatorMode.WHATSAPP_DARK,
    val isDeleteConfirmDialogOpen: Boolean = false,
    val packToDelete: StickerPack? = null,
    val exportEvent: WhatsAppExportEvent? = null,
    // Multi-selection & Batch actions
    val isSelectionMode: Boolean = false,
    val selectedPackIds: Set<String> = emptySet(),
    val isBulkDeleteConfirmDialogOpen: Boolean = false,
    val isMergeDialogOpen: Boolean = false,
    val isMergingPacks: Boolean = false,
    val exportQueue: List<StickerPack> = emptyList(),
    // Rename & Duplicate
    val isRenameDialogOpen: Boolean = false,
    val packToRename: StickerPack? = null,
    val isDuplicateDialogOpen: Boolean = false,
    val packToDuplicate: StickerPack? = null,
    // Sticker Management in Detail
    val isReorderMode: Boolean = false,
    val isDraggingSticker: Boolean = false,
    val isDeleteStickerConfirmDialogOpen: Boolean = false,
    val stickerToDelete: PackSticker? = null,
    val isAddStickersDialogOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SevenTvEmote> = emptyList(),
    val isSearching: Boolean = false,
    val isAddingStickers: Boolean = false,
    val userFeedbackMessage: String? = null,
    // Diagnostics & WhatsApp Validation
    val stickerIssues: Map<String, com.seventv.stickers.core.domain.validator.StickerIssue> = emptyMap(),
    val isDiagnosticDialogOpen: Boolean = false,
    val packDiagnosticReport: com.seventv.stickers.core.domain.validator.StickerPackInspectionReport? = null,
    val isDeletingInvalidStickers: Boolean = false,
    // Channel Pack Synchronization
    val syncState: PackSyncState = PackSyncState.Idle,
    val selectedSyncEmoteIds: Set<String> = emptySet(),
    val isApplyingSync: Boolean = false
)

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
    val exportQueue: List<StickerPack> = emptyList(),
    // Rename
    val isRenameDialogOpen: Boolean = false,
    val packToRename: StickerPack? = null,
    // Sticker Management in Detail
    val isDeleteStickerConfirmDialogOpen: Boolean = false,
    val stickerToDelete: PackSticker? = null,
    val isAddStickersDialogOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SevenTvEmote> = emptyList(),
    val isSearching: Boolean = false,
    val isAddingStickers: Boolean = false,
    val userFeedbackMessage: String? = null
)

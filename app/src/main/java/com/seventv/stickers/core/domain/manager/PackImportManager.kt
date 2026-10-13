package com.seventv.stickers.core.domain.manager

import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.engine.SplitPlan
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.data.repository.StickerPackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PackImportProgress {
    data object Idle : PackImportProgress
    data class Running(
        val streamerName: String,
        val currentPack: Int,
        val totalPacks: Int,
        val currentSticker: Int,
        val totalStickers: Int
    ) : PackImportProgress
    data class Completed(
        val streamerName: String,
        val packsCount: Int,
        val createdPacks: List<StickerPack>
    ) : PackImportProgress
    data class Failed(
        val streamerName: String,
        val errorMessage: String
    ) : PackImportProgress
}

class PackImportManager(
    private val stickerPackRepository: StickerPackRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _progress = MutableStateFlow<PackImportProgress>(PackImportProgress.Idle)
    val progress: StateFlow<PackImportProgress> = _progress.asStateFlow()

    fun isImporting(): Boolean = _progress.value is PackImportProgress.Running

    fun startImport(streamerName: String, plans: List<SplitPlan>, originSetId: String? = null) {
        if (plans.isEmpty()) return
        if (isImporting()) return

        scope.launch {
            val allCreatedPacks = mutableListOf<StickerPack>()

            for ((index, plan) in plans.withIndex()) {
                _progress.value = PackImportProgress.Running(
                    streamerName = streamerName,
                    currentPack = index + 1,
                    totalPacks = plans.size,
                    currentSticker = 0,
                    totalStickers = plan.emotes.size
                )

                val result = stickerPackRepository.createPacksFromEmotes(
                    basePackName = plan.packName,
                    publisher = streamerName,
                    emotes = plan.emotes,
                    onProgress = { current, total ->
                        _progress.value = PackImportProgress.Running(
                            streamerName = streamerName,
                            currentPack = index + 1,
                            totalPacks = plans.size,
                            currentSticker = current,
                            totalStickers = total
                        )
                    },
                    originSetId = originSetId,
                    originStreamerName = streamerName
                )

                when (result) {
                    is StickerResult.Success -> {
                        allCreatedPacks.addAll(result.data)
                    }
                    is StickerResult.Failure -> {
                        _progress.value = PackImportProgress.Failed(
                            streamerName = streamerName,
                            errorMessage = "Error creando el pack '${plan.packName}': ${result.error}"
                        )
                        return@launch
                    }
                }
            }

            _progress.value = PackImportProgress.Completed(
                streamerName = streamerName,
                packsCount = allCreatedPacks.size,
                createdPacks = allCreatedPacks
            )
        }
    }

    fun dismissProgress() {
        _progress.value = PackImportProgress.Idle
    }
}

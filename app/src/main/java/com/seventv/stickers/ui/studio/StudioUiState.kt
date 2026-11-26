package com.seventv.stickers.ui.studio

import android.net.Uri
import com.seventv.stickers.core.media.sound.AudioTrackInfo
import com.seventv.stickers.core.media.sound.SoundStickerExportResult
import com.seventv.stickers.core.media.sound.SoundStickerSpecs
import com.seventv.stickers.core.media.sound.StudioCanvasBackground
import com.seventv.stickers.core.media.sound.StudioLoopMode
import com.seventv.stickers.core.media.sound.StudioScaleMode
import com.seventv.stickers.core.media.sound.VoiceRecordingState
import com.seventv.stickers.core.network.model.SevenTvEmote

/**
 * Visual source chosen by the user for a Sound Sticker.
 */
sealed interface StudioVisualSelection {
    data object None : StudioVisualSelection

    data class EmoteVisual(
        val emote: SevenTvEmote
    ) : StudioVisualSelection

    data class GalleryVisual(
        val uri: Uri,
        val displayName: String,
        val isAnimated: Boolean,
        val isVideo: Boolean = false
    ) : StudioVisualSelection

    data class LocalFileVisual(
        val filePath: String,
        val displayName: String,
        val isAnimated: Boolean
    ) : StudioVisualSelection
}

/**
 * Audio source and trim window configured by the user.
 */
sealed interface StudioAudioSelection {
    data object None : StudioAudioSelection

    data class Ready(
        val info: AudioTrackInfo,
        val trimStartMs: Long,
        val trimEndMs: Long,
        val isFromMicrophone: Boolean
    ) : StudioAudioSelection {
        val clipDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
    }
}

sealed interface StudioExportState {
    data object Idle : StudioExportState
    data class Processing(
        val progress: Float,
        val statusMessage: String
    ) : StudioExportState
    data class Success(
        val result: SoundStickerExportResult
    ) : StudioExportState
    data class Error(
        val message: String
    ) : StudioExportState
}

data class StudioUiState(
    val visual: StudioVisualSelection = StudioVisualSelection.None,
    val audio: StudioAudioSelection = StudioAudioSelection.None,
    val canvasBackground: StudioCanvasBackground = StudioCanvasBackground.WHATSAPP_DARK,
    val loopMode: StudioLoopMode = StudioLoopMode.LOOP,
    val scaleMode: StudioScaleMode = StudioScaleMode.CONTAIN,
    val recordingState: VoiceRecordingState = VoiceRecordingState(),
    val isAudioPreviewPlaying: Boolean = false,
    val isLoadingAudio: Boolean = false,
    val audioError: String? = null,
    val exportState: StudioExportState = StudioExportState.Idle,
    val isEmotePickerOpen: Boolean = false,
    val emoteSearchQuery: String = "",
    val emotePickerResults: List<SevenTvEmote> = emptyList(),
    val isSearchingEmotes: Boolean = false
) {
    val isVisualAnimated: Boolean
        get() = when (visual) {
            is StudioVisualSelection.None -> false
            is StudioVisualSelection.EmoteVisual -> visual.emote.isAnimated
            is StudioVisualSelection.GalleryVisual -> visual.isAnimated || visual.isVideo
            is StudioVisualSelection.LocalFileVisual -> visual.isAnimated
        }

    val visualDisplayName: String?
        get() = when (visual) {
            is StudioVisualSelection.None -> null
            is StudioVisualSelection.EmoteVisual -> visual.emote.name
            is StudioVisualSelection.GalleryVisual -> visual.displayName
            is StudioVisualSelection.LocalFileVisual -> visual.displayName
        }

    val maxClipDurationMs: Long
        get() = SoundStickerSpecs.maxDurationForVisual(isVisualAnimated)

    val canGenerate: Boolean
        get() = visual !is StudioVisualSelection.None &&
            audio is StudioAudioSelection.Ready &&
            !recordingState.isRecording &&
            exportState !is StudioExportState.Processing
}

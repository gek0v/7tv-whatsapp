package com.seventv.stickers.ui.studio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.sound.AudioInspector
import com.seventv.stickers.core.media.sound.SoundStickerExporter
import com.seventv.stickers.core.media.sound.SoundStickerSpecs
import com.seventv.stickers.core.media.sound.StudioCanvasBackground
import com.seventv.stickers.core.media.sound.StudioLoopMode
import com.seventv.stickers.core.media.sound.VoiceRecorderController
import com.seventv.stickers.core.network.downloader.EmoteDownloader
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.toSevenTvEmote
import com.seventv.stickers.core.network.model.toUnifiedEmote
import com.seventv.stickers.data.repository.AggregatedEmoteCatalogRepository
import com.seventv.stickers.data.repository.SevenTvRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StudioViewModel(
    private val context: Context,
    private val sevenTvRepository: SevenTvRepository,
    private val aggregatedRepository: AggregatedEmoteCatalogRepository? = null,
    private val emoteDownloader: EmoteDownloader? = null,
    private val audioInspector: AudioInspector,
    private val voiceRecorder: VoiceRecorderController,
    private val soundStickerExporter: SoundStickerExporter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var emoteSearchJob: Job? = null
    private var exportJob: Job? = null
    private var autoStoppedHandledFile: File? = null

    init {
        viewModelScope.launch {
            voiceRecorder.state.collect { recState ->
                val wasRecording = _uiState.value.recordingState.isRecording
                _uiState.update { it.copy(recordingState = recState) }

                // If MediaRecorder hit maxDuration auto-stop, import the recorded file automatically
                val recorded = recState.recordedFile
                if (wasRecording && !recState.isRecording && recorded != null && recorded != autoStoppedHandledFile) {
                    autoStoppedHandledFile = recorded
                    importAudioFromUri(Uri.fromFile(recorded), isFromMicrophone = true)
                }
            }
        }
    }

    fun applyVisualSelection(selection: StudioVisualSelection) {
        _uiState.update { current ->
            val next = current.copy(
                visual = selection,
                isEmotePickerOpen = false,
                exportState = StudioExportState.Idle
            )
            reclampAudioToVisual(next)
        }
    }

    fun onSelectEmoteVisual(emote: SevenTvEmote) {
        applyVisualSelection(StudioVisualSelection.EmoteVisual(emote))
    }

    fun onSelectLocalFileVisual(filePath: String, displayName: String, isAnimated: Boolean) {
        applyVisualSelection(
            StudioVisualSelection.LocalFileVisual(
                filePath = filePath,
                displayName = displayName,
                isAnimated = isAnimated
            )
        )
    }

    fun onSelectGalleryVisual(uri: Uri) {
        viewModelScope.launch {
            val (displayName, isAnimated) = withContext(ioDispatcher) {
                resolveVisualMetadata(uri)
            }
            applyVisualSelection(
                StudioVisualSelection.GalleryVisual(
                    uri = uri,
                    displayName = displayName,
                    isAnimated = isAnimated
                )
            )
        }
    }

    fun onClearVisual() {
        _uiState.update {
            it.copy(
                visual = StudioVisualSelection.None,
                exportState = StudioExportState.Idle
            )
        }
    }

    fun onSelectBackground(background: StudioCanvasBackground) {
        _uiState.update {
            it.copy(
                canvasBackground = background,
                exportState = StudioExportState.Idle
            )
        }
    }

    fun onSelectLoopMode(mode: StudioLoopMode) {
        _uiState.update {
            it.copy(
                loopMode = mode,
                exportState = StudioExportState.Idle
            )
        }
    }

    fun onImportAudioUri(uri: Uri) {
        importAudioFromUri(uri, isFromMicrophone = false)
    }

    private fun importAudioFromUri(uri: Uri, isFromMicrophone: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingAudio = true,
                    audioError = null,
                    isAudioPreviewPlaying = false,
                    exportState = StudioExportState.Idle
                )
            }
            when (val result = audioInspector.inspect(uri)) {
                is StickerResult.Success -> {
                    val info = result.data
                    _uiState.update { state ->
                        val maxDur = state.maxClipDurationMs
                        val (startMs, endMs) = SoundStickerSpecs.clampTrimRange(
                            startMs = 0L,
                            endMs = minOf(info.durationMs, maxDur),
                            totalDurationMs = info.durationMs,
                            maxClipDurationMs = maxDur
                        )
                        state.copy(
                            isLoadingAudio = false,
                            audioError = null,
                            audio = StudioAudioSelection.Ready(
                                info = info,
                                trimStartMs = startMs,
                                trimEndMs = endMs,
                                isFromMicrophone = isFromMicrophone
                            )
                        )
                    }
                }
                is StickerResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAudio = false,
                            audioError = "No se pudo leer este archivo de audio. Prueba con MP3, WAV, M4A, OGG o MP4."
                        )
                    }
                }
            }
        }
    }

    fun onStartVoiceRecording() {
        _uiState.update {
            it.copy(
                isAudioPreviewPlaying = false,
                audioError = null,
                exportState = StudioExportState.Idle
            )
        }
        autoStoppedHandledFile = null
        voiceRecorder.startRecording(maxDurationMs = _uiState.value.maxClipDurationMs)
    }

    fun onStopVoiceRecording() {
        val file = voiceRecorder.stopRecording()
        if (file != null) {
            autoStoppedHandledFile = file
            importAudioFromUri(Uri.fromFile(file), isFromMicrophone = true)
        }
    }

    fun onCancelVoiceRecording() {
        voiceRecorder.cancelRecording()
    }

    fun onTrimRangeChanged(startMs: Long, endMs: Long) {
        _uiState.update { state ->
            val ready = state.audio as? StudioAudioSelection.Ready ?: return@update state
            val (clampedStart, clampedEnd) = SoundStickerSpecs.clampTrimRange(
                startMs = startMs,
                endMs = endMs,
                totalDurationMs = ready.info.durationMs,
                maxClipDurationMs = state.maxClipDurationMs
            )
            state.copy(
                audio = ready.copy(trimStartMs = clampedStart, trimEndMs = clampedEnd),
                exportState = StudioExportState.Idle
            )
        }
    }

    fun onClearAudio() {
        _uiState.update {
            it.copy(
                audio = StudioAudioSelection.None,
                isAudioPreviewPlaying = false,
                audioError = null,
                exportState = StudioExportState.Idle
            )
        }
    }

    fun onSetAudioPreviewPlaying(playing: Boolean) {
        _uiState.update { it.copy(isAudioPreviewPlaying = playing) }
    }

    fun onDismissAudioError() {
        _uiState.update { it.copy(audioError = null) }
    }

    fun onOpenEmotePicker() {
        _uiState.update { it.copy(isEmotePickerOpen = true) }
        if (_uiState.value.emotePickerResults.isEmpty()) {
            loadPickerEmotes(_uiState.value.emoteSearchQuery)
        }
    }

    fun onDismissEmotePicker() {
        _uiState.update { it.copy(isEmotePickerOpen = false) }
    }

    fun onEmoteSearchQueryChanged(query: String) {
        _uiState.update { it.copy(emoteSearchQuery = query) }
        emoteSearchJob?.cancel()
        emoteSearchJob = viewModelScope.launch {
            delay(350L)
            loadPickerEmotes(query)
        }
    }

    private fun loadPickerEmotes(query: String) {
        emoteSearchJob?.cancel()
        emoteSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingEmotes = true) }
            val trimmed = query.trim()

            if (aggregatedRepository != null) {
                var latestList: List<SevenTvEmote> = emptyList()
                val flow = aggregatedRepository.searchAllProvidersProgressive(query = trimmed, page = 1, limit = 40)
                flow.collect { result ->
                    if (result is StickerResult.Success) {
                        val mapped = result.data.values.flatten().map { it.toSevenTvEmote() }
                            .distinctBy { it.provider to it.id }
                        if (mapped.isNotEmpty()) {
                            latestList = mapped
                            _uiState.update {
                                it.copy(
                                    emotePickerResults = latestList,
                                    isSearchingEmotes = false
                                )
                            }
                        }
                    }
                }
                _uiState.update { it.copy(isSearchingEmotes = false) }
            } else {
                val res = sevenTvRepository.searchEmotes(query = trimmed, page = 1, limit = 40)
                when (res) {
                    is StickerResult.Success -> {
                        _uiState.update {
                            it.copy(
                                emotePickerResults = res.data,
                                isSearchingEmotes = false
                            )
                        }
                    }
                    is StickerResult.Failure -> {
                        _uiState.update { it.copy(isSearchingEmotes = false) }
                    }
                }
            }
        }
    }

    fun onGenerateSoundSticker() {
        val state = _uiState.value
        val visual = state.visual
        val audio = state.audio as? StudioAudioSelection.Ready ?: return
        if (visual is StudioVisualSelection.None || state.exportState is StudioExportState.Processing) return

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAudioPreviewPlaying = false,
                    exportState = StudioExportState.Processing(0.02f, "Descargando recurso visual...")
                )
            }

            try {
                val visualBytes = withContext(ioDispatcher) {
                    loadVisualBytes(visual)
                }
                if (visualBytes == null || visualBytes.isEmpty()) {
                    _uiState.update {
                        it.copy(exportState = StudioExportState.Error("No se pudo cargar la imagen o emote seleccionado."))
                    }
                    return@launch
                }

                val result = soundStickerExporter.exportMp4(
                    visualBytes = visualBytes,
                    audioUri = audio.info.uri,
                    trimStartMs = audio.trimStartMs,
                    trimEndMs = audio.trimEndMs,
                    background = state.canvasBackground,
                    loopMode = state.loopMode,
                    onProgress = { progress, msg ->
                        _uiState.update {
                            it.copy(exportState = StudioExportState.Processing(progress, msg))
                        }
                    }
                )

                when (result) {
                    is StickerResult.Success -> {
                        _uiState.update {
                            it.copy(exportState = StudioExportState.Success(result.data))
                        }
                    }
                    is StickerResult.Failure -> {
                        val msg = when (val err = result.error) {
                            is StickerError.NativeEncodingFailed -> err.details
                            is StickerError.CorruptedSourceData -> "Los datos de audio o imagen están dañados."
                            else -> "Error al generar el MP4 de Sound Sticker."
                        }
                        _uiState.update {
                            it.copy(exportState = StudioExportState.Error(msg))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(exportState = StudioExportState.Error("Error inesperado: ${e.message ?: "desconocido"}"))
                }
            }
        }
    }

    fun onResetExportState() {
        _uiState.update { it.copy(exportState = StudioExportState.Idle) }
    }

    private fun reclampAudioToVisual(state: StudioUiState): StudioUiState {
        val ready = state.audio as? StudioAudioSelection.Ready ?: return state
        val (clampedStart, clampedEnd) = SoundStickerSpecs.clampTrimRange(
            startMs = ready.trimStartMs,
            endMs = ready.trimEndMs,
            totalDurationMs = ready.info.durationMs,
            maxClipDurationMs = state.maxClipDurationMs
        )
        return state.copy(
            audio = ready.copy(trimStartMs = clampedStart, trimEndMs = clampedEnd)
        )
    }

    private suspend fun loadVisualBytes(visual: StudioVisualSelection): ByteArray? {
        return when (visual) {
            is StudioVisualSelection.None -> null
            is StudioVisualSelection.EmoteVisual -> {
                val emote = visual.emote
                val res = if (emote.provider != EmoteProvider.SEVENTV && emoteDownloader != null) {
                    emoteDownloader.downloadEmote(emote.toUnifiedEmote())
                } else {
                    sevenTvRepository.downloadEmote(emote)
                }
                (res as? StickerResult.Success)?.data?.bytes
            }
            is StudioVisualSelection.GalleryVisual -> {
                context.contentResolver.openInputStream(visual.uri)?.use { it.readBytes() }
            }
            is StudioVisualSelection.LocalFileVisual -> {
                val candidate = File(visual.filePath)
                val file = if (candidate.isAbsolute && candidate.exists()) candidate else File(context.filesDir, visual.filePath)
                if (file.exists()) file.readBytes() else null
            }
        }
    }

    private fun resolveVisualMetadata(uri: Uri): Pair<String, Boolean> {
        val name = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        } ?: (uri.lastPathSegment ?: "Imagen")

        val mime = context.contentResolver.getType(uri) ?: ""
        val isGifOrWebpAnim = mime.equals("image/gif", ignoreCase = true) ||
            name.endsWith(".gif", ignoreCase = true) ||
            detectAnimatedBytes(uri)
        return name to isGifOrWebpAnim
    }

    private fun detectAnimatedBytes(uri: Uri): Boolean {
        return try {
            val head = ByteArray(128)
            val read = context.contentResolver.openInputStream(uri)?.use { it.read(head) } ?: 0
            if (read < 12) return false
            // GIF87a / GIF89a
            if (head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte()) {
                return true
            }
            // WebP VP8X ANIM chunk signature check ("ANIM")
            if (head[0] == 'R'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte() && head[3] == 'F'.code.toByte()) {
                for (i in 12 until (read - 4)) {
                    if (head[i] == 'A'.code.toByte() &&
                        head[i + 1] == 'N'.code.toByte() &&
                        head[i + 2] == 'I'.code.toByte() &&
                        head[i + 3] == 'M'.code.toByte()
                    ) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.cancelRecording()
    }

    class Factory(
        private val context: Context,
        private val sevenTvRepository: SevenTvRepository,
        private val aggregatedRepository: AggregatedEmoteCatalogRepository? = null,
        private val emoteDownloader: EmoteDownloader? = null,
        private val audioInspector: AudioInspector,
        private val voiceRecorder: VoiceRecorderController,
        private val soundStickerExporter: SoundStickerExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(
                context = context.applicationContext,
                sevenTvRepository = sevenTvRepository,
                aggregatedRepository = aggregatedRepository,
                emoteDownloader = emoteDownloader,
                audioInspector = audioInspector,
                voiceRecorder = voiceRecorder,
                soundStickerExporter = soundStickerExporter
            ) as T
        }
    }
}

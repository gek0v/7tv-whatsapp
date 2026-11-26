package com.seventv.stickers.ui.studio

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.model.AnimationFrame
import com.seventv.stickers.core.media.sound.AudioInspector
import com.seventv.stickers.core.media.sound.AudioTrackInfo
import com.seventv.stickers.core.media.sound.SoundStickerExportResult
import com.seventv.stickers.core.media.sound.SoundStickerExporter
import com.seventv.stickers.core.media.sound.SoundStickerSpecs
import com.seventv.stickers.core.media.sound.StudioCanvasBackground
import com.seventv.stickers.core.media.sound.StudioLoopMode
import com.seventv.stickers.core.media.sound.StudioScaleMode
import com.seventv.stickers.core.media.sound.VoiceRecorderController
import com.seventv.stickers.core.media.sound.VoiceRecordingState
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.EmoteProvider
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import com.seventv.stickers.data.repository.SevenTvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StudioViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeSevenTvRepo: FakeStudioSevenTvRepository
    private lateinit var fakeAudioInspector: FakeAudioInspector
    private lateinit var fakeVoiceRecorder: FakeVoiceRecorderController
    private lateinit var fakeExporter: FakeSoundStickerExporter
    private lateinit var viewModel: StudioViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        fakeSevenTvRepo = FakeStudioSevenTvRepository()
        fakeAudioInspector = FakeAudioInspector()
        fakeVoiceRecorder = FakeVoiceRecorderController()
        fakeExporter = FakeSoundStickerExporter()

        viewModel = StudioViewModel(
            context = context,
            sevenTvRepository = fakeSevenTvRepo,
            audioInspector = fakeAudioInspector,
            voiceRecorder = fakeVoiceRecorder,
            soundStickerExporter = fakeExporter,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun animatedVisualClampsLongAudioTrimWindowToFiveSeconds() = runTest(testDispatcher) {
        val staticEmote = sampleEmote(id = "e1", isAnimated = false)
        val animatedEmote = sampleEmote(id = "e2", isAnimated = true)
        fakeAudioInspector.nextDurationMs = 15_000L

        viewModel.onSelectEmoteVisual(staticEmote)
        viewModel.onImportAudioUri(Uri.parse("content://audio/sample.mp3"))
        advanceUntilIdle()

        val staticAudio = viewModel.uiState.value.audio as StudioAudioSelection.Ready
        assertEquals(0L, staticAudio.trimStartMs)
        assertEquals(10_000L, staticAudio.trimEndMs)

        // Switching to animated visual should automatically re-clamp trim window to 5s
        viewModel.onSelectEmoteVisual(animatedEmote)
        val animatedAudio = viewModel.uiState.value.audio as StudioAudioSelection.Ready
        assertEquals(0L, animatedAudio.trimStartMs)
        assertEquals(5_000L, animatedAudio.trimEndMs)
        assertTrue(viewModel.uiState.value.canGenerate)
    }

    @Test
    fun generateSoundStickerExportsMp4WithSelectedBackgroundAndTrimRange() = runTest(testDispatcher) {
        val emote = sampleEmote(id = "peepoJam", isAnimated = true)
        fakeAudioInspector.nextDurationMs = 8_000L

        viewModel.onSelectEmoteVisual(emote)
        viewModel.onSelectBackground(StudioCanvasBackground.NIGHT_PURPLE)
        viewModel.onImportAudioUri(Uri.parse("content://audio/jam.mp3"))
        advanceUntilIdle()

        viewModel.onTrimRangeChanged(1_500L, 4_500L)
        viewModel.onGenerateSoundSticker()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.exportState is StudioExportState.Success)
        assertEquals(1_500L, fakeExporter.lastTrimStartMs)
        assertEquals(4_500L, fakeExporter.lastTrimEndMs)
        assertEquals(StudioCanvasBackground.NIGHT_PURPLE, fakeExporter.lastBackground)
        assertEquals(StudioLoopMode.LOOP, fakeExporter.lastLoopMode)
        assertEquals(StudioScaleMode.CONTAIN, fakeExporter.lastScaleMode)
    }

    @Test
    fun selectLoopModeUpdatesStateAndPassesChoiceToExporter() = runTest(testDispatcher) {
        val emote = sampleEmote(id = "catJAM", isAnimated = true)
        fakeAudioInspector.nextDurationMs = 4_000L

        assertEquals(StudioLoopMode.LOOP, viewModel.uiState.value.loopMode)

        viewModel.onSelectEmoteVisual(emote)
        viewModel.onImportAudioUri(Uri.parse("content://audio/catjam.mp3"))
        advanceUntilIdle()

        viewModel.onSelectLoopMode(StudioLoopMode.HOLD_LAST_FRAME)
        assertEquals(StudioLoopMode.HOLD_LAST_FRAME, viewModel.uiState.value.loopMode)

        viewModel.onGenerateSoundSticker()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.exportState is StudioExportState.Success)
        assertEquals(StudioLoopMode.HOLD_LAST_FRAME, fakeExporter.lastLoopMode)

        // Changing loop mode after export resets exportState back to Idle
        viewModel.onSelectLoopMode(StudioLoopMode.LOOP)
        assertEquals(StudioLoopMode.LOOP, viewModel.uiState.value.loopMode)
        assertTrue(viewModel.uiState.value.exportState is StudioExportState.Idle)
    }

    @Test
    fun selectScaleModeUpdatesStateResetsExportAndForwardsToExporter() = runTest(testDispatcher) {
        val emote = sampleEmote(id = "catJam", isAnimated = false)
        fakeAudioInspector.nextDurationMs = 5_000L

        assertEquals(StudioScaleMode.CONTAIN, viewModel.uiState.value.scaleMode)

        viewModel.onSelectEmoteVisual(emote)
        viewModel.onImportAudioUri(Uri.parse("content://audio/cat.mp3"))
        advanceUntilIdle()

        viewModel.onGenerateSoundSticker()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.exportState is StudioExportState.Success)

        viewModel.onSelectScaleMode(StudioScaleMode.CROP)
        assertEquals(StudioScaleMode.CROP, viewModel.uiState.value.scaleMode)
        assertEquals(StudioExportState.Idle, viewModel.uiState.value.exportState)

        viewModel.onGenerateSoundSticker()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.exportState is StudioExportState.Success)
        assertEquals(StudioScaleMode.CROP, fakeExporter.lastScaleMode)
    }

    @Test
    fun audioInspectionFailureSetsErrorMessageAndDisablesGenerate() = runTest(testDispatcher) {
        val emote = sampleEmote(id = "sadge", isAnimated = false)
        viewModel.onSelectEmoteVisual(emote)
        fakeAudioInspector.shouldFail = true

        viewModel.onImportAudioUri(Uri.parse("content://audio/corrupt.bin"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.audio is StudioAudioSelection.None)
        assertFalse(state.canGenerate)
        assertTrue(state.audioError != null)
    }

    @Test
    fun selectingGalleryVideoSetsAnimatedFiveSecondLimitAndAutoImportsAudioTrack() = runTest(testDispatcher) {
        Robolectric.setupContentProvider(FakeGalleryContentProvider::class.java, FakeGalleryContentProvider.AUTHORITY)
        val videoUri = Uri.parse("content://${FakeGalleryContentProvider.AUTHORITY}/clip_with_audio.mp4")
        fakeAudioInspector.nextDurationMs = 12_000L

        viewModel.onSelectGalleryVisual(videoUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val visual = state.visual as StudioVisualSelection.GalleryVisual
        assertTrue(visual.isVideo)
        assertTrue(visual.isAnimated)
        assertEquals("clip_with_audio.mp4", visual.displayName)
        assertTrue(state.isVisualAnimated)
        assertEquals(SoundStickerSpecs.MAX_ANIMATED_CLIP_DURATION_MS, state.maxClipDurationMs)

        // Video audio track is auto-imported and clamped to 5s animated ceiling
        val audio = state.audio as StudioAudioSelection.Ready
        assertEquals(videoUri, audio.info.uri)
        assertEquals(0L, audio.trimStartMs)
        assertEquals(5_000L, audio.trimEndMs)
        assertFalse(audio.isFromMicrophone)
        assertNull(state.audioError)
        assertTrue(state.canGenerate)

        // Exporting passes videoUri directly instead of buffering video bytes in RAM
        viewModel.onGenerateSoundSticker()
        advanceUntilIdle()
        assertEquals(videoUri, fakeExporter.lastVideoUri)
        assertEquals(0, fakeExporter.lastVisualBytes.size)
        assertTrue(viewModel.uiState.value.exportState is StudioExportState.Success)
    }

    @Test
    fun selectingSilentGalleryVideoDoesNotOverwriteExistingAudioOrShowAudioError() = runTest(testDispatcher) {
        Robolectric.setupContentProvider(FakeGalleryContentProvider::class.java, FakeGalleryContentProvider.AUTHORITY)
        val silentWebmUri = Uri.parse("content://${FakeGalleryContentProvider.AUTHORITY}/silent_loop.webm")

        // Case 1: silent video with no prior audio -> remains ready for user to add audio, no error banner
        fakeAudioInspector.shouldFail = true
        viewModel.onSelectGalleryVisual(silentWebmUri)
        advanceUntilIdle()

        val afterSilent = viewModel.uiState.value
        assertTrue((afterSilent.visual as StudioVisualSelection.GalleryVisual).isVideo)
        assertTrue(afterSilent.audio is StudioAudioSelection.None)
        assertNull(afterSilent.audioError)
        assertEquals(5_000L, afterSilent.maxClipDurationMs)

        // Case 2: user imports custom audio first, then picks a video with audio -> keeps user's audio clamped to 5s
        fakeAudioInspector.shouldFail = false
        fakeAudioInspector.nextDurationMs = 9_000L
        val customAudioUri = Uri.parse("content://audio/voice_meme.mp3")
        viewModel.onImportAudioUri(customAudioUri)
        advanceUntilIdle()

        val videoWithAudioUri = Uri.parse("content://${FakeGalleryContentProvider.AUTHORITY}/dance.mp4")
        viewModel.onSelectGalleryVisual(videoWithAudioUri)
        advanceUntilIdle()

        val preserved = viewModel.uiState.value.audio as StudioAudioSelection.Ready
        assertEquals(customAudioUri, preserved.info.uri)
        assertEquals(0L, preserved.trimStartMs)
        assertEquals(5_000L, preserved.trimEndMs)
    }

    @Test
    fun selectingStaticGalleryImageKeepsTenSecondLimitAndDoesNotAutoImportAudio() = runTest(testDispatcher) {
        Robolectric.setupContentProvider(FakeGalleryContentProvider::class.java, FakeGalleryContentProvider.AUTHORITY)
        val imageUri = Uri.parse("content://${FakeGalleryContentProvider.AUTHORITY}/photo.png")

        viewModel.onSelectGalleryVisual(imageUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val visual = state.visual as StudioVisualSelection.GalleryVisual
        assertFalse(visual.isVideo)
        assertFalse(visual.isAnimated)
        assertFalse(state.isVisualAnimated)
        assertEquals(SoundStickerSpecs.MAX_STATIC_CLIP_DURATION_MS, state.maxClipDurationMs)
        assertTrue(state.audio is StudioAudioSelection.None)
    }

    private fun sampleEmote(id: String, isAnimated: Boolean) = SevenTvEmote(
        id = id,
        name = id,
        isAnimated = isAnimated,
        isZeroWidth = false,
        isNsfw = false,
        bestAssetUrl = "https://cdn.7tv.app/emote/$id/4x.webp",
        availableScales = listOf("4x"),
        provider = EmoteProvider.SEVENTV
    )

    private class FakeAudioInspector : AudioInspector {
        var nextDurationMs: Long = 8_000L
        var shouldFail: Boolean = false

        override suspend fun inspect(uri: Uri): StickerResult<AudioTrackInfo> {
            if (shouldFail) return StickerResult.Failure(StickerError.CorruptedSourceData)
            return StickerResult.Success(
                AudioTrackInfo(
                    uri = uri,
                    displayName = "sample.mp3",
                    durationMs = nextDurationMs,
                    sampleRate = 44100,
                    channelCount = 2,
                    mimeType = "audio/mpeg",
                    waveformBars = List(40) { 0.5f }
                )
            )
        }
    }

    private class FakeVoiceRecorderController : VoiceRecorderController {
        private val _state = MutableStateFlow(VoiceRecordingState())
        override val state: StateFlow<VoiceRecordingState> = _state

        override fun startRecording(maxDurationMs: Long) {
            _state.value = VoiceRecordingState(isRecording = true)
        }

        override fun stopRecording(): File? {
            _state.value = VoiceRecordingState(isRecording = false)
            return null
        }

        override fun cancelRecording() {
            _state.value = VoiceRecordingState(isRecording = false)
        }

        override fun release() = Unit
    }

    private class FakeSoundStickerExporter : SoundStickerExporter {
        var lastTrimStartMs: Long = -1L
        var lastTrimEndMs: Long = -1L
        var lastBackground: StudioCanvasBackground? = null
        var lastLoopMode: StudioLoopMode? = null
        var lastScaleMode: StudioScaleMode? = null
        var lastVideoUri: Uri? = null
        var lastVisualBytes: ByteArray = ByteArray(0)

        override suspend fun exportMp4(
            visualBytes: ByteArray,
            audioUri: Uri,
            trimStartMs: Long,
            trimEndMs: Long,
            background: StudioCanvasBackground,
            loopMode: StudioLoopMode,
            scaleMode: StudioScaleMode,
            onProgress: (Float, String) -> Unit
        ): StickerResult<SoundStickerExportResult> = exportMp4(
            visualBytes = visualBytes,
            audioUri = audioUri,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            background = background,
            loopMode = loopMode,
            scaleMode = scaleMode,
            videoUri = null,
            preExtractedFrames = null,
            onProgress = onProgress
        )

        override suspend fun exportMp4(
            visualBytes: ByteArray,
            audioUri: Uri,
            trimStartMs: Long,
            trimEndMs: Long,
            background: StudioCanvasBackground,
            loopMode: StudioLoopMode,
            scaleMode: StudioScaleMode,
            videoUri: Uri?,
            preExtractedFrames: List<AnimationFrame>?,
            onProgress: (Float, String) -> Unit
        ): StickerResult<SoundStickerExportResult> {
            lastVisualBytes = visualBytes
            lastVideoUri = videoUri
            lastTrimStartMs = trimStartMs
            lastTrimEndMs = trimEndMs
            lastBackground = background
            lastLoopMode = loopMode
            lastScaleMode = scaleMode
            onProgress(1f, "¡Listo!")
            return StickerResult.Success(
                SoundStickerExportResult(
                    galleryUri = Uri.parse("content://media/external/video/media/99"),
                    shareFilePath = "/tmp/7tv_sound_sticker.mp4",
                    fileSizeBytes = 250_000L,
                    durationMs = trimEndMs - trimStartMs,
                    savedToGallery = true
                )
            )
        }
    }

    class FakeGalleryContentProvider : ContentProvider() {
        companion object {
            const val AUTHORITY = "com.seventv.stickers.test.gallery"
        }

        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? {
            val name = uri.lastPathSegment ?: return null
            return when {
                name.endsWith(".mp4") -> "video/mp4"
                name.endsWith(".webm") -> "video/webm"
                name.endsWith(".png") -> "image/png"
                else -> "application/octet-stream"
            }
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val cols = arrayOf(OpenableColumns.DISPLAY_NAME)
            val cursor = MatrixCursor(cols)
            cursor.addRow(arrayOf(uri.lastPathSegment ?: "media"))
            return cursor
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private class FakeStudioSevenTvRepository : SevenTvRepository {
        override suspend fun searchEmotes(query: String, page: Int, limit: Int, category: String?) =
            StickerResult.Success(emptyList<SevenTvEmote>())

        override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> =
            StickerResult.Failure(StickerError.ResourceNotFound)

        override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> =
            StickerResult.Failure(StickerError.ResourceNotFound)

        override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> {
            return StickerResult.Success(
                DownloadedEmoteAsset(
                    emoteId = emote.id,
                    emoteName = emote.name,
                    bytes = byteArrayOf(0x52, 0x49, 0x46, 0x46),
                    isAnimated = emote.isAnimated,
                    format = "WEBP"
                )
            )
        }

        override suspend fun downloadEmoteGif(emote: SevenTvEmote) =
            StickerResult.Failure(StickerError.ResourceNotFound)
    }
}

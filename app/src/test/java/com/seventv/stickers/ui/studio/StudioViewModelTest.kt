package com.seventv.stickers.ui.studio

import android.content.Context
import android.net.Uri
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.media.sound.AudioInspector
import com.seventv.stickers.core.media.sound.AudioTrackInfo
import com.seventv.stickers.core.media.sound.SoundStickerExportResult
import com.seventv.stickers.core.media.sound.SoundStickerExporter
import com.seventv.stickers.core.media.sound.StudioCanvasBackground
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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

        override suspend fun exportMp4(
            visualBytes: ByteArray,
            audioUri: Uri,
            trimStartMs: Long,
            trimEndMs: Long,
            background: StudioCanvasBackground,
            onProgress: (Float, String) -> Unit
        ): StickerResult<SoundStickerExportResult> {
            lastTrimStartMs = trimStartMs
            lastTrimEndMs = trimEndMs
            lastBackground = background
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

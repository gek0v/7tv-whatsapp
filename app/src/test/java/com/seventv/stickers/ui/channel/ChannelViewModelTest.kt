package com.seventv.stickers.ui.channel

import android.content.Context
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.engine.PackSplittingEngine
import com.seventv.stickers.core.domain.engine.SplitPlan
import com.seventv.stickers.core.domain.manager.PackImportManager
import com.seventv.stickers.core.domain.manager.PackImportProgress
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import com.seventv.stickers.data.repository.SevenTvRepository
import com.seventv.stickers.data.repository.StickerPackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSevenTvRepository: FakeChannelSevenTvRepository
    private lateinit var fakeStickerPackRepository: FakeChannelStickerPackRepository
    private lateinit var viewModel: ChannelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSevenTvRepository = FakeChannelSevenTvRepository()
        fakeStickerPackRepository = FakeChannelStickerPackRepository()
        viewModel = ChannelViewModel(
            sevenTvRepository = fakeSevenTvRepository,
            stickerPackRepository = fakeStickerPackRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialUiStateIsIdle() {
        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertEquals(ChannelFilter.ALL, state.activeFilter)
        assertTrue(state.loadState is ChannelLoadState.Idle)
        assertTrue(state.importState is ChannelImportState.Idle)
        assertTrue(state.selectedEmoteIds.isEmpty())
    }

    @Test
    fun searchStreamerDirectSetSuccess() = runTest(testDispatcher) {
        val emotes = (1..5).map { createSampleEmote("id_$it", "emote_$it", isAnimated = it % 2 == 0) }
        val sampleSet = SevenTvEmoteSet("set_1", "Ibai Set", "ibai", emotes)
        fakeSevenTvRepository.emoteSetHandler = { StickerResult.Success(sampleSet) }

        viewModel.onSearchStreamer("ibai")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success, got ${state.loadState}", state.loadState is ChannelLoadState.Success)
        val success = state.loadState as ChannelLoadState.Success
        assertEquals("Ibai Set", success.streamerName)
        assertEquals(5, success.allEmotes.size)
    }

    @Test
    fun searchStreamerError() = runTest(testDispatcher) {
        fakeSevenTvRepository.emoteSetHandler = { StickerResult.Failure(StickerError.ResourceNotFound) }
        fakeSevenTvRepository.searchEmotesHandler = { _, _, _ ->
            StickerResult.Failure(StickerError.NetworkError(500, "Network down"))
        }

        viewModel.onSearchStreamer("desconocido")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error, got ${state.loadState}", state.loadState is ChannelLoadState.Error)
    }

    @Test
    fun filterTogglesAnimatedAndStatic() = runTest(testDispatcher) {
        val emotes = listOf(
            createSampleEmote("1", "anim1", isAnimated = true),
            createSampleEmote("2", "anim2", isAnimated = true),
            createSampleEmote("3", "static1", isAnimated = false)
        )
        fakeSevenTvRepository.emoteSetHandler = {
            StickerResult.Success(SevenTvEmoteSet("s1", "Channel", "channel", emotes))
        }

        viewModel.onSearchStreamer("channel")
        advanceUntilIdle()

        viewModel.onFilterSelected(ChannelFilter.ANIMATED)
        val animated = viewModel.getFilteredEmotes()
        assertEquals(2, animated.size)
        assertTrue(animated.all { it.isAnimated })

        viewModel.onFilterSelected(ChannelFilter.STATIC)
        val staticEmotes = viewModel.getFilteredEmotes()
        assertEquals(1, staticEmotes.size)
        assertFalse(staticEmotes.first().isAnimated)
    }

    @Test
    fun toggleSelectEmotesAndSelectAll() = runTest(testDispatcher) {
        val emotes = (1..4).map { createSampleEmote("e_$it", "name_$it") }
        fakeSevenTvRepository.emoteSetHandler = {
            StickerResult.Success(SevenTvEmoteSet("s1", "Channel", "channel", emotes))
        }

        viewModel.onSearchStreamer("channel")
        advanceUntilIdle()

        viewModel.onToggleSelectEmote("e_1")
        assertTrue(viewModel.uiState.value.selectedEmoteIds.contains("e_1"))

        viewModel.onToggleSelectEmote("e_1")
        assertFalse(viewModel.uiState.value.selectedEmoteIds.contains("e_1"))

        viewModel.onSelectAllEmotes()
        assertEquals(4, viewModel.uiState.value.selectedEmoteIds.size)

        viewModel.onDeselectAllEmotes()
        assertTrue(viewModel.uiState.value.selectedEmoteIds.isEmpty())
    }

    @Test
    fun importWithLessThan3EmotesSetsError() = runTest(testDispatcher) {
        val emotes = listOf(
            createSampleEmote("1", "one"),
            createSampleEmote("2", "two")
        )
        fakeSevenTvRepository.emoteSetHandler = {
            StickerResult.Success(SevenTvEmoteSet("s1", "Channel", "channel", emotes))
        }

        viewModel.onSearchStreamer("channel")
        advanceUntilIdle()

        viewModel.onStartChannelImport(useOnlySelected = false)
        assertTrue(viewModel.uiState.value.importState is ChannelImportState.Error)
    }

    @Test
    fun importChannelWith35EmotesTriggersSplittingEngine() = runTest(testDispatcher) {
        // 35 animated emotes: must be split into 2 packs (18 and 17)
        val emotes = (1..35).map { createSampleEmote("id_$it", "emote_$it", isAnimated = true) }
        fakeSevenTvRepository.emoteSetHandler = {
            StickerResult.Success(SevenTvEmoteSet("s1", "Auron", "auron", emotes))
        }

        viewModel.onSearchStreamer("auron")
        advanceUntilIdle()

        viewModel.onStartChannelImport()

        val importState = viewModel.uiState.value.importState
        assertTrue("Expected Confirming, got $importState", importState is ChannelImportState.Confirming)
        val confirming = importState as ChannelImportState.Confirming
        assertEquals(2, confirming.splitPlans.size)
        assertEquals(18, confirming.splitPlans[0].emotes.size)
        assertEquals(17, confirming.splitPlans[1].emotes.size)

        // Confirm import
        viewModel.onConfirmImport(confirming.splitPlans)
        advanceUntilIdle()

        val finalState = viewModel.uiState.value.importState
        assertTrue("Expected Success, got $finalState", finalState is ChannelImportState.Success)
        assertEquals(2, (finalState as ChannelImportState.Success).createdPacks.size)
    }

    @Test
    fun importChannelWithPackImportManagerDelegatesToManagerAndDismissesDialogImmediately() = runTest(testDispatcher) {
        val emotes = (1..5).map { createSampleEmote("id_$it", "emote_$it") }
        fakeSevenTvRepository.emoteSetHandler = {
            StickerResult.Success(SevenTvEmoteSet("s1", "Streamer", "streamer", emotes))
        }

        val packImportManager = PackImportManager(
            stickerPackRepository = fakeStickerPackRepository,
            scope = this
        )
        val vm = ChannelViewModel(
            sevenTvRepository = fakeSevenTvRepository,
            stickerPackRepository = fakeStickerPackRepository,
            packImportManager = packImportManager
        )

        vm.onSearchStreamer("streamer")
        advanceUntilIdle()

        vm.onStartChannelImport()
        val confirming = vm.uiState.value.importState as ChannelImportState.Confirming

        vm.onConfirmImport(confirming.splitPlans)

        // Dialog should be dismissed immediately (importState is Idle)
        assertTrue(vm.uiState.value.importState is ChannelImportState.Idle)
        assertTrue(vm.uiState.value.selectedEmoteIds.isEmpty())

        advanceUntilIdle()

        // PackImportManager should have completed
        val progress = packImportManager.progress.value
        assertTrue("Expected Completed, got $progress", progress is PackImportProgress.Completed)
        assertEquals(1, (progress as PackImportProgress.Completed).packsCount)
    }

    private fun createSampleEmote(
        id: String,
        name: String,
        isAnimated: Boolean = false
    ): SevenTvEmote = SevenTvEmote(
        id = id,
        name = name,
        isAnimated = isAnimated,
        isZeroWidth = false,
        isNsfw = false,
        bestAssetUrl = "https://cdn.7tv.app/emote/$id/4x.webp",
        availableScales = listOf("1x", "2x", "3x", "4x")
    )

    private class FakeChannelSevenTvRepository : SevenTvRepository {
        var streamerEmoteSetHandler: (suspend (String) -> StickerResult<SevenTvEmoteSet>)? = null
        var emoteSetHandler: suspend (String) -> StickerResult<SevenTvEmoteSet> = {
            StickerResult.Failure(StickerError.ResourceNotFound)
        }
        var searchEmotesHandler: suspend (String, Int, Int) -> StickerResult<List<SevenTvEmote>> = { _, _, _ ->
            StickerResult.Success(emptyList())
        }

        override suspend fun getStreamerEmoteSet(query: String): StickerResult<SevenTvEmoteSet> {
            return streamerEmoteSetHandler?.invoke(query) ?: super.getStreamerEmoteSet(query)
        }

        override suspend fun searchEmotes(query: String, page: Int, limit: Int): StickerResult<List<SevenTvEmote>> {
            return searchEmotesHandler(query, page, limit)
        }

        override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }

        override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> {
            return emoteSetHandler(setId)
        }

        override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> {
            return StickerResult.Failure(StickerError.ResourceNotFound)
        }
    }

    private class FakeChannelStickerPackRepository : StickerPackRepository {
        override fun observeAllPacks(): Flow<List<StickerPack>> = flowOf(emptyList())

        override suspend fun getPack(identifier: String): StickerResult<StickerPack> {
            return StickerResult.Failure(StickerError.PackNotFound(identifier))
        }

        override suspend fun createPacksFromEmotes(
            basePackName: String,
            publisher: String,
            emotes: List<SevenTvEmote>,
            onProgress: ((current: Int, total: Int) -> Unit)?
        ): StickerResult<List<StickerPack>> {
            onProgress?.invoke(emotes.size, emotes.size)
            val pack = StickerPack(
                identifier = "pack_${basePackName.hashCode()}",
                name = basePackName,
                publisher = publisher,
                trayImagePath = "tray.webp",
                isAnimated = emotes.any { it.isAnimated },
                stickers = emptyList()
            )
            return StickerResult.Success(listOf(pack))
        }

        override suspend fun deletePack(identifier: String): StickerResult<Unit> = StickerResult.Success(Unit)

        override suspend fun updateStickerEmojis(stickerId: String, emojis: List<String>): StickerResult<Unit> =
            StickerResult.Success(Unit)

        override suspend fun updatePackWhitelisted(identifier: String, isWhitelisted: Boolean): StickerResult<Unit> =
            StickerResult.Success(Unit)

        override suspend fun syncWhitelistStatus(context: Context, packIdentifier: String): StickerResult<Boolean> =
            StickerResult.Success(false)
    }
}

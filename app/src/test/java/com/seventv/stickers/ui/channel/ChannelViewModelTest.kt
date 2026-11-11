package com.seventv.stickers.ui.channel

import android.content.Context
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.engine.PackSplittingEngine
import com.seventv.stickers.core.domain.engine.SplitPlan
import com.seventv.stickers.core.domain.manager.PackImportManager
import com.seventv.stickers.core.domain.manager.PackImportProgress
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.ChannelEmoteSetSummary
import com.seventv.stickers.core.network.model.ChannelSearchResult
import com.seventv.stickers.core.network.model.DownloadedEmoteAsset
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.core.network.model.SevenTvEmoteSet
import com.seventv.stickers.data.repository.SevenTvRepository
import com.seventv.stickers.data.repository.StickerPackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun searchStreamerDeduplicatesEmotesById() = runTest(testDispatcher) {
        val e1 = createSampleEmote("dup_1", "emote1")
        val e1Duplicate = createSampleEmote("dup_1", "emote1_alias")
        val e2 = createSampleEmote("dup_2", "emote2")
        val sampleSet = SevenTvEmoteSet("set_dup", "Dup Set", "streamer", listOf(e1, e1Duplicate, e2))
        fakeSevenTvRepository.emoteSetHandler = { StickerResult.Success(sampleSet) }

        viewModel.onSearchStreamer("streamer")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val success = state.loadState as ChannelLoadState.Success
        assertEquals(2, success.allEmotes.size)
        assertEquals("dup_1", success.allEmotes[0].id)
        assertEquals("dup_2", success.allEmotes[1].id)
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

    @Test
    fun toggleDraftEmoteAddsAndRemovesFromChannelDraft() {
        val emote1 = createSampleEmote("d_1", "emote1")
        val emote2 = createSampleEmote("d_2", "emote2")

        assertTrue(viewModel.uiState.value.draftEmotes.isEmpty())

        viewModel.onToggleDraftEmote(emote1)
        assertEquals(listOf(emote1), viewModel.uiState.value.draftEmotes)

        viewModel.onToggleDraftEmote(emote2)
        assertEquals(listOf(emote1, emote2), viewModel.uiState.value.draftEmotes)

        viewModel.onToggleDraftEmote(emote1)
        assertEquals(listOf(emote2), viewModel.uiState.value.draftEmotes)

        viewModel.onClearDraft()
        assertTrue(viewModel.uiState.value.draftEmotes.isEmpty())
    }

    @Test
    fun createPackFromDraftCreatesPackAndClearsDraftOnSuccess() = runTest(testDispatcher) {
        val emotes = (1..5).map { createSampleEmote("d_$it", "name_$it") }
        emotes.forEach { viewModel.onToggleDraftEmote(it) }

        viewModel.onCreatePackFromDraft("Canal Pack")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.draftEmotes.isEmpty())
        val importState = viewModel.uiState.value.importState
        assertTrue("Expected Success, got $importState", importState is ChannelImportState.Success)
    }

    @Test
    fun createPackFromDraftFailsWhenUnder3Emotes() = runTest(testDispatcher) {
        val emote = createSampleEmote("d_1", "name_1")
        viewModel.onToggleDraftEmote(emote)

        viewModel.onCreatePackFromDraft("Insuficiente")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.draftEmotes.size)
        val importState = viewModel.uiState.value.importState
        assertTrue("Expected Error or Idle, got $importState", importState is ChannelImportState.Error || importState is ChannelImportState.Idle)
    }

    @Test
    fun existingPacksIsObservedFromRepository() = runTest(testDispatcher) {
        val pack1 = StickerPack(
            identifier = "pack_1",
            name = "Pack 1",
            publisher = "7TV",
            trayImagePath = "tray1.png",
            isAnimated = false
        )
        val pack2 = StickerPack(
            identifier = "pack_2",
            name = "Pack 2",
            publisher = "7TV",
            trayImagePath = "tray2.png",
            isAnimated = true
        )

        fakeStickerPackRepository.packsFlow.value = listOf(pack1, pack2)
        runCurrent()

        assertEquals(listOf(pack1, pack2), viewModel.uiState.value.existingPacks)
    }

    @Test
    fun openAndDismissAddToExistingPackDialog() {
        val emote = createSampleEmote("1", "pepe")

        // Open with explicit emotes
        viewModel.onOpenAddToExistingPackDialog(listOf(emote))
        assertTrue(viewModel.uiState.value.isAddToExistingPackDialogOpen)
        assertEquals(listOf(emote), viewModel.uiState.value.emotesToAddToPack)

        // Dismiss dialog
        viewModel.onDismissAddToExistingPackDialog()
        assertFalse(viewModel.uiState.value.isAddToExistingPackDialogOpen)
        assertTrue(viewModel.uiState.value.emotesToAddToPack.isEmpty())

        // Default open uses draft emotes if non-empty
        viewModel.onToggleDraftEmote(emote)
        viewModel.onOpenAddToExistingPackDialog()
        assertTrue(viewModel.uiState.value.isAddToExistingPackDialogOpen)
        assertEquals(listOf(emote), viewModel.uiState.value.emotesToAddToPack)

        // Open with empty list does nothing
        viewModel.onDismissAddToExistingPackDialog()
        viewModel.onOpenAddToExistingPackDialog(emptyList())
        assertFalse(viewModel.uiState.value.isAddToExistingPackDialogOpen)
    }

    @Test
    fun addEmotesToPackSuccessRemovesEmotesFromDraftAndSetsSuccessMessage() = runTest(testDispatcher) {
        val pack = StickerPack(
            identifier = "pack_target",
            name = "Target Pack",
            publisher = "7TV",
            trayImagePath = "tray.png",
            isAnimated = false
        )
        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        runCurrent()

        val emote1 = createSampleEmote("1", "emote1")
        val emote2 = createSampleEmote("2", "emote2")
        val emote3 = createSampleEmote("3", "emote3")
        viewModel.onToggleDraftEmote(emote1)
        viewModel.onToggleDraftEmote(emote2)
        viewModel.onToggleDraftEmote(emote3)

        fakeStickerPackRepository.addEmotesToPackHandler = { packId, emotes ->
            assertEquals("pack_target", packId)
            assertEquals(listOf(emote1, emote2), emotes)
            StickerResult.Success(Unit)
        }

        viewModel.onOpenAddToExistingPackDialog(listOf(emote1, emote2))
        viewModel.onAddEmotesToPack("pack_target")
        advanceUntilIdle()

        assertEquals(1, fakeStickerPackRepository.addEmotesCallCount)
        assertFalse(viewModel.uiState.value.isAddToExistingPackDialogOpen)
        assertFalse(viewModel.uiState.value.isAddingToPack)
        assertEquals(listOf(emote3), viewModel.uiState.value.draftEmotes)
        assertEquals("¡2 stickers añadidos a \"Target Pack\"!", viewModel.uiState.value.addToPackSuccessMessage)
        assertNull(viewModel.uiState.value.addToPackErrorMessage)

        viewModel.onClearAddToPackMessages()
        assertNull(viewModel.uiState.value.addToPackSuccessMessage)
    }

    @Test
    fun addEmotesToPackFailureSetsErrorMessageAndKeepsDraft() = runTest(testDispatcher) {
        val emote1 = createSampleEmote("1", "emote1")
        viewModel.onToggleDraftEmote(emote1)

        fakeStickerPackRepository.addEmotesToPackHandler = { _, _ ->
            StickerResult.Failure(StickerError.ExceedsPackLimit(31, 30))
        }

        viewModel.onOpenAddToExistingPackDialog(listOf(emote1))
        viewModel.onAddEmotesToPack("pack_full")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAddingToPack)
        assertEquals(listOf(emote1), viewModel.uiState.value.draftEmotes)
        assertEquals("El paquete supera el límite de 30 stickers (actual: 31)", viewModel.uiState.value.addToPackErrorMessage)
        assertNull(viewModel.uiState.value.addToPackSuccessMessage)
    }

    @Test
    fun searchStreamerWithMultipleEmoteSetsLoadsAllPacks() = runTest(testDispatcher) {
        val emotes1 = listOf(createSampleEmote("1", "e1"))
        val set1 = SevenTvEmoteSet("set_1", "Main Set", "streamer", emotes1)
        val summary1 = ChannelEmoteSetSummary("set_1", "Main Set", 1, isActive = true)
        val summary2 = ChannelEmoteSetSummary("set_2", "Holiday Set", 50, isActive = false)

        fakeSevenTvRepository.channelDataHandler = { query ->
            StickerResult.Success(
                ChannelSearchResult(
                    streamerName = "Streamer",
                    username = query,
                    availableSets = listOf(summary1, summary2),
                    activeSet = set1
                )
            )
        }

        viewModel.onSearchStreamer("streamer")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success, got ${state.loadState}", state.loadState is ChannelLoadState.Success)
        val success = state.loadState as ChannelLoadState.Success
        assertEquals(2, success.availableSets.size)
        assertEquals("set_1", success.currentSetId)
        assertEquals("Main Set", success.emoteSet.name)
        assertEquals(1, success.allEmotes.size)
    }

    @Test
    fun selectEmoteSetLoadsNewEmotePack() = runTest(testDispatcher) {
        val emotes1 = listOf(createSampleEmote("1", "e1"))
        val set1 = SevenTvEmoteSet("set_1", "Main Set", "streamer", emotes1)
        val summary1 = ChannelEmoteSetSummary("set_1", "Main Set", 1, isActive = true)
        val summary2 = ChannelEmoteSetSummary("set_2", "Holiday Set", 2, isActive = false)

        fakeSevenTvRepository.channelDataHandler = { query ->
            StickerResult.Success(
                ChannelSearchResult(
                    streamerName = "Streamer",
                    username = query,
                    availableSets = listOf(summary1, summary2),
                    activeSet = set1
                )
            )
        }

        val emotes2 = listOf(createSampleEmote("2", "e2"), createSampleEmote("3", "e3"))
        val set2 = SevenTvEmoteSet("set_2", "Holiday Set", "streamer", emotes2)
        fakeSevenTvRepository.emoteSetHandler = { setId ->
            if (setId == "set_2") StickerResult.Success(set2) else StickerResult.Failure(StickerError.ResourceNotFound)
        }

        viewModel.onSearchStreamer("streamer")
        advanceUntilIdle()

        // Select the holiday pack
        viewModel.onSelectEmoteSet("set_2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val success = state.loadState as ChannelLoadState.Success
        assertEquals("set_2", success.currentSetId)
        assertEquals("Holiday Set", success.emoteSet.name)
        assertEquals(2, success.allEmotes.size)
        assertFalse(success.isLoadingSet)
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
        var channelDataHandler: (suspend (String) -> StickerResult<ChannelSearchResult>)? = null
        var emoteSetHandler: suspend (String) -> StickerResult<SevenTvEmoteSet> = {
            StickerResult.Failure(StickerError.ResourceNotFound)
        }
        var searchEmotesHandler: suspend (String, Int, Int) -> StickerResult<List<SevenTvEmote>> = { _, _, _ ->
            StickerResult.Success(emptyList())
        }

        override suspend fun getChannelData(query: String): StickerResult<ChannelSearchResult> {
            if (channelDataHandler != null) {
                return channelDataHandler!!.invoke(query)
            }
            val setResult = streamerEmoteSetHandler?.invoke(query) ?: emoteSetHandler(query)
            return when (setResult) {
                is StickerResult.Success -> {
                    val set = setResult.data
                    val summary = ChannelEmoteSetSummary(
                        id = set.id,
                        name = set.name,
                        emoteCount = set.emotes.size,
                        isActive = true
                    )
                    StickerResult.Success(
                        ChannelSearchResult(
                            streamerName = set.name,
                            username = query,
                            availableSets = listOf(summary),
                            activeSet = set
                        )
                    )
                }
                is StickerResult.Failure -> StickerResult.Failure(setResult.error)
            }
        }

        override suspend fun getStreamerEmoteSet(query: String): StickerResult<SevenTvEmoteSet> {
            return streamerEmoteSetHandler?.invoke(query) ?: super.getStreamerEmoteSet(query)
        }

        override suspend fun searchEmotes(
            query: String,
            page: Int,
            limit: Int,
            category: String?
        ): StickerResult<List<SevenTvEmote>> {
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
        val packsFlow = MutableStateFlow<List<StickerPack>>(emptyList())
        override fun observeAllPacks(): Flow<List<StickerPack>> = packsFlow

        var addEmotesToPackHandler: (suspend (String, List<SevenTvEmote>) -> StickerResult<Unit>)? = null
        var addEmotesCallCount: Int = 0

        override suspend fun getPack(identifier: String): StickerResult<StickerPack> {
            return StickerResult.Failure(StickerError.PackNotFound(identifier))
        }

        override suspend fun createPacksFromEmotes(
            basePackName: String,
            publisher: String,
            emotes: List<SevenTvEmote>,
            onProgress: ((current: Int, total: Int) -> Unit)?,
            originSetId: String?,
            originStreamerName: String?
        ): StickerResult<List<StickerPack>> {
            onProgress?.invoke(emotes.size, emotes.size)
            val pack = StickerPack(
                identifier = "pack_${basePackName.hashCode()}",
                name = basePackName,
                publisher = publisher,
                trayImagePath = "tray.webp",
                isAnimated = emotes.any { it.isAnimated },
                stickers = emptyList(),
                originSetId = originSetId,
                originStreamerName = originStreamerName
            )
            return StickerResult.Success(listOf(pack))
        }

        override suspend fun deletePack(identifier: String): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun deletePacks(identifiers: List<String>): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun renamePack(identifier: String, newName: String): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun mergePacks(sourcePackIdentifiers: List<String>, basePackName: String): StickerResult<List<StickerPack>> =
            StickerResult.Success(emptyList())
        override suspend fun deleteStickerFromPack(packIdentifier: String, stickerId: String): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun addEmotesToPack(packIdentifier: String, emotes: List<SevenTvEmote>): StickerResult<Unit> {
            addEmotesCallCount++
            return addEmotesToPackHandler?.invoke(packIdentifier, emotes) ?: StickerResult.Success(Unit)
        }

        override suspend fun updateStickerEmojis(stickerId: String, emojis: List<String>): StickerResult<Unit> =
            StickerResult.Success(Unit)
        override suspend fun reorderStickers(packIdentifier: String, orderedStickerIds: List<String>): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun setStickerAsTrayIcon(packIdentifier: String, stickerId: String): StickerResult<Unit> = StickerResult.Success(Unit)

        override suspend fun updatePackWhitelisted(identifier: String, isWhitelisted: Boolean): StickerResult<Unit> =
            StickerResult.Success(Unit)

        override suspend fun syncWhitelistStatus(context: Context, packIdentifier: String): StickerResult<Boolean> =
            StickerResult.Success(false)

        override suspend fun checkForPackUpdates(packIdentifier: String): StickerResult<List<SevenTvEmote>> =
            StickerResult.Success(emptyList())
    }
}

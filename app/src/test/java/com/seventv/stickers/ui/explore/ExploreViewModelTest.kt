package com.seventv.stickers.ui.explore

import android.content.Context
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.model.StickerPack
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSevenTvRepository: FakeSevenTvRepository
    private lateinit var fakeStickerPackRepository: FakeStickerPackRepository
    private lateinit var viewModel: ExploreViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSevenTvRepository = FakeSevenTvRepository()
        fakeStickerPackRepository = FakeStickerPackRepository()
        viewModel = ExploreViewModel(
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
        assertEquals(EmoteFilter.ALL, state.activeFilter)
        assertTrue(state.searchResults is SearchResultsState.Idle)
        assertTrue(state.draftEmotes.isEmpty())
        assertTrue(state.packCreationState is PackCreationState.Idle)
        assertFalse(state.isSavePackDialogOpen)
        assertEquals("", state.draftPackName)
    }

    @Test
    fun searchWithDebounce400msPopulatesSuccess() = runTest(testDispatcher) {
        val emote1 = createSampleEmote("1", "pepeD", isAnimated = true)
        fakeSevenTvRepository.searchEmotesHandler = { query, _, _ ->
            assertEquals("pepe", query)
            StickerResult.Success(listOf(emote1))
        }

        viewModel.onQueryChanged("pepe")

        // Before 400ms: repository should not have been called yet
        advanceTimeBy(399)
        assertEquals(0, fakeSevenTvRepository.searchCallCount)
        assertTrue(viewModel.uiState.value.searchResults is SearchResultsState.Idle)

        // At 400ms: debounce triggers, search is executed
        advanceTimeBy(2)
        assertEquals(1, fakeSevenTvRepository.searchCallCount)
        assertEquals("pepe", fakeSevenTvRepository.lastSearchQuery)

        val searchResults = viewModel.uiState.value.searchResults
        assertTrue("Expected Success, got $searchResults", searchResults is SearchResultsState.Success)
        assertEquals(listOf(emote1), (searchResults as SearchResultsState.Success).emotes)
    }

    @Test
    fun rapidTypingCancelsEarlierSearchesAndDebounces() = runTest(testDispatcher) {
        fakeSevenTvRepository.searchEmotesHandler = { query, _, _ ->
            StickerResult.Success(listOf(createSampleEmote("1", query)))
        }

        viewModel.onQueryChanged("p")
        advanceTimeBy(200)
        viewModel.onQueryChanged("pe")
        advanceTimeBy(200)
        viewModel.onQueryChanged("pep")
        advanceTimeBy(200)
        viewModel.onQueryChanged("pepe")

        // Still 0 calls because 400ms haven't elapsed since last keypress
        assertEquals(0, fakeSevenTvRepository.searchCallCount)

        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, fakeSevenTvRepository.searchCallCount)
        assertEquals("pepe", fakeSevenTvRepository.lastSearchQuery)
    }

    @Test
    fun blankQueryRestoresCategoryEmotes() = runTest(testDispatcher) {
        val trendingEmote = createSampleEmote("1", "catJAM", isAnimated = true)
        fakeSevenTvRepository.searchEmotesWithCategoryHandler = { query, _, _, category ->
            assertEquals("", query)
            assertEquals("TRENDING_DAY", category)
            StickerResult.Success(listOf(trendingEmote))
        }

        viewModel.onQueryChanged("pepe")
        advanceTimeBy(200)
        viewModel.onQueryChanged("")
        advanceTimeBy(500)
        runCurrent()

        assertEquals(1, fakeSevenTvRepository.searchCallCount)
        val state = viewModel.uiState.value
        assertTrue(state.searchResults is SearchResultsState.Success)
        assertEquals(listOf(trendingEmote), (state.searchResults as SearchResultsState.Success).emotes)
    }

    @Test
    fun loadCategoryEmotesPopulatesTrendingSuccess() = runTest(testDispatcher) {
        val trendingEmote = createSampleEmote("1", "GIGACHAD", isAnimated = false)
        fakeSevenTvRepository.searchEmotesWithCategoryHandler = { query, _, _, category ->
            assertEquals("", query)
            assertEquals("TRENDING_DAY", category)
            StickerResult.Success(listOf(trendingEmote))
        }

        viewModel.loadCategoryEmotes(ExploreCategory.TRENDING)
        runCurrent()

        assertEquals(1, fakeSevenTvRepository.searchCallCount)
        val state = viewModel.uiState.value
        assertEquals(ExploreCategory.TRENDING, state.selectedCategory)
        assertTrue(state.searchResults is SearchResultsState.Success)
        assertEquals(listOf(trendingEmote), (state.searchResults as SearchResultsState.Success).emotes)
    }

    @Test
    fun onCategorySelectedSwitchesToTopAndLoadsEmotes() = runTest(testDispatcher) {
        val topEmote = createSampleEmote("2", "NOOOO", isAnimated = true)
        fakeSevenTvRepository.searchEmotesWithCategoryHandler = { query, _, _, category ->
            assertEquals("", query)
            assertEquals("TOP", category)
            StickerResult.Success(listOf(topEmote))
        }

        viewModel.onCategorySelected(ExploreCategory.TOP)
        runCurrent()

        assertEquals(1, fakeSevenTvRepository.searchCallCount)
        val state = viewModel.uiState.value
        assertEquals(ExploreCategory.TOP, state.selectedCategory)
        assertTrue(state.searchResults is SearchResultsState.Success)
        assertEquals(listOf(topEmote), (state.searchResults as SearchResultsState.Success).emotes)
    }

    @Test
    fun searchEmptyResultsPopulatesEmptyState() = runTest(testDispatcher) {
        fakeSevenTvRepository.searchEmotesHandler = { _, _, _ ->
            StickerResult.Success(emptyList())
        }

        viewModel.onQueryChanged("nonexistent")
        advanceTimeBy(400)
        runCurrent()

        val searchResults = viewModel.uiState.value.searchResults
        assertTrue(searchResults is SearchResultsState.Empty)
        assertEquals("nonexistent", (searchResults as SearchResultsState.Empty).query)
    }

    @Test
    fun searchErrorPopulatesErrorState() = runTest(testDispatcher) {
        fakeSevenTvRepository.searchEmotesHandler = { _, _, _ ->
            StickerResult.Failure(StickerError.NetworkError(500, "Server Error"))
        }

        viewModel.onQueryChanged("error")
        advanceTimeBy(400)
        runCurrent()

        val searchResults = viewModel.uiState.value.searchResults
        assertTrue(searchResults is SearchResultsState.Error)
        assertEquals("Server Error", (searchResults as SearchResultsState.Error).message)
    }

    @Test
    fun filterSwitchingReflectsInFilteredEmoteList() = runTest(testDispatcher) {
        val anim1 = createSampleEmote("1", "catJAM", isAnimated = true)
        val anim2 = createSampleEmote("2", "pepeD", isAnimated = true)
        val stat1 = createSampleEmote("3", "KEKW", isAnimated = false)
        val stat2 = createSampleEmote("4", "monkaS", isAnimated = false)

        fakeSevenTvRepository.searchEmotesHandler = { _, _, _ ->
            StickerResult.Success(listOf(anim1, anim2, stat1, stat2))
        }

        viewModel.onQueryChanged("all")
        advanceTimeBy(400)
        runCurrent()

        // Default ALL
        assertEquals(EmoteFilter.ALL, viewModel.uiState.value.activeFilter)
        assertEquals(listOf(anim1, anim2, stat1, stat2), viewModel.uiState.value.filteredEmotes)

        // Switch to ANIMATED
        viewModel.onFilterSelected(EmoteFilter.ANIMATED)
        assertEquals(EmoteFilter.ANIMATED, viewModel.uiState.value.activeFilter)
        assertEquals(listOf(anim1, anim2), viewModel.uiState.value.filteredEmotes)

        // Switch to STATIC
        viewModel.onFilterSelected(EmoteFilter.STATIC)
        assertEquals(EmoteFilter.STATIC, viewModel.uiState.value.activeFilter)
        assertEquals(listOf(stat1, stat2), viewModel.uiState.value.filteredEmotes)

        // Switch back to ALL
        viewModel.onFilterSelected(EmoteFilter.ALL)
        assertEquals(listOf(anim1, anim2, stat1, stat2), viewModel.uiState.value.filteredEmotes)
    }

    @Test
    fun filteredEmotesDeduplicatesDuplicateEmoteIds() = runTest(testDispatcher) {
        val emote1 = createSampleEmote("dup_1", "pepe1")
        val emote1Duplicate = createSampleEmote("dup_1", "pepe1_alias")
        val emote2 = createSampleEmote("dup_2", "pepe2")

        fakeSevenTvRepository.searchEmotesHandler = { _, _, _ ->
            StickerResult.Success(listOf(emote1, emote1Duplicate, emote2))
        }

        viewModel.onQueryChanged("pepe")
        advanceTimeBy(400)
        runCurrent()

        val filtered = viewModel.uiState.value.filteredEmotes
        assertEquals(2, filtered.size)
        assertEquals("dup_1", filtered[0].id)
        assertEquals("dup_2", filtered[1].id)
    }

    @Test
    fun toggleDraftAddsEmoteAndRemovesOnSecondToggle() {
        val emote1 = createSampleEmote("1", "catJAM")
        val emote2 = createSampleEmote("2", "KEKW")

        assertTrue(viewModel.uiState.value.draftEmotes.isEmpty())

        // Add emote 1
        viewModel.onToggleDraftEmote(emote1)
        assertEquals(listOf(emote1), viewModel.uiState.value.draftEmotes)

        // Add emote 2
        viewModel.onToggleDraftEmote(emote2)
        assertEquals(listOf(emote1, emote2), viewModel.uiState.value.draftEmotes)

        // Toggle emote 1 again -> removes emote 1
        viewModel.onToggleDraftEmote(emote1)
        assertEquals(listOf(emote2), viewModel.uiState.value.draftEmotes)

        // Clear draft
        viewModel.onClearDraft()
        assertTrue(viewModel.uiState.value.draftEmotes.isEmpty())
    }

    @Test
    fun rejectsDuplicateEmotesInDraft() {
        val emote = createSampleEmote("1", "catJAM")
        viewModel.onToggleDraftEmote(emote)
        assertEquals(1, viewModel.uiState.value.draftEmotes.size)

        // Toggling same emote again removes it, preventing duplicate accumulation
        viewModel.onToggleDraftEmote(emote)
        assertEquals(0, viewModel.uiState.value.draftEmotes.size)
    }

    @Test
    fun enforcesMax30Limit() {
        for (i in 1..30) {
            viewModel.onToggleDraftEmote(createSampleEmote("id_$i", "emote_$i"))
        }
        assertEquals(30, viewModel.uiState.value.draftEmotes.size)

        // Attempting to add 31st emote
        val emote31 = createSampleEmote("id_31", "emote_31")
        viewModel.onToggleDraftEmote(emote31)

        // Still 30, emote 31 was rejected
        assertEquals(30, viewModel.uiState.value.draftEmotes.size)
        assertFalse(viewModel.uiState.value.draftEmotes.any { it.id == "id_31" })

        // Removing an emote reduces count to 29
        viewModel.onToggleDraftEmote(createSampleEmote("id_1", "emote_1"))
        assertEquals(29, viewModel.uiState.value.draftEmotes.size)

        // Now adding emote 31 succeeds since count is 29 < 30
        viewModel.onToggleDraftEmote(emote31)
        assertEquals(30, viewModel.uiState.value.draftEmotes.size)
        assertTrue(viewModel.uiState.value.draftEmotes.any { it.id == "id_31" })
    }

    @Test
    fun savePackDialogFlowAndStateChanges() {
        viewModel.onOpenSavePackDialog()
        assertTrue(viewModel.uiState.value.isSavePackDialogOpen)
        assertEquals("7TV Pack", viewModel.uiState.value.draftPackName)

        viewModel.onDraftPackNameChanged("Custom Pack Name")
        assertEquals("Custom Pack Name", viewModel.uiState.value.draftPackName)

        viewModel.onDismissSavePackDialog()
        assertFalse(viewModel.uiState.value.isSavePackDialogOpen)
    }

    @Test
    fun savePackInvokesCreatePacksFromEmotesAndClearsDraftOnSuccess() = runTest(testDispatcher) {
        val emotes = (1..5).map { createSampleEmote("id_$it", "emote_$it") }
        emotes.forEach { viewModel.onToggleDraftEmote(it) }

        val createdPack = StickerPack(
            identifier = "pack_1",
            name = "My Cool Pack",
            publisher = "7TV",
            trayImagePath = "tray.png",
            isAnimated = false
        )
        fakeStickerPackRepository.createPacksFromEmotesHandler = { name, publisher, draftEmotes ->
            assertEquals("My Cool Pack", name)
            assertEquals("7TV", publisher)
            assertEquals(emotes, draftEmotes)
            StickerResult.Success(listOf(createdPack))
        }

        viewModel.onOpenSavePackDialog()
        viewModel.onDraftPackNameChanged("My Cool Pack")
        viewModel.onConfirmSavePack()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSavePackDialogOpen)
        assertTrue(viewModel.uiState.value.draftEmotes.isEmpty())
        val creationState = viewModel.uiState.value.packCreationState
        assertTrue("Expected Success, got $creationState", creationState is PackCreationState.Success)
        assertEquals(listOf(createdPack), (creationState as PackCreationState.Success).createdPacks)
    }

    @Test
    fun savePackErrorHandling() = runTest(testDispatcher) {
        val emotes = (1..5).map { createSampleEmote("id_$it", "emote_$it") }
        emotes.forEach { viewModel.onToggleDraftEmote(it) }

        fakeStickerPackRepository.createPacksFromEmotesHandler = { _, _, _ ->
            StickerResult.Failure(StickerError.CorruptedSourceData)
        }

        viewModel.onConfirmSavePack()
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.draftEmotes.size)
        val creationState = viewModel.uiState.value.packCreationState
        assertTrue("Expected Error, got $creationState", creationState is PackCreationState.Error)
    }

    @Test
    fun savePackRejectsDraftWithLessThan3Stickers() = runTest(testDispatcher) {
        val emote1 = createSampleEmote("1", "one")
        val emote2 = createSampleEmote("2", "two")
        viewModel.onToggleDraftEmote(emote1)
        viewModel.onToggleDraftEmote(emote2)

        viewModel.onConfirmSavePack()
        advanceUntilIdle()

        assertEquals(0, fakeStickerPackRepository.createCallCount)
        val creationState = viewModel.uiState.value.packCreationState
        assertTrue("Expected Error for < 3 emotes, got $creationState", creationState is PackCreationState.Error)
    }

    @Test
    fun createPackFromDraftSetsNameAndInvokesRepository() = runTest(testDispatcher) {
        val emotes = (1..4).map { createSampleEmote("id_$it", "emote_$it") }
        emotes.forEach { viewModel.onToggleDraftEmote(it) }

        val createdPack = StickerPack(
            identifier = "pack_draft",
            name = "Borrador M3",
            publisher = "7TV",
            trayImagePath = "tray.png",
            isAnimated = false
        )
        fakeStickerPackRepository.createPacksFromEmotesHandler = { name, publisher, draftEmotes ->
            assertEquals("Borrador M3", name)
            assertEquals("7TV", publisher)
            assertEquals(emotes, draftEmotes)
            StickerResult.Success(listOf(createdPack))
        }

        viewModel.onCreatePackFromDraft("Borrador M3")
        advanceUntilIdle()

        assertEquals("Borrador M3", viewModel.uiState.value.draftPackName)
        assertTrue(viewModel.uiState.value.draftEmotes.isEmpty())
        val creationState = viewModel.uiState.value.packCreationState
        assertTrue(creationState is PackCreationState.Success)
    }

    @Test
    fun emotesPropertyExposesFilteredEmotes() = runTest(testDispatcher) {
        val emote = createSampleEmote("id_1", "kekw")
        fakeSevenTvRepository.searchEmotesHandler = { _, _, _ ->
            StickerResult.Success(listOf(emote))
        }

        viewModel.onQueryChanged("kekw")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(viewModel.uiState.value.filteredEmotes, viewModel.uiState.value.emotes)
        assertEquals(listOf(emote), viewModel.uiState.value.emotes)
    }

    @Test
    fun factoryCreatesExploreViewModel() {
        val factory = ExploreViewModel.Factory(fakeSevenTvRepository, fakeStickerPackRepository)
        val vm = factory.create(ExploreViewModel::class.java)
        assertNotNull(vm)
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

    private class FakeSevenTvRepository : SevenTvRepository {
        var searchEmotesHandler: (suspend (String, Int, Int) -> StickerResult<List<SevenTvEmote>>)? = null
        var searchEmotesWithCategoryHandler: (suspend (String, Int, Int, String?) -> StickerResult<List<SevenTvEmote>>)? = null
        var searchCallCount: Int = 0
        var lastSearchQuery: String? = null
        var lastSearchCategory: String? = null

        override suspend fun searchEmotes(
            query: String,
            page: Int,
            limit: Int,
            category: String?
        ): StickerResult<List<SevenTvEmote>> {
            searchCallCount++
            lastSearchQuery = query
            lastSearchCategory = category
            return searchEmotesWithCategoryHandler?.invoke(query, page, limit, category)
                ?: searchEmotesHandler?.invoke(query, page, limit)
                ?: StickerResult.Success(emptyList())
        }

        override suspend fun getEmote(emoteId: String): StickerResult<SevenTvEmote> =
            StickerResult.Failure(StickerError.ResourceNotFound)

        override suspend fun getEmoteSet(setId: String): StickerResult<SevenTvEmoteSet> =
            StickerResult.Failure(StickerError.ResourceNotFound)

        override suspend fun downloadEmote(emote: SevenTvEmote): StickerResult<DownloadedEmoteAsset> =
            StickerResult.Failure(StickerError.ResourceNotFound)
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
        org.junit.Assert.assertNull(viewModel.uiState.value.addToPackErrorMessage)

        viewModel.onClearAddToPackMessages()
        org.junit.Assert.assertNull(viewModel.uiState.value.addToPackSuccessMessage)
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
        org.junit.Assert.assertNull(viewModel.uiState.value.addToPackSuccessMessage)
    }

    private class FakeStickerPackRepository : StickerPackRepository {
        var createPacksFromEmotesHandler: (suspend (String, String, List<SevenTvEmote>) -> StickerResult<List<StickerPack>>)? = null
        var createCallCount: Int = 0

        val packsFlow = MutableStateFlow<List<StickerPack>>(emptyList())
        override fun observeAllPacks(): Flow<List<StickerPack>> = packsFlow

        var addEmotesToPackHandler: (suspend (String, List<SevenTvEmote>) -> StickerResult<Unit>)? = null
        var addEmotesCallCount: Int = 0

        override suspend fun getPack(identifier: String): StickerResult<StickerPack> =
            StickerResult.Failure(StickerError.PackNotFound(identifier))

        override suspend fun createPacksFromEmotes(
            basePackName: String,
            publisher: String,
            emotes: List<SevenTvEmote>,
            onProgress: ((current: Int, total: Int) -> Unit)?,
            originSetId: String?,
            originStreamerName: String?
        ): StickerResult<List<StickerPack>> {
            createCallCount++
            return createPacksFromEmotesHandler?.invoke(basePackName, publisher, emotes)
                ?: StickerResult.Success(emptyList())
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
        override suspend fun updateStickerEmojis(stickerId: String, emojis: List<String>): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun updatePackWhitelisted(identifier: String, isWhitelisted: Boolean): StickerResult<Unit> = StickerResult.Success(Unit)
        override suspend fun syncWhitelistStatus(context: Context, packIdentifier: String): StickerResult<Boolean> = StickerResult.Success(false)
        override suspend fun checkForPackUpdates(packIdentifier: String): StickerResult<List<SevenTvEmote>> = StickerResult.Success(emptyList())
    }
}

package com.seventv.stickers.ui.packs

import android.content.Context
import android.content.pm.PackageInfo
import org.robolectric.RuntimeEnvironment
import com.seventv.stickers.core.common.result.StickerError
import com.seventv.stickers.core.common.result.StickerResult
import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote
import com.seventv.stickers.data.repository.StickerPackRepository
import com.seventv.stickers.ui.components.ChatSimulatorMode
import com.seventv.stickers.whatsapp.WhatsAppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PacksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeStickerPackRepository: FakePacksStickerPackRepository
    private lateinit var context: Context
    private lateinit var viewModel: PacksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        fakeStickerPackRepository = FakePacksStickerPackRepository()
        viewModel = PacksViewModel(stickerPackRepository = fakeStickerPackRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialPacksObservedFromRepository() = runTest(testDispatcher) {
        val pack1 = createSamplePack("pack_1", "Pack Uno", isWhitelisted = false)
        val pack2 = createSamplePack("pack_2", "Pack Dos", isWhitelisted = true)
        fakeStickerPackRepository.packsFlow.value = listOf(pack1, pack2)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success, got ${state.loadState}", state.loadState is PacksLoadState.Success)
        val success = state.loadState as PacksLoadState.Success
        assertEquals(2, success.packs.size)
        assertEquals("Pack Uno", success.packs[0].name)
        assertEquals("Pack Dos", success.packs[1].name)
    }

    @Test
    fun selectPackDetailUpdatesUiState() = runTest(testDispatcher) {
        val pack = createSamplePack("pack_1", "Detalle Pack")
        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        advanceUntilIdle()

        viewModel.onSelectPackDetail("pack_1")

        val state = viewModel.uiState.value
        assertNotNull(state.selectedPackDetail)
        assertEquals("pack_1", state.selectedPackDetail?.identifier)
        assertEquals("Detalle Pack", state.selectedPackDetail?.name)

        viewModel.onClearSelectedPackDetail()
        assertNull(viewModel.uiState.value.selectedPackDetail)
    }

    @Test
    fun chatSimulatorModeSwitching() {
        assertEquals(ChatSimulatorMode.WHATSAPP_DARK, viewModel.uiState.value.chatSimulatorMode)

        viewModel.onSetChatSimulatorMode(ChatSimulatorMode.WHATSAPP_LIGHT)
        assertEquals(ChatSimulatorMode.WHATSAPP_LIGHT, viewModel.uiState.value.chatSimulatorMode)

        viewModel.onSetChatSimulatorMode(ChatSimulatorMode.CHECKERBOARD)
        assertEquals(ChatSimulatorMode.CHECKERBOARD, viewModel.uiState.value.chatSimulatorMode)
    }

    @Test
    fun addPackToWhatsApp_whenWhatsAppNotInstalled_emitsWhatsAppNotInstalledEvent() {
        val packageManager = shadowOf(context.packageManager)
        // Ensure com.whatsapp is NOT installed
        packageManager.removePackage(WhatsAppConstants.PACKAGE_WHATSAPP)

        val pack = createSamplePack("pack_1", "Mi Pack")
        viewModel.onAddPackToWhatsApp(context, pack)

        val state = viewModel.uiState.value
        assertEquals(WhatsAppExportEvent.WhatsAppNotInstalled, state.exportEvent)

        viewModel.onClearExportEvent()
        assertNull(viewModel.uiState.value.exportEvent)
    }

    @Test
    fun addPackToWhatsApp_whenWhatsAppInstalled_emitsLaunchIntentEvent() {
        val packageManager = shadowOf(context.packageManager)
        // Simulate com.whatsapp installed
        packageManager.installPackage(
            PackageInfo().apply { packageName = WhatsAppConstants.PACKAGE_WHATSAPP }
        )

        val pack = createSamplePack("pack_1", "Mi Pack")
        viewModel.onAddPackToWhatsApp(context, pack)

        val state = viewModel.uiState.value
        assertTrue(state.exportEvent is WhatsAppExportEvent.LaunchIntent)
        val event = state.exportEvent as WhatsAppExportEvent.LaunchIntent
        assertEquals("pack_1", event.pack.identifier)
    }

    @Test
    fun deletePackFlow() = runTest(testDispatcher) {
        val pack = createSamplePack("pack_to_delete", "Borrar")
        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        advanceUntilIdle()

        viewModel.onOpenDeleteDialog(pack)
        assertTrue(viewModel.uiState.value.isDeleteConfirmDialogOpen)
        assertEquals(pack, viewModel.uiState.value.packToDelete)

        viewModel.onConfirmDeletePack()
        advanceUntilIdle()

        assertEquals("pack_to_delete", fakeStickerPackRepository.lastDeletedIdentifier)
        assertFalse(viewModel.uiState.value.isDeleteConfirmDialogOpen)
        assertNull(viewModel.uiState.value.packToDelete)
    }

    @Test
    fun syncWhitelistCallsRepository() = runTest(testDispatcher) {
        viewModel.syncWhitelist(context, "pack_123")
        advanceUntilIdle()

        assertEquals("pack_123", fakeStickerPackRepository.lastSyncedIdentifier)
    }

    @Test
    fun selectionModeToggleAndItemToggling() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedPackIds.isEmpty())

        viewModel.onToggleSelectionMode()
        assertTrue(viewModel.uiState.value.isSelectionMode)

        viewModel.onTogglePackSelection("pack_1")
        assertEquals(setOf("pack_1"), viewModel.uiState.value.selectedPackIds)

        viewModel.onTogglePackSelection("pack_2")
        assertEquals(setOf("pack_1", "pack_2"), viewModel.uiState.value.selectedPackIds)

        viewModel.onTogglePackSelection("pack_1")
        assertEquals(setOf("pack_2"), viewModel.uiState.value.selectedPackIds)

        viewModel.onToggleSelectionMode()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedPackIds.isEmpty())
    }

    @Test
    fun selectAllAndClearSelection() = runTest(testDispatcher) {
        val pack1 = createSamplePack("pack_1", "Uno")
        val pack2 = createSamplePack("pack_2", "Dos")
        fakeStickerPackRepository.packsFlow.value = listOf(pack1, pack2)
        advanceUntilIdle()

        viewModel.onToggleSelectionMode()
        viewModel.onSelectAllPacks()
        assertEquals(setOf("pack_1", "pack_2"), viewModel.uiState.value.selectedPackIds)

        viewModel.onClearSelection()
        assertTrue(viewModel.uiState.value.selectedPackIds.isEmpty())
    }

    @Test
    fun bulkDeleteFlow() = runTest(testDispatcher) {
        val pack1 = createSamplePack("pack_1", "Uno")
        val pack2 = createSamplePack("pack_2", "Dos")
        val pack3 = createSamplePack("pack_3", "Tres")
        fakeStickerPackRepository.packsFlow.value = listOf(pack1, pack2, pack3)
        advanceUntilIdle()

        viewModel.onToggleSelectionMode()
        viewModel.onTogglePackSelection("pack_1")
        viewModel.onTogglePackSelection("pack_2")

        viewModel.onOpenBulkDeleteDialog()
        assertTrue(viewModel.uiState.value.isBulkDeleteConfirmDialogOpen)

        viewModel.onConfirmBulkDelete()
        advanceUntilIdle()

        assertEquals(listOf("pack_1", "pack_2"), fakeStickerPackRepository.lastDeletedIdentifiers)
        assertFalse(viewModel.uiState.value.isBulkDeleteConfirmDialogOpen)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedPackIds.isEmpty())
    }

    @Test
    fun batchWhatsAppExportQueueChaining() = runTest(testDispatcher) {
        val packageManager = shadowOf(context.packageManager)
        packageManager.installPackage(
            PackageInfo().apply { packageName = WhatsAppConstants.PACKAGE_WHATSAPP }
        )

        val pack1 = createSamplePack("pack_1", "Uno")
        val pack2 = createSamplePack("pack_2", "Dos")
        fakeStickerPackRepository.packsFlow.value = listOf(pack1, pack2)
        advanceUntilIdle()

        viewModel.onToggleSelectionMode()
        viewModel.onTogglePackSelection("pack_1")
        viewModel.onTogglePackSelection("pack_2")

        viewModel.onStartBatchWhatsAppExport(context)

        val state1 = viewModel.uiState.value
        assertTrue(state1.exportEvent is WhatsAppExportEvent.LaunchIntent)
        assertEquals("pack_1", (state1.exportEvent as WhatsAppExportEvent.LaunchIntent).pack.identifier)
        assertEquals(listOf(pack2), state1.exportQueue)

        viewModel.onClearExportEvent()
        viewModel.onAdvanceExportQueue(context)

        val state2 = viewModel.uiState.value
        assertTrue(state2.exportEvent is WhatsAppExportEvent.LaunchIntent)
        assertEquals("pack_2", (state2.exportEvent as WhatsAppExportEvent.LaunchIntent).pack.identifier)
        assertTrue(state2.exportQueue.isEmpty())

        viewModel.onClearExportEvent()
        viewModel.onAdvanceExportQueue(context)

        val state3 = viewModel.uiState.value
        assertFalse(state3.isSelectionMode)
        assertTrue(state3.selectedPackIds.isEmpty())
    }

    @Test
    fun renamePackFlow() = runTest(testDispatcher) {
        val pack = createSamplePack("pack_1", "Original")
        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        advanceUntilIdle()

        viewModel.onSelectPackDetail("pack_1")
        viewModel.onOpenRenameDialog(pack)
        assertTrue(viewModel.uiState.value.isRenameDialogOpen)
        assertEquals(pack, viewModel.uiState.value.packToRename)

        viewModel.onConfirmRename("Nuevo Nombre")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRenameDialogOpen)
        assertNull(viewModel.uiState.value.packToRename)
        assertEquals("Nuevo Nombre", viewModel.uiState.value.selectedPackDetail?.name)
    }

    @Test
    fun deleteStickerFromPackFlow() = runTest(testDispatcher) {
        val pack = createSamplePack("pack_1", "Pack")
        val sticker = pack.stickers.first()
        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        advanceUntilIdle()

        viewModel.onSelectPackDetail("pack_1")
        viewModel.onOpenDeleteStickerDialog(sticker)
        assertTrue(viewModel.uiState.value.isDeleteStickerConfirmDialogOpen)
        assertEquals(sticker, viewModel.uiState.value.stickerToDelete)

        viewModel.onConfirmDeleteSticker("pack_1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleteStickerConfirmDialogOpen)
        assertNull(viewModel.uiState.value.stickerToDelete)
        assertEquals("Sticker eliminado", viewModel.uiState.value.userFeedbackMessage)
    }

    private fun createSamplePack(
        id: String,
        name: String,
        isWhitelisted: Boolean = false
    ): StickerPack {
        val sticker = PackSticker(
            id = "s_1",
            packIdentifier = id,
            emoteId = "e_1",
            emoteName = "pepe",
            filePath = "/data/pack/$id/sticker_1.webp",
            emojis = listOf("💬"),
            fileLength = 1000L,
            sortOrder = 0
        )
        return StickerPack(
            identifier = id,
            name = name,
            publisher = "7TV Stickers",
            trayImagePath = "/data/pack/$id/tray.webp",
            isAnimated = false,
            isWhitelisted = isWhitelisted,
            stickers = listOf(sticker)
        )
    }

    private class FakePacksStickerPackRepository : StickerPackRepository {
        val packsFlow = MutableStateFlow<List<StickerPack>>(emptyList())
        var lastDeletedIdentifier: String? = null
        var lastSyncedIdentifier: String? = null

        override fun observeAllPacks(): Flow<List<StickerPack>> = packsFlow.asStateFlow()

        override suspend fun getPack(identifier: String): StickerResult<StickerPack> {
            val found = packsFlow.value.find { it.identifier == identifier }
            return if (found != null) StickerResult.Success(found)
            else StickerResult.Failure(StickerError.PackNotFound(identifier))
        }

        override suspend fun createPacksFromEmotes(
            basePackName: String,
            publisher: String,
            emotes: List<SevenTvEmote>,
            onProgress: ((current: Int, total: Int) -> Unit)?
        ): StickerResult<List<StickerPack>> = StickerResult.Success(emptyList())

        override suspend fun deletePack(identifier: String): StickerResult<Unit> {
            lastDeletedIdentifier = identifier
            packsFlow.value = packsFlow.value.filter { it.identifier != identifier }
            return StickerResult.Success(Unit)
        }

        var lastDeletedIdentifiers: List<String>? = null
        override suspend fun deletePacks(identifiers: List<String>): StickerResult<Unit> {
            lastDeletedIdentifiers = identifiers
            packsFlow.value = packsFlow.value.filter { it.identifier !in identifiers }
            return StickerResult.Success(Unit)
        }

        override suspend fun renamePack(identifier: String, newName: String): StickerResult<Unit> {
            packsFlow.value = packsFlow.value.map {
                if (it.identifier == identifier) it.copy(name = newName) else it
            }
            return StickerResult.Success(Unit)
        }

        override suspend fun deleteStickerFromPack(packIdentifier: String, stickerId: String): StickerResult<Unit> {
            packsFlow.value = packsFlow.value.map { pack ->
                if (pack.identifier == packIdentifier) {
                    pack.copy(stickers = pack.stickers.filter { it.id != stickerId })
                } else pack
            }
            return StickerResult.Success(Unit)
        }

        override suspend fun addEmotesToPack(packIdentifier: String, emotes: List<SevenTvEmote>): StickerResult<Unit> =
            StickerResult.Success(Unit)

        override suspend fun updateStickerEmojis(stickerId: String, emojis: List<String>): StickerResult<Unit> =
            StickerResult.Success(Unit)

        override suspend fun updatePackWhitelisted(identifier: String, isWhitelisted: Boolean): StickerResult<Unit> {
            packsFlow.value = packsFlow.value.map {
                if (it.identifier == identifier) it.copy(isWhitelisted = isWhitelisted) else it
            }
            return StickerResult.Success(Unit)
        }

        override suspend fun syncWhitelistStatus(context: Context, packIdentifier: String): StickerResult<Boolean> {
            lastSyncedIdentifier = packIdentifier
            return StickerResult.Success(true)
        }
    }
}

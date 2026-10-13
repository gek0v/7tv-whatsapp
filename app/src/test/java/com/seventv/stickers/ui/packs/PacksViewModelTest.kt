package com.seventv.stickers.ui.packs

import android.content.Context
import android.content.pm.PackageInfo
import java.io.File
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
    fun addPackToWhatsApp_whenFewerThan3Stickers_showsFeedbackMessage() {
        val packageManager = shadowOf(context.packageManager)
        packageManager.installPackage(
            PackageInfo().apply { packageName = WhatsAppConstants.PACKAGE_WHATSAPP }
        )

        val pack = createSamplePack("pack_small", "Small").copy(stickers = emptyList())
        viewModel.onAddPackToWhatsApp(context, pack)

        val state = viewModel.uiState.value
        assertNull(state.exportEvent)
        assertEquals("WhatsApp requiere al menos 3 stickers por paquete.", state.userFeedbackMessage)
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

    @Test
    fun openMergeDialog_whenLessThanTwoPacksSelected_showsFeedbackMessage() = runTest(testDispatcher) {
        val pack1 = createSamplePack("pack_1", "Uno")
        fakeStickerPackRepository.packsFlow.value = listOf(pack1)
        advanceUntilIdle()

        viewModel.onToggleSelectionMode()
        viewModel.onTogglePackSelection("pack_1")

        viewModel.onOpenMergeDialog()
        assertFalse(viewModel.uiState.value.isMergeDialogOpen)
        assertEquals("Selecciona al menos 2 paquetes para combinarlos.", viewModel.uiState.value.userFeedbackMessage)
    }

    @Test
    fun openMergeDialog_whenMixedStaticAndAnimated_showsFeedbackMessage() = runTest(testDispatcher) {
        val pack1 = createSamplePack("pack_1", "Estático", isAnimated = false)
        val pack2 = createSamplePack("pack_2", "Animado", isAnimated = true)
        fakeStickerPackRepository.packsFlow.value = listOf(pack1, pack2)
        advanceUntilIdle()

        viewModel.onToggleSelectionMode()
        viewModel.onTogglePackSelection("pack_1")
        viewModel.onTogglePackSelection("pack_2")

        viewModel.onOpenMergeDialog()
        assertFalse(viewModel.uiState.value.isMergeDialogOpen)
        assertEquals("No se pueden combinar paquetes animados y estáticos juntos.", viewModel.uiState.value.userFeedbackMessage)
    }

    @Test
    fun mergePacksFlow_whenValid_callsRepositoryAndClearsSelection() = runTest(testDispatcher) {
        val pack1 = createSamplePack("pack_1", "Uno")
        val pack2 = createSamplePack("pack_2", "Dos")
        fakeStickerPackRepository.packsFlow.value = listOf(pack1, pack2)
        advanceUntilIdle()

        viewModel.onToggleSelectionMode()
        viewModel.onTogglePackSelection("pack_1")
        viewModel.onTogglePackSelection("pack_2")

        viewModel.onOpenMergeDialog()
        assertTrue(viewModel.uiState.value.isMergeDialogOpen)

        viewModel.onConfirmMergePacks("Pack Combinado")
        advanceUntilIdle()

        assertEquals(listOf("pack_1", "pack_2"), fakeStickerPackRepository.lastMergedSourceIdentifiers)
        assertEquals("Pack Combinado", fakeStickerPackRepository.lastMergedBaseName)
        assertFalse(viewModel.uiState.value.isMergeDialogOpen)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedPackIds.isEmpty())
        assertTrue(viewModel.uiState.value.userFeedbackMessage?.contains("Paquetes combinados con éxito") == true)
    }

    @Test
    fun duplicatePackFlow() = runTest(testDispatcher) {
        val pack = createSamplePack("pack_1", "Original")
        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        advanceUntilIdle()

        viewModel.onOpenDuplicateDialog(pack)
        assertTrue(viewModel.uiState.value.isDuplicateDialogOpen)
        assertEquals(pack, viewModel.uiState.value.packToDuplicate)

        viewModel.onConfirmDuplicatePack("Original (Copia)")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDuplicateDialogOpen)
        assertNull(viewModel.uiState.value.packToDuplicate)
        assertTrue(viewModel.uiState.value.userFeedbackMessage?.contains("Original (Copia)") == true)
    }

    @Test
    fun updateStickerEmojisFlow() = runTest(testDispatcher) {
        val pack = createSamplePack("pack_1", "Original")
        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        advanceUntilIdle()

        viewModel.onSelectPackDetail("pack_1")
        viewModel.onUpdateStickerEmojis("pack_1", "s_1", listOf("💬", "🔥"))
        advanceUntilIdle()

        assertEquals("s_1", fakeStickerPackRepository.lastUpdatedStickerId)
        assertEquals(listOf("💬", "🔥"), fakeStickerPackRepository.lastUpdatedEmojis)
    }

    @Test
    fun setStickerAsTrayIconShowsFeedback() = runTest(testDispatcher) {
        val pack = createSamplePack("pack_1", "Original")
        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        advanceUntilIdle()

        viewModel.onSetStickerAsTrayIcon("pack_1", "s_1")
        advanceUntilIdle()

        assertEquals("Icono del pack actualizado", viewModel.uiState.value.userFeedbackMessage)
    }

    @Test
    fun selectPackDetail_inspectsPackAndPopulatesStickerIssues() = runTest(testDispatcher) {
        val pack = createSamplePack("pack_invalid", "Invalid Pack")
        // Corrupt one sticker file: empty file (0 bytes)
        val badFile = File(pack.stickers[0].filePath)
        badFile.writeBytes(ByteArray(0))

        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        advanceUntilIdle()

        viewModel.onSelectPackDetail("pack_invalid")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("pack_invalid", state.selectedPackDetail?.identifier)
        assertTrue("Expected stickerIssues to contain bad sticker", state.stickerIssues.containsKey("s_1"))
        assertTrue(state.stickerIssues["s_1"]?.reason?.contains("0 bytes") == true || state.stickerIssues["s_1"]?.reason?.contains("vacío") == true)
    }

    @Test
    fun addPackToWhatsApp_whenPackHasInvalidSticker_opensDiagnosticDialog() = runTest(testDispatcher) {
        val packageManager = shadowOf(context.packageManager)
        packageManager.installPackage(
            PackageInfo().apply { packageName = WhatsAppConstants.PACKAGE_WHATSAPP }
        )

        val pack = createSamplePack("pack_bad", "Bad Pack")
        // Corrupt first sticker: write 0 bytes
        File(pack.stickers[0].filePath).writeBytes(ByteArray(0))

        viewModel.onAddPackToWhatsApp(context, pack)

        val state = viewModel.uiState.value
        assertNull("Export intent should not be launched for invalid pack", state.exportEvent)
        assertTrue("Diagnostic dialog should open", state.isDiagnosticDialogOpen)
        assertTrue(state.stickerIssues.containsKey("s_1"))
    }

    @Test
    fun deleteInvalidStickers_deletesAllProblematicStickersFromPack() = runTest(testDispatcher) {
        val pack = createSamplePack("pack_to_clean", "Clean Pack")
        // Make s_1 invalid (0 bytes)
        File(pack.stickers[0].filePath).writeBytes(ByteArray(0))

        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        advanceUntilIdle()

        viewModel.onSelectPackDetail("pack_to_clean")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.stickerIssues.containsKey("s_1"))

        viewModel.onDeleteInvalidStickers("pack_to_clean")
        advanceUntilIdle()

        val updatedPack = fakeStickerPackRepository.packsFlow.value.find { it.identifier == "pack_to_clean" }
        assertNotNull(updatedPack)
        assertFalse("s_1 should have been deleted", updatedPack!!.stickers.any { it.id == "s_1" })
        assertEquals(2, updatedPack.stickers.size)
        assertFalse(viewModel.uiState.value.isDiagnosticDialogOpen)
    }

    @Test
    fun onWhatsAppPreviewValidationFailed_opensDiagnosticDialog() = runTest(testDispatcher) {
        val pack = createSamplePack("pack_failed", "Failed Pack")
        File(pack.stickers[0].filePath).writeBytes(ByteArray(0))

        fakeStickerPackRepository.packsFlow.value = listOf(pack)
        advanceUntilIdle()

        viewModel.onSelectPackDetail("pack_failed")
        advanceUntilIdle()

        viewModel.onWhatsAppPreviewValidationFailed("pack_failed")

        val state = viewModel.uiState.value
        assertTrue(state.isDiagnosticDialogOpen)
        assertTrue(state.stickerIssues.containsKey("s_1"))
    }

    private fun createMinimalWebp(isAnimated: Boolean): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val body = java.io.ByteArrayOutputStream()
        body.write("WEBP".toByteArray(Charsets.US_ASCII))
        body.write("VP8X".toByteArray(Charsets.US_ASCII))
        body.write(byteArrayOf(10, 0, 0, 0))
        body.write(if (isAnimated) 0x02 else 0x00)
        body.write(byteArrayOf(0, 0, 0))
        val w511 = 511
        body.write(w511 and 0xFF)
        body.write((w511 shr 8) and 0xFF)
        body.write((w511 shr 16) and 0xFF)
        body.write(w511 and 0xFF)
        body.write((w511 shr 8) and 0xFF)
        body.write((w511 shr 16) and 0xFF)
        if (isAnimated) {
            body.write("ANIM".toByteArray(Charsets.US_ASCII))
            body.write(byteArrayOf(6, 0, 0, 0))
            body.write(byteArrayOf(0, 0, 0, 0, 0, 0))
            for (i in 0 until 2) {
                body.write("ANMF".toByteArray(Charsets.US_ASCII))
                body.write(byteArrayOf(16, 0, 0, 0))
                body.write(byteArrayOf(0, 0, 0))
                body.write(byteArrayOf(0, 0, 0))
                body.write(w511 and 0xFF)
                body.write((w511 shr 8) and 0xFF)
                body.write((w511 shr 16) and 0xFF)
                body.write(w511 and 0xFF)
                body.write((w511 shr 8) and 0xFF)
                body.write((w511 shr 16) and 0xFF)
                body.write(100)
                body.write(0)
                body.write(0)
                body.write(0)
            }
        }
        val bodyBytes = body.toByteArray()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        val riffLen = bodyBytes.size
        out.write(riffLen and 0xFF)
        out.write((riffLen shr 8) and 0xFF)
        out.write((riffLen shr 16) and 0xFF)
        out.write((riffLen shr 24) and 0xFF)
        out.write(bodyBytes)
        return out.toByteArray()
    }

    @Test
    fun onCheckPackUpdatesWhenNoOriginSetReportsError() = runTest(testDispatcher) {
        val pack = createSamplePack("no-origin-pack", "No Origin")
        viewModel.onCheckPackUpdates(pack)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.syncState is PackSyncState.Error)
        assertEquals("Este paquete no está vinculado a un conjunto de emotes de 7TV.", (state.syncState as PackSyncState.Error).message)
    }

    @Test
    fun onCheckPackUpdatesWhenUpToDateReportsUpToDate() = runTest(testDispatcher) {
        val pack = createSamplePack("sync-pack", "Sync Pack", originSetId = "set-123")
        fakeStickerPackRepository.checkForUpdatesHandler = {
            StickerResult.Success(emptyList())
        }

        viewModel.onCheckPackUpdates(pack)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.syncState is PackSyncState.UpToDate)
    }

    @Test
    fun onCheckPackUpdatesWhenNewEmotesFoundSetsNewEmotesAvailableState() = runTest(testDispatcher) {
        val pack = createSamplePack("sync-pack", "Sync Pack", originSetId = "set-123", originStreamerName = "ibai")
        val emote1 = SevenTvEmote(id = "e1", name = "Emote1", isAnimated = false, isZeroWidth = false, isNsfw = false, bestAssetUrl = "url", availableScales = listOf("1x"))
        val emote2 = SevenTvEmote(id = "e2", name = "Emote2", isAnimated = false, isZeroWidth = false, isNsfw = false, bestAssetUrl = "url", availableScales = listOf("1x"))

        fakeStickerPackRepository.checkForUpdatesHandler = {
            StickerResult.Success(listOf(emote1, emote2))
        }

        viewModel.onCheckPackUpdates(pack)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.syncState is PackSyncState.NewEmotesAvailable)
        val available = state.syncState as PackSyncState.NewEmotesAvailable
        assertEquals(2, available.newEmotes.size)
        assertEquals("ibai", available.streamerName)
        assertEquals(setOf("e1", "e2"), state.selectedSyncEmoteIds)
    }

    @Test
    fun onApplySyncUpdatesCallsAddEmotesToPackAndShowsFeedback() = runTest(testDispatcher) {
        val pack = createSamplePack("sync-pack", "Sync Pack", originSetId = "set-123", originStreamerName = "ibai")
        val emote1 = SevenTvEmote(id = "e1", name = "Emote1", isAnimated = false, isZeroWidth = false, isNsfw = false, bestAssetUrl = "url", availableScales = listOf("1x"))

        fakeStickerPackRepository.checkForUpdatesHandler = {
            StickerResult.Success(listOf(emote1))
        }

        viewModel.onCheckPackUpdates(pack)
        advanceUntilIdle()

        viewModel.onApplySyncUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.syncState is PackSyncState.Idle)
        assertFalse(state.isApplyingSync)
        assertEquals("¡Se han añadido 1 nuevos stickers al pack!", state.userFeedbackMessage)
    }

    private fun createSamplePack(
        id: String,
        name: String,
        isWhitelisted: Boolean = false,
        isAnimated: Boolean = false,
        originSetId: String? = null,
        originStreamerName: String? = null
    ): StickerPack {
        val packDir = File(context.cacheDir, "test_packs/$id").apply { mkdirs() }
        val webpBytes = createMinimalWebp(isAnimated)
        val stickers = (1..3).map { i ->
            val file = File(packDir, "sticker_$i.webp").apply {
                writeBytes(webpBytes)
            }
            PackSticker(
                id = "s_$i",
                packIdentifier = id,
                emoteId = "e_$i",
                emoteName = "pepe$i",
                filePath = file.absolutePath,
                emojis = listOf("💬"),
                fileLength = file.length(),
                sortOrder = i - 1
            )
        }
        return StickerPack(
            identifier = id,
            name = name,
            publisher = "7TV Stickers",
            trayImagePath = "/data/pack/$id/tray.png",
            isAnimated = isAnimated,
            isWhitelisted = isWhitelisted,
            stickers = stickers,
            originSetId = originSetId,
            originStreamerName = originStreamerName
        )
    }

    private class FakePacksStickerPackRepository : StickerPackRepository {
        val packsFlow = MutableStateFlow<List<StickerPack>>(emptyList())
        var lastDeletedIdentifier: String? = null
        var lastSyncedIdentifier: String? = null
        var checkForUpdatesHandler: (suspend (String) -> StickerResult<List<SevenTvEmote>>)? = null

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
            onProgress: ((current: Int, total: Int) -> Unit)?,
            originSetId: String?,
            originStreamerName: String?
        ): StickerResult<List<StickerPack>> = StickerResult.Success(emptyList())

        override suspend fun checkForPackUpdates(packIdentifier: String): StickerResult<List<SevenTvEmote>> {
            return checkForUpdatesHandler?.invoke(packIdentifier) ?: StickerResult.Success(emptyList())
        }

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

        var lastMergedSourceIdentifiers: List<String>? = null
        var lastMergedBaseName: String? = null
        override suspend fun mergePacks(
            sourcePackIdentifiers: List<String>,
            basePackName: String
        ): StickerResult<List<StickerPack>> {
            lastMergedSourceIdentifiers = sourcePackIdentifiers
            lastMergedBaseName = basePackName
            val remaining = packsFlow.value.filter { it.identifier !in sourcePackIdentifiers }
            val newPack = StickerPack(
                identifier = "merged_1",
                name = basePackName,
                publisher = "7TV Stickers",
                trayImagePath = "/data/pack/merged_1/tray.webp",
                isAnimated = false,
                isWhitelisted = false,
                stickers = emptyList()
            )
            packsFlow.value = remaining + listOf(newPack)
            return StickerResult.Success(listOf(newPack))
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

        var lastUpdatedStickerId: String? = null
        var lastUpdatedEmojis: List<String>? = null
        override suspend fun updateStickerEmojis(stickerId: String, emojis: List<String>): StickerResult<Unit> {
            lastUpdatedStickerId = stickerId
            lastUpdatedEmojis = emojis
            return StickerResult.Success(Unit)
        }

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

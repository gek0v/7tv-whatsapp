package com.seventv.stickers.ui.components

import android.content.Context
import android.os.Build
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.seventv.stickers.core.domain.model.PackSticker
import com.seventv.stickers.core.domain.model.StickerPack
import com.seventv.stickers.core.network.model.SevenTvEmote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ComponentsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun coilSetup_producesConfiguredImageLoader() {
        val imageLoader = CoilSetup.createImageLoader(context)
        assertNotNull("ImageLoader must not be null", imageLoader)

        val factories = imageLoader.components.decoderFactories
        assertTrue("Decoder factories must not be empty", factories.isNotEmpty())

        if (Build.VERSION.SDK_INT >= 28) {
            assertTrue(
                "ImageDecoderDecoder.Factory must be present on API 28+",
                factories.any { it is ImageDecoderDecoder.Factory }
            )
        }
        assertTrue(
            "GifDecoder.Factory must be present in decoder factories",
            factories.any { it is GifDecoder.Factory }
        )

        val directLoader = createCoilImageLoader(context)
        assertNotNull("Top-level createCoilImageLoader must produce non-null ImageLoader", directLoader)
    }

    @Test
    fun activeDraftBanner_validatesStickerThresholdLogic() {
        // Less than 3 stickers: cannot save pack, shows minimum prompt
        for (count in 0..2) {
            assertFalse("Draft count $count should not be eligible to save pack", ActiveDraftBannerDefaults.canSave(count))
            assertEquals("(Mínimo 3 stickers)", ActiveDraftBannerDefaults.subtitleText(count))
        }

        // Between 3 and 30 stickers: can save pack, shows ready prompt
        for (count in 3..30) {
            assertTrue("Draft count $count should be eligible to save pack", ActiveDraftBannerDefaults.canSave(count))
            assertEquals("Listo para crear pack", ActiveDraftBannerDefaults.subtitleText(count))
        }

        // Greater than 30 stickers: exceeds capacity, cannot save
        assertFalse("Draft count 31 should not be eligible to save pack", ActiveDraftBannerDefaults.canSave(31))
        assertEquals("Listo para crear pack", ActiveDraftBannerDefaults.subtitleText(31))

        // Custom max limit evaluation
        assertTrue(ActiveDraftBannerDefaults.canSave(5, maxCount = 10))
        assertFalse(ActiveDraftBannerDefaults.canSave(11, maxCount = 10))

        // Counter text formatting
        assertEquals("0/30 stickers", ActiveDraftBannerDefaults.counterText(0))
        assertEquals("3/30 stickers", ActiveDraftBannerDefaults.counterText(3))
        assertEquals("30/30 stickers", ActiveDraftBannerDefaults.counterText(30))
        assertEquals("5/15 stickers", ActiveDraftBannerDefaults.counterText(5, maxCount = 15))
    }

    @Test
    fun whatsAppAddButton_validatesWhitelistedAndLoadingFlagBehaviors() {
        // Case 1: Not whitelisted, not loading -> button to add, clickable, no checkmark
        val unwhitelistedIdle = WhatsAppAddButtonDefaults.evaluateState(isWhitelisted = false, isLoading = false)
        assertEquals("Añadir a WhatsApp", unwhitelistedIdle.buttonText)
        assertTrue(unwhitelistedIdle.isClickable)
        assertFalse(unwhitelistedIdle.showProgress)
        assertFalse(unwhitelistedIdle.showCheckmark)

        // Case 2: Whitelisted, not loading -> already added, clickable/interactive, checkmark shown
        val whitelistedIdle = WhatsAppAddButtonDefaults.evaluateState(isWhitelisted = true, isLoading = false)
        assertEquals("Añadido a WhatsApp", whitelistedIdle.buttonText)
        assertTrue(whitelistedIdle.isClickable)
        assertFalse(whitelistedIdle.showProgress)
        assertTrue(whitelistedIdle.showCheckmark)

        // Case 3: In loading state -> disabled for clicks, progress indicator visible
        val loadingState = WhatsAppAddButtonDefaults.evaluateState(isWhitelisted = false, isLoading = true)
        assertEquals("Añadir a WhatsApp", loadingState.buttonText)
        assertFalse(loadingState.isClickable)
        assertTrue(loadingState.showProgress)
        assertFalse(loadingState.showCheckmark)

        // Direct helper tests
        assertEquals("Añadir a WhatsApp", WhatsAppAddButtonDefaults.buttonText(isWhitelisted = false))
        assertEquals("Añadido a WhatsApp", WhatsAppAddButtonDefaults.buttonText(isWhitelisted = true))
    }

    @Test
    fun emoteCard_evaluatesPreviewUrlAndBadgeText() {
        val animatedEmote = SevenTvEmote(
            id = "emote_1",
            name = "catJAM",
            isAnimated = true,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "https://cdn.7tv.app/emote/emote_1/4x.webp",
            availableScales = listOf("1x", "2x", "3x", "4x")
        )
        val staticEmote = SevenTvEmote(
            id = "emote_2",
            name = "OMEGALUL",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "https://cdn.7tv.app/emote/emote_2/4x.webp",
            availableScales = listOf("1x", "2x", "3x", "4x")
        )

        // Verify previewUrl convenience property
        assertEquals("https://cdn.7tv.app/emote/emote_1/4x.webp", animatedEmote.previewUrl)
        assertEquals("https://cdn.7tv.app/emote/emote_2/4x.webp", staticEmote.previewUrl)

        // Verify badge helper
        assertEquals("ANIM", EmoteCardDefaults.badgeText(isAnimated = true))
        assertEquals("STATIC", EmoteCardDefaults.badgeText(isAnimated = false))
    }

    @Test
    fun sevenTvSearchBarDefaults_providesExpectedFiltersAndQueryBehaviors() {
        val expectedFilters = listOf("Todos", "Animados", "Estáticos", "Populares hoy")
        assertEquals(expectedFilters, SevenTvSearchBarDefaults.filters)
        assertEquals(4, SevenTvSearchBarDefaults.filters.size)
        assertTrue(SevenTvSearchBarDefaults.filters.contains("Todos"))
        assertTrue(SevenTvSearchBarDefaults.filters.contains("Animados"))
        assertTrue(SevenTvSearchBarDefaults.filters.contains("Estáticos"))
        assertTrue(SevenTvSearchBarDefaults.filters.contains("Populares hoy"))

        // Search clear button visibility logic
        assertFalse("Empty query should not show clear button", SevenTvSearchBarDefaults.shouldShowClear(""))
        assertTrue("Non-empty query should show clear button", SevenTvSearchBarDefaults.shouldShowClear("pepe"))

        // Direct 7TV Emote URL extraction
        val fullUrl = "https://7tv.app/emotes/60ccf4479f59298d1a7b4698"
        val nakedUrl = "7tv.app/emotes/60ccf4479f59298d1a7b4698"
        val nonUrl = "catJAM"

        assertEquals("60ccf4479f59298d1a7b4698", SevenTvSearchBarDefaults.extractEmoteIdFromUrl(fullUrl))
        assertEquals("60ccf4479f59298d1a7b4698", SevenTvSearchBarDefaults.extractEmoteIdFromUrl(nakedUrl))
        org.junit.Assert.assertNull(SevenTvSearchBarDefaults.extractEmoteIdFromUrl(nonUrl))

        assertTrue(SevenTvSearchBarDefaults.isDirectEmoteUrl(fullUrl))
        assertTrue(SevenTvSearchBarDefaults.isDirectEmoteUrl(nakedUrl))
        assertFalse(SevenTvSearchBarDefaults.isDirectEmoteUrl(nonUrl))
    }

    @Test
    fun emoteCardDefaults_badgeConstantsAndHelpers() {
        assertEquals("ANIM", EmoteCardDefaults.ANIMATED_BADGE)
        assertEquals("STATIC", EmoteCardDefaults.STATIC_BADGE)
        assertEquals("ANIM", EmoteCardDefaults.badgeText(isAnimated = true))
        assertEquals("STATIC", EmoteCardDefaults.badgeText(isAnimated = false))
    }

    @Test
    fun dockedDraftPillDefaults_evaluatesDraftHealthAndStatus() {
        // Less than 3 stickers: INSUFFICIENT
        for (count in 0..2) {
            assertEquals(DraftStatus.INSUFFICIENT, DockedDraftPillDefaults.draftStatus(count))
            assertEquals("Mínimo 3 requeridos", DockedDraftPillDefaults.statusMessage(count))
            assertEquals(com.seventv.stickers.ui.theme.ErrorRed, DockedDraftPillDefaults.statusColor(count))
            assertFalse("Draft count $count should not be valid for pack export", DockedDraftPillDefaults.isValid(count))
        }

        // 3 to 30 stickers: READY
        for (count in 3..30) {
            assertEquals(DraftStatus.READY, DockedDraftPillDefaults.draftStatus(count))
            assertEquals("Listo para WhatsApp", DockedDraftPillDefaults.statusMessage(count))
            assertEquals(com.seventv.stickers.ui.theme.WhatsAppGreen, DockedDraftPillDefaults.statusColor(count))
            assertTrue("Draft count $count should be valid for pack export", DockedDraftPillDefaults.isValid(count))
        }

        // More than 30 stickers: EXCEEDED
        assertEquals(DraftStatus.EXCEEDED, DockedDraftPillDefaults.draftStatus(31))
        assertEquals("Límite excedido", DockedDraftPillDefaults.statusMessage(31))
        assertEquals(DockedDraftPillDefaults.WarningOrange, DockedDraftPillDefaults.statusColor(31))
        assertFalse("Draft count 31 should not be valid for pack export", DockedDraftPillDefaults.isValid(31))

        // Custom limits
        assertEquals(DraftStatus.READY, DockedDraftPillDefaults.draftStatus(10, minCount = 5, maxCount = 10))
        assertEquals(DraftStatus.EXCEEDED, DockedDraftPillDefaults.draftStatus(11, minCount = 5, maxCount = 10))
        assertEquals(DraftStatus.INSUFFICIENT, DockedDraftPillDefaults.draftStatus(4, minCount = 5, maxCount = 10))

        // Counter text format: "X / 30 stickers"
        assertEquals("0 / 30 stickers", DockedDraftPillDefaults.counterText(0))
        assertEquals("3 / 30 stickers", DockedDraftPillDefaults.counterText(3))
        assertEquals("30 / 30 stickers", DockedDraftPillDefaults.counterText(30))
        assertEquals("5 / 15 stickers", DockedDraftPillDefaults.counterText(5, maxCount = 15))
    }

    @Test
    fun draftBottomSheetDefaults_validatesThresholdAndHelperTexts() {
        // Can create: count in 3..30 AND non-blank packName
        assertFalse(DraftBottomSheetDefaults.canCreate(count = 2, packName = "My Pack"))
        assertFalse(DraftBottomSheetDefaults.canCreate(count = 3, packName = ""))
        assertFalse(DraftBottomSheetDefaults.canCreate(count = 3, packName = "   "))
        assertTrue(DraftBottomSheetDefaults.canCreate(count = 3, packName = "My Pack"))
        assertTrue(DraftBottomSheetDefaults.canCreate(count = 30, packName = "My Pack"))
        assertFalse(DraftBottomSheetDefaults.canCreate(count = 31, packName = "My Pack"))

        // Helper text evaluation
        assertEquals("Añade al menos 3 stickers para exportar a WhatsApp", DraftBottomSheetDefaults.helperText(0))
        assertEquals("Añade al menos 3 stickers para exportar a WhatsApp", DraftBottomSheetDefaults.helperText(2))
        org.junit.Assert.assertNull(DraftBottomSheetDefaults.helperText(3))
        org.junit.Assert.assertNull(DraftBottomSheetDefaults.helperText(30))
        assertEquals("El límite máximo es de 30 stickers por paquete", DraftBottomSheetDefaults.helperText(31))
    }

    @Test
    fun packHeroCardDefaults_validatesSyncStatusAndLabels() {
        // Sync status determination
        assertEquals(
            PackSyncStatus.SYNCED,
            PackHeroCardDefaults.syncStatus(isWhitelisted = true, hasPendingChanges = false)
        )
        assertEquals(
            PackSyncStatus.PENDING,
            PackHeroCardDefaults.syncStatus(isWhitelisted = true, hasPendingChanges = true)
        )
        assertEquals(
            PackSyncStatus.PENDING,
            PackHeroCardDefaults.syncStatus(isWhitelisted = false, hasPendingChanges = true)
        )
        assertEquals(
            PackSyncStatus.NOT_EXPORTED,
            PackHeroCardDefaults.syncStatus(isWhitelisted = false, hasPendingChanges = false)
        )

        // Labels
        assertEquals("Sincronizado", PackHeroCardDefaults.statusLabel(PackSyncStatus.SYNCED))
        assertEquals("Pendiente", PackHeroCardDefaults.statusLabel(PackSyncStatus.PENDING))
        assertEquals("No exportado", PackHeroCardDefaults.statusLabel(PackSyncStatus.NOT_EXPORTED))

        // Emojis / Chips
        assertEquals("🟢", PackHeroCardDefaults.statusEmoji(PackSyncStatus.SYNCED))
        assertEquals("🟡", PackHeroCardDefaults.statusEmoji(PackSyncStatus.PENDING))
        assertEquals("⚪", PackHeroCardDefaults.statusEmoji(PackSyncStatus.NOT_EXPORTED))

        assertEquals("🟢 Sincronizado", PackHeroCardDefaults.statusChipText(PackSyncStatus.SYNCED))
        assertEquals("🟡 Pendiente", PackHeroCardDefaults.statusChipText(PackSyncStatus.PENDING))
        assertEquals("⚪ No exportado", PackHeroCardDefaults.statusChipText(PackSyncStatus.NOT_EXPORTED))

        // Counter text format
        assertEquals("5 / 30", PackHeroCardDefaults.counterText(5))
        assertEquals("30 / 30", PackHeroCardDefaults.counterText(30))
        assertEquals("12 / 20", PackHeroCardDefaults.counterText(12, maxCount = 20))
    }

    @Test
    fun chatSimulatorDefaults_providesExactSpecTokensAndHelpers() {
        val modes = ChatSimulatorMode.values()
        assertTrue(modes.contains(ChatSimulatorMode.WHATSAPP_DARK))
        assertTrue(modes.contains(ChatSimulatorMode.WHATSAPP_LIGHT))
        assertTrue(modes.contains(ChatSimulatorMode.CHECKERBOARD))

        // Exact spec tokens
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0B141A), ChatSimulatorDefaults.DarkChat)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF005C4B), ChatSimulatorDefaults.DarkBubble)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFEFEAE2), ChatSimulatorDefaults.LightChat)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFD9FDD3), ChatSimulatorDefaults.LightBubble)

        // Background color helper
        assertEquals(ChatSimulatorDefaults.DarkChat, ChatSimulatorDefaults.backgroundColor(ChatSimulatorMode.WHATSAPP_DARK))
        assertEquals(ChatSimulatorDefaults.LightChat, ChatSimulatorDefaults.backgroundColor(ChatSimulatorMode.WHATSAPP_LIGHT))

        // Bubble color helper
        assertEquals(ChatSimulatorDefaults.DarkBubble, ChatSimulatorDefaults.bubbleColor(ChatSimulatorMode.WHATSAPP_DARK))
        assertEquals(ChatSimulatorDefaults.LightBubble, ChatSimulatorDefaults.bubbleColor(ChatSimulatorMode.WHATSAPP_LIGHT))
        assertEquals(androidx.compose.ui.graphics.Color.Transparent, ChatSimulatorDefaults.bubbleColor(ChatSimulatorMode.CHECKERBOARD))
    }

    @Test
    fun stickerInspectorSheetDefaults_calculatesMetricsAndValidations() {
        // Size limits: Static <= 100KB (102400 bytes), Animated <= 500KB (512000 bytes)
        assertFalse(StickerInspectorSheetDefaults.isFileSizeExceeded(100 * 1024L, isAnimated = false))
        assertTrue(StickerInspectorSheetDefaults.isFileSizeExceeded(100 * 1024L + 1, isAnimated = false))

        assertFalse(StickerInspectorSheetDefaults.isFileSizeExceeded(500 * 1024L, isAnimated = true))
        assertTrue(StickerInspectorSheetDefaults.isFileSizeExceeded(500 * 1024L + 1, isAnimated = true))

        // Formatted file size string
        assertEquals("50.0 KB", StickerInspectorSheetDefaults.formatFileSize(50 * 1024L))
        assertEquals("500.0 KB", StickerInspectorSheetDefaults.formatFileSize(500 * 1024L))

        // Emoji capacity limit: max 3 emojis
        assertTrue(StickerInspectorSheetDefaults.canAddEmoji(emptyList()))
        assertTrue(StickerInspectorSheetDefaults.canAddEmoji(listOf("👍", "🔥")))
        assertFalse(StickerInspectorSheetDefaults.canAddEmoji(listOf("👍", "🔥", "❤️")))

        // Metric string formats
        assertEquals("2.5s", StickerInspectorSheetDefaults.formatDuration(2.5f))
        assertEquals("30 FPS", StickerInspectorSheetDefaults.formatFps(30))
    }

    @Test
    fun addToExistingPackDialogDefaults_validatesCompatibilityAndReasons() {
        val staticEmote1 = SevenTvEmote(
            id = "1",
            name = "kekw",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "",
            availableScales = emptyList()
        )
        val staticEmote2 = SevenTvEmote(
            id = "2",
            name = "lul",
            isAnimated = false,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "",
            availableScales = emptyList()
        )
        val animEmote = SevenTvEmote(
            id = "3",
            name = "catJAM",
            isAnimated = true,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "",
            availableScales = emptyList()
        )

        val staticPack = StickerPack(
            identifier = "static_1",
            name = "Static Pack",
            publisher = "7TV",
            trayImagePath = "tray.webp",
            isAnimated = false,
            stickers = List(25) {
                PackSticker("s_$it", "static_1", "e_$it", "name_$it", "path_$it", emptyList(), 1000L, it)
            }
        )

        val animPack = StickerPack(
            identifier = "anim_1",
            name = "Anim Pack",
            publisher = "7TV",
            trayImagePath = "tray.webp",
            isAnimated = true,
            stickers = List(29) {
                PackSticker("a_$it", "anim_1", "e_$it", "name_$it", "path_$it", emptyList(), 1000L, it)
            }
        )

        // 1. Static emotes to static pack with room (25 + 2 = 27 <= 30) -> compatible
        assertTrue(AddToExistingPackDialogDefaults.isPackCompatible(staticPack, listOf(staticEmote1, staticEmote2)))
        org.junit.Assert.assertNull(AddToExistingPackDialogDefaults.incompatibilityReason(staticPack, listOf(staticEmote1, staticEmote2)))

        // 2. Animated emote to static pack -> incompatible format
        assertFalse(AddToExistingPackDialogDefaults.isPackCompatible(staticPack, listOf(animEmote)))
        assertEquals(
            "Incompatible (el pack es estático)",
            AddToExistingPackDialogDefaults.incompatibilityReason(staticPack, listOf(animEmote))
        )

        // 3. Static emote to animated pack -> incompatible format
        assertFalse(AddToExistingPackDialogDefaults.isPackCompatible(animPack, listOf(staticEmote1)))
        assertEquals(
            "Incompatible (el pack es animado)",
            AddToExistingPackDialogDefaults.incompatibilityReason(animPack, listOf(staticEmote1))
        )

        // 4. Mixed emotes (both animated & static) -> incompatible
        assertFalse(AddToExistingPackDialogDefaults.isPackCompatible(staticPack, listOf(staticEmote1, animEmote)))
        assertEquals(
            "Selección mixta: contiene emotes animados y estáticos",
            AddToExistingPackDialogDefaults.incompatibilityReason(staticPack, listOf(staticEmote1, animEmote))
        )

        // 5. Exceeding capacity: animPack has 29 stickers, adding 2 animated emotes -> 31 > 30
        val animEmote2 = SevenTvEmote(
            id = "4",
            name = "pepeD",
            isAnimated = true,
            isZeroWidth = false,
            isNsfw = false,
            bestAssetUrl = "",
            availableScales = emptyList()
        )
        assertFalse(AddToExistingPackDialogDefaults.isPackCompatible(animPack, listOf(animEmote, animEmote2)))
        assertEquals(
            "Supera el límite de 30 (quedan 1 espacios)",
            AddToExistingPackDialogDefaults.incompatibilityReason(animPack, listOf(animEmote, animEmote2))
        )

        // 6. Empty emotes list -> not compatible
        assertFalse(AddToExistingPackDialogDefaults.isPackCompatible(staticPack, emptyList()))
        org.junit.Assert.assertNull(AddToExistingPackDialogDefaults.incompatibilityReason(staticPack, emptyList()))
    }

    @Test
    fun emoteFullscreenViewerDefaults_validatesZoomAndBadgeCalculations() {
        // Zoom clamping
        assertEquals(1.0f, EmoteFullscreenViewerDefaults.clampZoom(0.5f), 0.001f)
        assertEquals(1.0f, EmoteFullscreenViewerDefaults.clampZoom(1.0f), 0.001f)
        assertEquals(2.5f, EmoteFullscreenViewerDefaults.clampZoom(2.5f), 0.001f)
        assertEquals(4.0f, EmoteFullscreenViewerDefaults.clampZoom(4.0f), 0.001f)
        assertEquals(4.0f, EmoteFullscreenViewerDefaults.clampZoom(5.5f), 0.001f)

        // Badge text
        assertEquals("ANIM", EmoteFullscreenViewerDefaults.badgeText(isAnimated = true))
        assertEquals("STATIC", EmoteFullscreenViewerDefaults.badgeText(isAnimated = false))

        // File size formatting
        assertEquals("128.0 KB", EmoteFullscreenViewerDefaults.formatFileSize(128 * 1024L))
        assertEquals("500.0 KB", EmoteFullscreenViewerDefaults.formatFileSize(500 * 1024L))
    }
}


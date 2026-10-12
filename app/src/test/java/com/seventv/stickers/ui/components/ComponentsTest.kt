package com.seventv.stickers.ui.components

import android.content.Context
import android.os.Build
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
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
}

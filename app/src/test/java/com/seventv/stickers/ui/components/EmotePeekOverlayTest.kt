package com.seventv.stickers.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotePeekOverlayTest {

    @Test
    fun defaults_matchSpecificationConstants() {
        assertEquals("ANIM WebP", EmotePeekOverlayDefaults.badgeText(isAnimated = true))
        assertEquals("STATIC", EmotePeekOverlayDefaults.badgeText(isAnimated = false))
        assertEquals(0.85f, EmotePeekOverlayDefaults.INITIAL_SCALE, 0.001f)
        assertEquals(1.0f, EmotePeekOverlayDefaults.TARGET_SCALE, 0.001f)
        assertEquals(140, EmotePeekOverlayDefaults.EXIT_DURATION_MS)
        assertEquals(16.dp, EmotePeekOverlayDefaults.BLUR_RADIUS)
        assertEquals(28.dp, EmotePeekOverlayDefaults.CARD_CORNER_RADIUS)
        assertEquals(256.dp, EmotePeekOverlayDefaults.PREVIEW_MAX_SIZE)
    }

    @Test
    fun calculateScale_interpolatesCorrectly() {
        assertEquals(0.85f, EmotePeekOverlayDefaults.calculateScale(0f), 0.001f)
        assertEquals(0.925f, EmotePeekOverlayDefaults.calculateScale(0.5f), 0.001f)
        assertEquals(1.0f, EmotePeekOverlayDefaults.calculateScale(1f), 0.001f)
    }

    @Test
    fun calculateEmergenceOffset_returnsZeroAtFullProgressOrMissingSource() {
        val screenCenter = Offset(500f, 1000f)
        assertEquals(
            Offset.Zero,
            EmotePeekOverlayDefaults.calculateEmergenceOffset(null, screenCenter, 0f)
        )
        assertEquals(
            Offset.Zero,
            EmotePeekOverlayDefaults.calculateEmergenceOffset(Offset(100f, 200f), screenCenter, 1f)
        )

        val originOffset = EmotePeekOverlayDefaults.calculateEmergenceOffset(
            sourceCenter = Offset(100f, 200f),
            containerCenter = screenCenter,
            progress = 0f
        )
        assertEquals(-400f * 0.35f, originOffset.x, 0.01f)
        assertEquals(-800f * 0.35f, originOffset.y, 0.01f)
    }

    @Test
    fun findHoveredActionIndex_detectsBoundsAndHitSlop() {
        val actionBounds = mapOf(
            0 to Rect(100f, 800f, 220f, 860f),
            1 to Rect(240f, 800f, 360f, 860f),
            2 to Rect(380f, 800f, 500f, 860f)
        )

        assertNull(EmotePeekOverlayDefaults.findHoveredActionIndex(null, actionBounds))
        assertNull(EmotePeekOverlayDefaults.findHoveredActionIndex(Offset(150f, 830f), emptyMap()))
        assertNull(EmotePeekOverlayDefaults.findHoveredActionIndex(Offset(50f, 50f), actionBounds))

        assertEquals(0, EmotePeekOverlayDefaults.findHoveredActionIndex(Offset(150f, 830f), actionBounds))
        assertEquals(1, EmotePeekOverlayDefaults.findHoveredActionIndex(Offset(300f, 830f), actionBounds))
        assertEquals(2, EmotePeekOverlayDefaults.findHoveredActionIndex(Offset(450f, 830f), actionBounds))

        // Outside exact rect, inside hitSlop = 15px
        assertNull(EmotePeekOverlayDefaults.findHoveredActionIndex(Offset(300f, 870f), actionBounds, hitSlopPx = 0f))
        assertEquals(1, EmotePeekOverlayDefaults.findHoveredActionIndex(Offset(300f, 870f), actionBounds, hitSlopPx = 15f))
    }

    @Test
    fun emotePeekState_managesLifecycleAndActionExecution() {
        val state = EmotePeekState()
        assertFalse(state.isActive)
        assertFalse(state.isVisible)
        assertNull(state.currentPayload)

        var draftClicked = 0
        var inspectClicked = 0
        var shareClicked = 0

        val payload = EmotePeekPayload(
            id = "emote_1",
            name = "KEKW",
            isAnimated = false,
            imageModel = "https://cdn.7tv.app/emote/1/4x.webp",
            placeholderCacheKey = CoilSetup.emotePreviewCacheKey("emote_1"),
            sourceLabel = "7TV • Explorar",
            actions = listOf(
                EmotePeekAction("draft", "Borrador", Icons.Default.Add, onClick = { draftClicked++ }),
                EmotePeekAction("inspect", "Inspeccionar", Icons.Default.ZoomIn, onClick = { inspectClicked++ }),
                EmotePeekAction("share", "Compartir", Icons.Default.Share, onClick = { shareClicked++ })
            )
        )

        state.show(payload)
        assertTrue(state.isActive)
        assertTrue(state.isVisible)
        assertEquals(payload, state.currentPayload)

        state.updatePointer(Offset(300f, 830f))
        assertEquals(Offset(300f, 830f), state.pointerPositionInRoot)

        val recordedHaptics = mutableListOf<HapticFeedbackType>()
        val fakeHaptic = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                recordedHaptics.add(hapticFeedbackType)
            }
        }

        // Release without hovered action -> dismisses without executing actions
        state.onRelease(fakeHaptic)
        assertFalse(state.isActive)
        assertFalse(state.isVisible)
        assertEquals(0, draftClicked)
        assertEquals(0, inspectClicked)
        assertEquals(0, shareClicked)
        assertTrue(recordedHaptics.isEmpty())

        // Re-open, hover index 1 ("Inspeccionar"), and release -> executes action 1 & triggers haptic
        state.show(payload)
        state.hoveredActionIndex = 1
        state.onRelease(fakeHaptic)
        assertEquals(0, draftClicked)
        assertEquals(1, inspectClicked)
        assertEquals(0, shareClicked)
        assertEquals(listOf(HapticFeedbackType.LongPress), recordedHaptics)
        assertFalse(state.isActive)

        state.clearPayload()
        assertNull(state.currentPayload)
    }


    @Test
    fun emotePeekState_executesShareAction_onReleaseUsingFreshBoundsSnapshot() {
        val state = EmotePeekState()
        var draftClicked = 0
        var inspectClicked = 0
        var shareClicked = 0

        val payload = EmotePeekPayload(
            id = "emote_share",
            name = "catJAM",
            isAnimated = true,
            imageModel = "https://cdn.7tv.app/emote/2/4x.webp",
            actions = listOf(
                EmotePeekAction("draft", "En borrador", Icons.Default.Add, onClick = { draftClicked++ }),
                EmotePeekAction("inspect", "Inspeccionar", Icons.Default.ZoomIn, onClick = { inspectClicked++ }),
                EmotePeekAction("share", "Compartir", Icons.Default.Share, onClick = { shareClicked++ })
            )
        )

        val recordedHaptics = mutableListOf<HapticFeedbackType>()
        val fakeHaptic = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                recordedHaptics.add(hapticFeedbackType)
            }
        }

        // Simulate bounds shifting from initial animation (progress=0f) to settled layout (progress=1f)
        var currentShareRect = Rect(300f, 650f, 400f, 700f)
        state.hitSlopPx = 14f
        state.boundsSnapshotProvider = {
            mapOf(
                0 to Rect(60f, 800f, 180f, 850f),
                1 to Rect(190f, 800f, 320f, 850f),
                2 to currentShareRect
            )
        }

        state.show(payload)
        // Update settled position of Share button after entrance animation completes
        currentShareRect = Rect(330f, 800f, 450f, 850f)
        state.updatePointer(Offset(390f, 825f))

        // Release executes index 2 ("Compartir") using fresh bounds snapshot even without manual hoveredActionIndex
        state.onRelease(fakeHaptic)
        assertEquals(0, draftClicked)
        assertEquals(0, inspectClicked)
        assertEquals(1, shareClicked)
        assertEquals(listOf(HapticFeedbackType.LongPress), recordedHaptics)
        assertFalse(state.isActive)
    }

    @Test
    fun emotePeekState_executesShareAction_withHitSlopOnEdge() {
        val state = EmotePeekState()
        var shareClicked = 0

        val payload = EmotePeekPayload(
            id = "emote_share_edge",
            name = "pepeLaugh",
            isAnimated = false,
            imageModel = "https://cdn.7tv.app/emote/3/4x.webp",
            actions = listOf(
                EmotePeekAction("draft", "En borrador", Icons.Default.Add, onClick = {}),
                EmotePeekAction("inspect", "Inspeccionar", Icons.Default.ZoomIn, onClick = {}),
                EmotePeekAction("share", "Compartir", Icons.Default.Share, onClick = { shareClicked++ })
            )
        )

        state.hitSlopPx = 20f
        state.boundsSnapshotProvider = {
            mapOf(
                0 to Rect(60f, 800f, 180f, 850f),
                1 to Rect(190f, 800f, 320f, 850f),
                2 to Rect(330f, 800f, 450f, 850f)
            )
        }

        state.show(payload)
        // Pointer is 10px below the Share button bottom edge (within 20px hitSlop)
        state.updatePointer(Offset(400f, 860f))
        state.onRelease()

        assertEquals(1, shareClicked)
        assertFalse(state.isActive)
    }

    @Test
    fun coilSetup_generatesConsistentCacheKeys() {
        assertEquals("emote_preview_abc123", CoilSetup.emotePreviewCacheKey("abc123"))
        assertEquals("pack_sticker_preview_stk99", CoilSetup.packStickerPreviewCacheKey("stk99"))
    }
}

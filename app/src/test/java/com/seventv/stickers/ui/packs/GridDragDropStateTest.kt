package com.seventv.stickers.ui.packs

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GridDragDropStateTest {

    private val sampleBounds = listOf(
        GridItemBounds(index = 0, key = "sticker_0", left = 0, top = 0, width = 100, height = 100),
        GridItemBounds(index = 1, key = "sticker_1", left = 110, top = 0, width = 100, height = 100),
        GridItemBounds(index = 2, key = "sticker_2", left = 220, top = 0, width = 100, height = 100),
        GridItemBounds(index = 3, key = "sticker_3", left = 0, top = 110, width = 100, height = 100),
        GridItemBounds(index = 4, key = "sticker_4", left = 110, top = 110, width = 100, height = 100),
        GridItemBounds(index = 5, key = "sticker_5", left = 220, top = 110, width = 100, height = 100)
    )

    @Test
    fun findHitItemReturnsMatchingItemWhenInsideBounds() {
        val hitTopLeft = GridDragDropMath.findHitItem(50f, 50f, sampleBounds)
        assertNotNull(hitTopLeft)
        assertEquals(0, hitTopLeft?.index)
        assertEquals("sticker_0", hitTopLeft?.key)

        val hitBottomRight = GridDragDropMath.findHitItem(250f, 150f, sampleBounds)
        assertNotNull(hitBottomRight)
        assertEquals(5, hitBottomRight?.index)
        assertEquals("sticker_5", hitBottomRight?.key)
    }

    @Test
    fun findHitItemReturnsNullWhenInGapsOrOutside() {
        // Gap between col 0 and col 1
        val inHorizontalGap = GridDragDropMath.findHitItem(105f, 50f, sampleBounds)
        assertNull(inHorizontalGap)

        // Gap between row 0 and row 1
        val inVerticalGap = GridDragDropMath.findHitItem(50f, 105f, sampleBounds)
        assertNull(inVerticalGap)

        // Completely out of bounds
        val outside = GridDragDropMath.findHitItem(500f, 500f, sampleBounds)
        assertNull(outside)
    }

    @Test
    fun findSwapTargetIndexDetectsCollisionWithTargetItemAndIgnoresSelf() {
        // Drag center is inside item 1 (110..210, 0..100)
        val swapTarget = GridDragDropMath.findSwapTargetIndex(
            draggedCenterX = 150f,
            draggedCenterY = 50f,
            draggedIndex = 0,
            items = sampleBounds
        )
        assertEquals(1, swapTarget)

        // Drag center is inside dragged item itself (0): should return null
        val selfCollision = GridDragDropMath.findSwapTargetIndex(
            draggedCenterX = 50f,
            draggedCenterY = 50f,
            draggedIndex = 0,
            items = sampleBounds
        )
        assertNull(selfCollision)
    }

    @Test
    fun findSwapTargetIndexReturnsNullWhenInGap() {
        val inGap = GridDragDropMath.findSwapTargetIndex(
            draggedCenterX = 105f,
            draggedCenterY = 50f,
            draggedIndex = 0,
            items = sampleBounds
        )
        assertNull(inGap)
    }

    @Test
    fun computeAutoscrollDeltaComputesCorrectDirectionsAndThresholds() {
        val viewportStart = 0f
        val viewportEnd = 800f
        val threshold = 100f
        val maxStep = 20f

        // Center of viewport -> 0 scroll
        val middle = GridDragDropMath.computeAutoscrollDelta(
            pointerY = 400f,
            viewportStartY = viewportStart,
            viewportEndY = viewportEnd,
            edgeThresholdPx = threshold,
            maxScrollStepPx = maxStep
        )
        assertEquals(0f, middle, 0.001f)

        // Exactly at top boundary (100f) -> 0 scroll
        val topBoundary = GridDragDropMath.computeAutoscrollDelta(
            pointerY = 100f,
            viewportStartY = viewportStart,
            viewportEndY = viewportEnd,
            edgeThresholdPx = threshold,
            maxScrollStepPx = maxStep
        )
        assertEquals(0f, topBoundary, 0.001f)

        // Halfway into top threshold (50f) -> negative scroll delta (up)
        val halfTop = GridDragDropMath.computeAutoscrollDelta(
            pointerY = 50f,
            viewportStartY = viewportStart,
            viewportEndY = viewportEnd,
            edgeThresholdPx = threshold,
            maxScrollStepPx = maxStep
        )
        assertEquals(-10f, halfTop, 0.001f)

        // At or beyond top edge (0f or negative) -> full negative step
        val fullTop = GridDragDropMath.computeAutoscrollDelta(
            pointerY = 0f,
            viewportStartY = viewportStart,
            viewportEndY = viewportEnd,
            edgeThresholdPx = threshold,
            maxScrollStepPx = maxStep
        )
        assertEquals(-20f, fullTop, 0.001f)

        // Halfway into bottom threshold (750f vs 700..800) -> positive scroll delta (down)
        val halfBottom = GridDragDropMath.computeAutoscrollDelta(
            pointerY = 750f,
            viewportStartY = viewportStart,
            viewportEndY = viewportEnd,
            edgeThresholdPx = threshold,
            maxScrollStepPx = maxStep
        )
        assertEquals(10f, halfBottom, 0.001f)

        // At or beyond bottom edge (800f) -> full positive step
        val fullBottom = GridDragDropMath.computeAutoscrollDelta(
            pointerY = 800f,
            viewportStartY = viewportStart,
            viewportEndY = viewportEnd,
            edgeThresholdPx = threshold,
            maxScrollStepPx = maxStep
        )
        assertEquals(20f, fullBottom, 0.001f)
    }

    @Test
    fun computeAutoscrollDeltaHandlesDegenerateBounds() {
        val delta = GridDragDropMath.computeAutoscrollDelta(
            pointerY = 10f,
            viewportStartY = 100f,
            viewportEndY = 50f
        )
        assertEquals(0f, delta, 0.001f)
    }

    @Test
    fun gridDragDropStateHandlesDragLifecycleAndResets() {
        val fakeHaptic = FakeHapticFeedback()
        var movedPair: Pair<Int, Int>? = null
        var startedIndex: Int? = null
        var finished = false
        var cancelled = false
        var slotZeroChanged = false

        val state = GridDragDropState(
            gridState = LazyGridState(),
            hapticFeedback = fakeHaptic,
            onMove = { from, to -> movedPair = from to to },
            onDragStart = { idx -> startedIndex = idx },
            onDragEnd = { finished = true },
            onDragCancel = { cancelled = true },
            onSlotZeroChanged = { slotZeroChanged = true }
        )

        assertFalse(state.isDragging)
        assertNull(state.draggingItemKey)
        assertNull(state.draggingItemIndex)
        assertEquals(Offset.Zero, state.currentItemTranslation("any_key"))

        // Calling start on an empty layout returns false gracefully
        val startResult = state.onStartDrag(Offset(50f, 50f))
        assertFalse(startResult)
        assertFalse(state.isDragging)

        // Cancel drag cleans up
        state.onCancelDrag()
        assertFalse(state.isDragging)

        // Finish drag cleans up
        state.onFinishDrag()
        assertFalse(state.isDragging)
    }

    private class FakeHapticFeedback : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed.add(hapticFeedbackType)
        }
    }
}

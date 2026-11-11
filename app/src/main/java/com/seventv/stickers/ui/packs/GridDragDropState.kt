package com.seventv.stickers.ui.packs

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Pure geometry representation of a visible grid cell for deterministic collision and autoscroll math.
 */
data class GridItemBounds(
    val index: Int,
    val key: Any,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height

    fun contains(x: Float, y: Float): Boolean {
        return x >= left && x <= right && y >= top && y <= bottom
    }
}

object GridDragDropMath {
    const val DEFAULT_EDGE_THRESHOLD_PX = 140f
    const val DEFAULT_MAX_SCROLL_STEP_PX = 24f

    fun findHitItem(
        x: Float,
        y: Float,
        items: List<GridItemBounds>
    ): GridItemBounds? {
        return items.firstOrNull { it.contains(x, y) }
    }

    fun findSwapTargetIndex(
        draggedCenterX: Float,
        draggedCenterY: Float,
        draggedIndex: Int,
        items: List<GridItemBounds>
    ): Int? {
        val target = items.firstOrNull { item ->
            item.index != draggedIndex && item.contains(draggedCenterX, draggedCenterY)
        }
        return target?.index
    }

    fun computeAutoscrollDelta(
        pointerY: Float,
        viewportStartY: Float,
        viewportEndY: Float,
        edgeThresholdPx: Float = DEFAULT_EDGE_THRESHOLD_PX,
        maxScrollStepPx: Float = DEFAULT_MAX_SCROLL_STEP_PX
    ): Float {
        if (viewportEndY <= viewportStartY || edgeThresholdPx <= 0f) return 0f
        val topBoundary = viewportStartY + edgeThresholdPx
        val bottomBoundary = viewportEndY - edgeThresholdPx
        return when {
            pointerY < topBoundary -> {
                val fraction = ((topBoundary - pointerY) / edgeThresholdPx).coerceIn(0f, 1f)
                -maxScrollStepPx * fraction
            }
            pointerY > bottomBoundary -> {
                val fraction = ((pointerY - bottomBoundary) / edgeThresholdPx).coerceIn(0f, 1f)
                maxScrollStepPx * fraction
            }
            else -> 0f
        }
    }
}

@Composable
fun rememberGridDragDropState(
    gridState: LazyGridState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragStart: (index: Int) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onSlotZeroChanged: () -> Unit = {}
): GridDragDropState {
    val hapticFeedback = LocalHapticFeedback.current
    return remember(gridState, hapticFeedback) {
        GridDragDropState(
            gridState = gridState,
            hapticFeedback = hapticFeedback,
            onMove = onMove,
            onDragStart = onDragStart,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            onSlotZeroChanged = onSlotZeroChanged
        )
    }
}

@Stable
class GridDragDropState(
    val gridState: LazyGridState,
    private val hapticFeedback: HapticFeedback,
    private var onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private var onDragStart: (index: Int) -> Unit,
    private var onDragEnd: () -> Unit,
    private var onDragCancel: () -> Unit,
    private var onSlotZeroChanged: () -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    val isDragging: Boolean
        get() = draggingItemKey != null

    private var initialItemOffset: IntOffset = IntOffset.Zero
    private var initialItemSize: IntSize = IntSize.Zero
    private var totalDragDelta: Offset by mutableStateOf(Offset.Zero)
    private var pointerPosition: Offset by mutableStateOf(Offset.Zero)
    private var initialSlotZeroKey: Any? = null
    private var lastKnownLayoutOffset: IntOffset = IntOffset.Zero

    fun updateCallbacks(
        onMove: (fromIndex: Int, toIndex: Int) -> Unit,
        onDragStart: (index: Int) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onSlotZeroChanged: () -> Unit
    ) {
        this.onMove = onMove
        this.onDragStart = onDragStart
        this.onDragEnd = onDragEnd
        this.onDragCancel = onDragCancel
        this.onSlotZeroChanged = onSlotZeroChanged
    }

    fun onStartDrag(offset: Offset): Boolean {
        val visibleBounds = gridState.layoutInfo.visibleItemsInfo.map { it.toBounds() }
        val hit = GridDragDropMath.findHitItem(offset.x, offset.y, visibleBounds) ?: return false
        val hitInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == hit.key } ?: return false

        draggingItemIndex = hitInfo.index
        draggingItemKey = hitInfo.key
        initialItemOffset = hitInfo.offset
        lastKnownLayoutOffset = hitInfo.offset
        initialItemSize = hitInfo.size
        totalDragDelta = Offset.Zero
        pointerPosition = offset
        initialSlotZeroKey = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.key

        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        onDragStart(hitInfo.index)
        return true
    }

    fun onDragBy(dragAmount: Offset) {
        if (!isDragging) return
        totalDragDelta += dragAmount
        pointerPosition += dragAmount
        checkForSwap()
    }

    fun checkForSwap() {
        val currentKey = draggingItemKey ?: return
        val currentIndex = gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == currentKey }?.index ?: draggingItemIndex ?: return

        draggingItemIndex = currentIndex

        val desiredTopLeftX = initialItemOffset.x + totalDragDelta.x
        val desiredTopLeftY = initialItemOffset.y + totalDragDelta.y
        val centerX = desiredTopLeftX + initialItemSize.width / 2f
        val centerY = desiredTopLeftY + initialItemSize.height / 2f

        val visibleBounds = gridState.layoutInfo.visibleItemsInfo.map { it.toBounds() }
        val targetIndex = GridDragDropMath.findSwapTargetIndex(
            draggedCenterX = centerX,
            draggedCenterY = centerY,
            draggedIndex = currentIndex,
            items = visibleBounds
        )

        if (targetIndex != null && targetIndex != currentIndex) {
            onMove(currentIndex, targetIndex)
            draggingItemIndex = targetIndex
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    suspend fun performAutoscrollStepIfNeeded(): Boolean {
        if (!isDragging) return false
        val layoutInfo = gridState.layoutInfo
        val step = GridDragDropMath.computeAutoscrollDelta(
            pointerY = pointerPosition.y,
            viewportStartY = layoutInfo.viewportStartOffset.toFloat(),
            viewportEndY = layoutInfo.viewportEndOffset.toFloat()
        )
        if (step == 0f) return false

        val consumed = gridState.scrollBy(step)
        if (consumed != 0f) {
            checkForSwap()
            return true
        }
        return false
    }

    fun currentItemTranslation(key: Any): Offset {
        if (draggingItemKey != key) return Offset.Zero
        val currentLayoutInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        val currentOffset = if (currentLayoutInfo != null) {
            lastKnownLayoutOffset = currentLayoutInfo.offset
            currentLayoutInfo.offset
        } else {
            lastKnownLayoutOffset
        }
        val desiredX = initialItemOffset.x + totalDragDelta.x
        val desiredY = initialItemOffset.y + totalDragDelta.y
        return Offset(
            x = desiredX - currentOffset.x,
            y = desiredY - currentOffset.y
        )
    }

    fun onFinishDrag() {
        if (!isDragging) return
        val newSlotZeroKey = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.key
        val slotZeroChanged = initialSlotZeroKey != null &&
            newSlotZeroKey != null &&
            initialSlotZeroKey != newSlotZeroKey

        resetDragState()
        onDragEnd()
        if (slotZeroChanged) {
            onSlotZeroChanged()
        }
    }

    fun onCancelDrag() {
        if (!isDragging) return
        resetDragState()
        onDragCancel()
    }

    private fun resetDragState() {
        draggingItemIndex = null
        draggingItemKey = null
        initialItemOffset = IntOffset.Zero
        lastKnownLayoutOffset = IntOffset.Zero
        initialItemSize = IntSize.Zero
        totalDragDelta = Offset.Zero
        pointerPosition = Offset.Zero
        initialSlotZeroKey = null
    }

    private fun LazyGridItemInfo.toBounds(): GridItemBounds {
        return GridItemBounds(
            index = index,
            key = key,
            left = offset.x,
            top = offset.y,
            width = size.width,
            height = size.height
        )
    }
}

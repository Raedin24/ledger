package com.ledger.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive

/**
 * Drag-to-reorder for a `LazyColumn`.
 *
 * Compose Foundation has no reorderable list, and only one screen needs it, so
 * this is a small version written here instead of adding a library.
 *
 * It works with item keys, not indices. The lists that use it have section
 * headers, which are items too, so an index isn't a position in the data.
 * [ReorderState.canDrop] keeps a row inside its own section: a header's key,
 * or a row from another section, fails the check and the drag passes over it.
 *
 * The dragged row stays under the finger while the list moves beneath it. Its
 * offset is worked out as "where it should be drawn minus where the list put
 * it", rather than added up step by step. That way, when a swap moves the row
 * to a new slot mid-drag, it doesn't slowly drift away from the finger.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
) {
    /**
     * Set by [rememberReorderState] on each composition. Only read from gesture
     * callbacks, never during composition, so a plain field is enough.
     */
    internal var onMove: (from: Any, to: Any) -> Unit = { _, _ -> }
    internal var canDrop: (from: Any, to: Any) -> Boolean = { _, _ -> false }

    /** Key of the row under the finger, or null when nothing is being dragged. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    /** Finger travel since the drag began, in pixels. */
    private var travel by mutableFloatStateOf(0f)

    /**
     * Where the list placed the row when the drag began, in viewport coordinates.
     * [drawnTop] is measured from here.
     */
    private var anchor = 0

    private fun itemFor(key: Any) =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    /** Where the dragged row is drawn, in viewport coordinates. */
    private val drawnTop: Float get() = anchor + travel

    /** Vertical offset for the row keyed [key]. Zero for every row except the dragged one. */
    fun translationFor(key: Any): Float {
        if (key != draggingKey) return 0f
        // Not laid out yet (scrolled away during a fast drag): use the raw finger
        // travel rather than snapping the row back to its slot.
        val item = itemFor(key) ?: return travel
        return drawnTop - item.offset
    }

    fun start(key: Any) {
        anchor = itemFor(key)?.offset ?: return
        travel = 0f
        draggingKey = key
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun drag(delta: Float) {
        val key = draggingKey ?: return
        travel += delta
        val dragged = itemFor(key) ?: return
        // Swap when the dragged row's middle crosses into a neighbour, not its
        // edge. Using the edge makes a slow drag flicker between two orders.
        val centre = drawnTop + dragged.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            other.key != key &&
                centre >= other.offset &&
                centre <= other.offset + other.size &&
                canDrop(key, other.key)
        }
        if (target != null) {
            onMove(key, target.key)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun stop() {
        draggingKey = null
        travel = 0f
    }

    /**
     * How far to scroll this frame when the row is held near the top or bottom
     * edge, so a list longer than the screen can be reordered all the way.
     */
    internal fun autoScrollDelta(): Float {
        val key = draggingKey ?: return 0f
        val dragged = itemFor(key) ?: return 0f
        val info = listState.layoutInfo
        val hot = ((info.viewportEndOffset - info.viewportStartOffset) * EDGE_FRACTION)
            .coerceAtMost(MAX_EDGE_PX)
        if (hot <= 0f) return 0f
        val bottom = drawnTop + dragged.size
        val past = when {
            drawnTop < info.viewportStartOffset + hot -> drawnTop - (info.viewportStartOffset + hot)
            bottom > info.viewportEndOffset - hot -> bottom - (info.viewportEndOffset - hot)
            else -> return 0f
        }
        // The further into the edge zone, the faster it scrolls.
        return (past / hot).coerceIn(-1f, 1f) * MAX_SCROLL_PER_FRAME
    }
}

/** How much of the viewport at each end counts as the auto-scroll zone. */
private const val EDGE_FRACTION = 0.15f
private const val MAX_EDGE_PX = 140f
private const val MAX_SCROLL_PER_FRAME = 14f

/**
 * @param canDrop whether the row keyed `from` may take the slot of the row keyed
 *   `to`. Return false for keys that aren't reorderable rows at all (headers,
 *   spacers) and for rows in a different group.
 * @param onMove move the row keyed `from` to where the row keyed `to` currently
 *   sits, in the caller's own model. Called repeatedly during a drag.
 */
@Composable
fun rememberReorderState(
    listState: LazyListState,
    canDrop: (from: Any, to: Any) -> Boolean,
    onMove: (from: Any, to: Any) -> Unit,
): ReorderState {
    val haptics = LocalHapticFeedback.current
    val state = remember(listState, haptics) { ReorderState(listState, haptics) }
    state.canDrop = canDrop
    state.onMove = onMove

    LaunchedEffect(state.draggingKey) {
        if (state.draggingKey == null) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val delta = state.autoScrollDelta()
            if (delta != 0f) listState.scrollBy(delta)
        }
    }
    return state
}

/**
 * Draws the row keyed [key] under the finger while it is dragged, above the
 * rows it passes over.
 */
fun Modifier.reorderableItem(state: ReorderState, key: Any): Modifier =
    this
        .zIndex(if (state.draggingKey == key) 1f else 0f)
        .graphicsLayer { translationY = state.translationFor(key) }

/**
 * Makes a control the drag handle for the row keyed [key].
 *
 * The gesture is on the handle, not the whole row, for two reasons. The row
 * keeps its own tap (opening the editor). And the handle takes the drag before
 * the `LazyColumn` sees it, so dragging the handle reorders instead of
 * scrolling.
 *
 * [onDrop] is called once, when the finger lifts. That is where the new order
 * is saved, so a long drag is one write.
 */
fun Modifier.dragHandle(state: ReorderState, key: Any, onDrop: () -> Unit): Modifier =
    this.pointerInput(key) {
        detectDragGestures(
            onDragStart = { state.start(key) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = { state.stop(); onDrop() },
            onDragCancel = { state.stop() },
        )
    }

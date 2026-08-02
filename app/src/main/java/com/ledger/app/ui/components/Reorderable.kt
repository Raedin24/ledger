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
 * Drag-to-reorder for a `LazyColumn`, hand-rolled.
 *
 * There is no reorderable list in Compose Foundation, and the third-party ones
 * are a dependency for something this app needs in exactly one screen — so this
 * is the small version: no animation queue, no nested-scroll plumbing, just the
 * two things a reorder actually needs.
 *
 * It works on item **keys**, not indices, because the lists that use it are
 * sectioned: the section headers are items too, so a lazy index is not a position
 * in any one model. [ReorderState.canDrop] is what keeps a row inside its own
 * section — a header's key simply fails the test, as does a row from another
 * section, and the drag slides past both.
 *
 * The dragged row is pinned to the **screen**, not to the content: it is drawn at
 * the point the finger has taken it to, and the list scrolls and re-slots
 * underneath. That is why the translation is computed as `drawn − laid out`
 * rather than accumulated: when a swap moves the row to a new slot mid-drag, the
 * jump the layout just made falls straight out of the subtraction. Accumulating
 * an offset and correcting it after each swap drifts by a pixel or two per swap,
 * and after a long drag the row is visibly no longer under the finger.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
) {
    /** Set by [rememberReorderState] on each composition — read only from gesture
     *  callbacks, never during composition, so plain fields are enough. */
    internal var onMove: (from: Any, to: Any) -> Unit = { _, _ -> }
    internal var canDrop: (from: Any, to: Any) -> Boolean = { _, _ -> false }

    /** Key of the row under the finger, or null when nothing is being dragged. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    /** Finger travel since the drag began, in pixels. */
    private var travel by mutableFloatStateOf(0f)

    /** Where the list had laid the row out when the drag began, in viewport
     *  coordinates — the anchor [drawnTop] is measured from. */
    private var anchor = 0

    private fun itemFor(key: Any) =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    /** Where the dragged row is drawn, in viewport coordinates. */
    private val drawnTop: Float get() = anchor + travel

    /**
     * Vertical translation for the row keyed [key] — zero for every row except
     * the one being dragged.
     */
    fun translationFor(key: Any): Float {
        if (key != draggingKey) return 0f
        // Not yet laid out (scrolled off during a fast drag): fall back to raw
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
        // Swap when the row's *middle* crosses into a neighbour, not its edge:
        // testing the edge fires as soon as the rows overlap by a pixel, which
        // makes a slow drag flutter between two orders.
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
     * Pixels to scroll this frame so a row held against the top or bottom edge
     * keeps travelling — without it a list taller than the screen can only be
     * reordered as far as one screenful.
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
        // Ramps with how far into the edge zone the row has been pushed, so a
        // nudge creeps and a shove runs.
        return (past / hot).coerceIn(-1f, 1f) * MAX_SCROLL_PER_FRAME
    }
}

/** How much of the viewport, at each end, counts as the auto-scroll edge zone. */
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

/** Draws the row keyed [key] under the finger while it is being dragged, and
 *  lifts it above the rows it is passing over. */
fun Modifier.reorderableItem(state: ReorderState, key: Any): Modifier =
    this
        .zIndex(if (state.draggingKey == key) 1f else 0f)
        .graphicsLayer { translationY = state.translationFor(key) }

/**
 * Turns a control into the drag handle for the row keyed [key].
 *
 * The gesture lives on the handle rather than the whole row for two reasons: the
 * row keeps its own tap (open the editor), and a child pointer handler consumes
 * the drag before the enclosing `LazyColumn`'s scroll sees it, so a vertical drag
 * from the handle reorders instead of scrolling.
 *
 * [onDrop] is called once, when the finger lifts — that is where the new order is
 * persisted, so a drag across ten rows is one write rather than ten.
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

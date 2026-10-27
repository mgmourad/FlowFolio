package com.flowfolio.feature.library

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drag and drop to reorder the books on a shelf (owner, 2026-10-26): press and hold a book, then drag it; the book
 * under the finger makes way, and letting go saves the new order. Holding still and letting go opens the book's menu
 * instead, so press-and-hold menus keep working. Works over a lazy row or grid through [slots], which says where each
 * visible item is in the list's own coordinates.
 */
@Stable
internal class ReorderState(
    keys: List<String>,
    private val scope: CoroutineScope,
    private val slots: () -> Map<String, Rect>,
    /** Scrolls the list by a distance (for dragging past its edge); returns what it used. */
    private val scrollBy: suspend (Offset) -> Unit,
    /** The list's visible size, for the edges. */
    private val viewport: () -> Rect,
    private val onCommit: (List<String>) -> Unit
) {
    /** The keys in the order shown: the stored order, or while dragging the order the drag has made. */
    var order by mutableStateOf(keys)
        private set

    /** The book being dragged, if any. */
    var dragging by mutableStateOf<String?>(null)
        private set

    /** The finger, in the list's coordinates (near enough: the book's cell plus where it was touched). */
    private var pointer = Offset.Zero
    private var startPointer = Offset.Zero
    private var startSlot = Offset.Zero
    private var travelled = 0f
    private var moved = false

    /** Where the dragged book is drawn, relative to the slot it now occupies. */
    var shift by mutableStateOf(Offset.Zero)
        private set

    /** A new stored order (after saving, or from elsewhere) replaces the shown one when no drag is under way. */
    fun sync(keys: List<String>) {
        if (dragging == null && keys != order) order = keys
    }

    fun start(key: String, local: Offset) {
        val slot = slots()[key] ?: return
        dragging = key
        startSlot = slot.topLeft
        pointer = slot.topLeft + local
        startPointer = pointer
        travelled = 0f
        moved = false
        shift = Offset.Zero
    }

    fun drag(delta: Offset) {
        val key = dragging ?: return
        pointer += delta
        travelled += delta.getDistance()
        val target = slots().entries.firstOrNull { (k, r) -> k != key && k in order && r.contains(pointer) }?.key
        if (target != null) {
            val list = order.toMutableList()
            list.remove(key)
            list.add(order.indexOf(target).coerceAtMost(list.size), key)
            order = list
            moved = true
        }
        // The finger's travel, less how far the book's own slot has moved as others made way.
        slots()[key]?.let { shift = (pointer - startPointer) - (it.topLeft - startSlot) }
        edgeScroll()
    }

    /** Ends the drag; true when the finger never really moved (a plain press and hold: open the menu). */
    fun end(slop: Float): Boolean {
        val still = travelled < slop
        if (moved) onCommit(order)
        dragging = null
        shift = Offset.Zero
        return still
    }

    private fun edgeScroll() {
        val view = viewport()
        val step = Offset(
            when {
                pointer.x < view.left + EDGE -> -STEP
                pointer.x > view.right - EDGE -> STEP
                else -> 0f
            },
            when {
                pointer.y < view.top + EDGE -> -STEP
                pointer.y > view.bottom - EDGE -> STEP
                else -> 0f
            }
        )
        if (step != Offset.Zero) scope.launch { scrollBy(step) }
    }

    private companion object {
        const val EDGE = 96f
        const val STEP = 24f
    }
}

/** A [ReorderState] over a lazy row's visible items. */
@Composable
internal fun rememberRowReorder(
    keys: List<String>,
    list: LazyListState,
    onCommit: (List<String>) -> Unit
): ReorderState {
    val scope = rememberCoroutineScope()
    val state = remember(list) {
        ReorderState(
            keys,
            scope,
            slots = {
                list.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    (info.key as? String)?.let {
                        it to Rect(info.offset.toFloat(), 0f, (info.offset + info.size).toFloat(), Float.MAX_VALUE)
                    }
                }.toMap()
            },
            scrollBy = { list.scrollBy(it.x) },
            viewport = {
                val size = list.layoutInfo.viewportSize
                Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
            },
            onCommit = onCommit
        )
    }
    state.sync(keys)
    return state
}

/** A [ReorderState] over a lazy grid's visible items. */
@Composable
internal fun rememberGridReorder(
    keys: List<String>,
    grid: LazyGridState,
    onCommit: (List<String>) -> Unit
): ReorderState {
    val scope = rememberCoroutineScope()
    val state = remember(grid) {
        ReorderState(
            keys,
            scope,
            slots = {
                grid.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    (info.key as? String)?.let {
                        it to Rect(
                            info.offset.x.toFloat(),
                            info.offset.y.toFloat(),
                            (info.offset.x + info.size.width).toFloat(),
                            (info.offset.y + info.size.height).toFloat()
                        )
                    }
                }.toMap()
            },
            scrollBy = { grid.scrollBy(it.y) },
            viewport = {
                val size = grid.layoutInfo.viewportSize
                Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
            },
            onCommit = onCommit
        )
    }
    state.sync(keys)
    return state
}

/** A [ReorderState] over a lazy column's visible items. */
@Composable
internal fun rememberColumnReorder(
    keys: List<String>,
    list: LazyListState,
    onCommit: (List<String>) -> Unit
): ReorderState {
    val scope = rememberCoroutineScope()
    val state = remember(list) {
        ReorderState(
            keys,
            scope,
            slots = {
                list.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    (info.key as? String)?.let {
                        it to Rect(0f, info.offset.toFloat(), Float.MAX_VALUE, (info.offset + info.size).toFloat())
                    }
                }.toMap()
            },
            scrollBy = { list.scrollBy(it.y) },
            viewport = {
                val size = list.layoutInfo.viewportSize
                Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
            },
            onCommit = onCommit
        )
    }
    state.sync(keys)
    return state
}

/**
 * The gestures of a reorderable book, in place of a clickable: a tap opens it, press and hold then drag moves it, and
 * press and hold without moving opens its menu ([onHold]).
 */
@Composable
internal fun Modifier.reorderable(state: ReorderState, key: String, onTap: () -> Unit, onHold: () -> Unit): Modifier {
    val slop = LocalViewConfiguration.current.touchSlop * 2
    val dragged = state.dragging == key
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    return this
        .zIndex(if (dragged) 1f else 0f)
        .graphicsLayer {
            if (dragged) {
                translationX = state.shift.x
                translationY = state.shift.y
                scaleX = LIFT_SCALE
                scaleY = LIFT_SCALE
            }
        }
        // A long press is the drag's, so the tap detector must not also treat it as a tap.
        .pointerInput(state, key) { detectTapGestures(onTap = { tap() }, onLongPress = {}) }
        .pointerInput(state, key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(key, it) },
                onDrag = { change, delta ->
                    change.consume()
                    state.drag(delta)
                },
                onDragEnd = { if (state.end(slop)) hold() },
                onDragCancel = { state.end(slop) }
            )
        }
}

private const val LIFT_SCALE = 1.06f

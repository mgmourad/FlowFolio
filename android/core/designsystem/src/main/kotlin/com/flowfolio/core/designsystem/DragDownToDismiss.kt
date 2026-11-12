package com.flowfolio.core.designsystem

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Pull a panel down to put it away (owner, 2026-11-12; easier than Back when the Fold is folded). The panel follows
 * the finger and [onDismiss] runs past [DISMISS_DISTANCE]; short of it, it springs back.
 *
 * A list inside scrolls first: only a pull that the list, already at its top, leaves over moves the panel, and a
 * scroll back up takes the panel back before the list moves. So scrolling search results never closes their panel.
 */
fun Modifier.dragDownToDismiss(onDismiss: () -> Unit): Modifier = composed {
    val dismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { DISMISS_DISTANCE.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableFloatStateOf(0f) }
    fun settle() {
        if (dismissed > 0f) return
        if (offset > threshold) {
            dismissed = 1f
            dismiss()
        } else {
            scope.launch { animate(offset, 0f, animationSpec = Motion.springSpec()) { v, _ -> offset = v } }
        }
    }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || offset <= 0f) return Offset.Zero
                val taken = maxOf(available.y, -offset)
                offset += taken
                return Offset(0f, taken)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                offset += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset <= 0f) return Velocity.Zero
                settle()
                return available
            }
        }
    }
    this
        .graphicsLayer { translationY = offset }
        .nestedScroll(connection)
        .draggable(
            rememberDraggableState { delta -> offset = (offset + delta).coerceAtLeast(0f) },
            Orientation.Vertical,
            onDragStopped = { settle() }
        )
}

/** How far a panel must be pulled down to close. */
private val DISMISS_DISTANCE = 96.dp

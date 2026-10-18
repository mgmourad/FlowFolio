package com.flowfolio.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.flowfolio.core.designsystem.Motion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A list row that slides sideways to show actions underneath: swiping right uncovers [start] (on the left edge),
 * swiping left uncovers [end] (on the right edge). It settles open or closed; a quick flick opens it. [startWidth] and
 * [endWidth] are how far each side opens (zero for no actions on that side).
 */
@Composable
internal fun SwipeRow(
    startWidth: Dp,
    endWidth: Dp,
    start: @Composable RowScope.(close: () -> Unit) -> Unit,
    end: @Composable RowScope.(close: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val open = with(density) { startWidth.toPx() }
    val openEnd = with(density) { endWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val settle = { target: Float ->
        scope.launch { offset.animateTo(target, tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) }
    }
    val close = {
        settle(0f)
        Unit
    }
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
            if (offset.value > 0f) start(close)
        }
        Row(Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
            if (offset.value < 0f) {
                Box(Modifier.weight(1f))
                end(close)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-openEnd, open)) }
                    },
                    onDragStopped = { velocity ->
                        val v = offset.value
                        val target = when {
                            abs(velocity) > FLICK && velocity > 0f -> if (v < 0f) 0f else open
                            abs(velocity) > FLICK && velocity < 0f -> if (v > 0f) 0f else -openEnd
                            v > open / 2f -> open
                            v < -openEnd / 2f -> -openEnd
                            else -> 0f
                        }
                        settle(target)
                    }
                )
        ) { content() }
    }
}

/** Pixels per second that count as a flick. */
private const val FLICK = 1200f

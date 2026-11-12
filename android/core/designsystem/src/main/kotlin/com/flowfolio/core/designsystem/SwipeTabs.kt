package com.flowfolio.core.designsystem

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Swiping between tabs (owner, 2026-11-12, as book details first did): a swipe to the left moves to the next of
 * [count] tabs, one to the right to the previous. Put it on the area under the tabs. Vertical scrolling passes
 * through, and a child that takes a horizontal drag itself (a slider, a carousel) keeps it.
 */
fun Modifier.swipeTabs(tab: Int, count: Int, onTab: (Int) -> Unit): Modifier = pointerInput(tab, count) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            val threshold = SWIPE_TAB.toPx()
            when {
                total < -threshold && tab < count - 1 -> onTab(tab + 1)
                total > threshold && tab > 0 -> onTab(tab - 1)
            }
        }
    ) { change, dx ->
        change.consume()
        total += dx
    }
}

/** How far a swipe must travel to change the tab. */
private val SWIPE_TAB = 56.dp

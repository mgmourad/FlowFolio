package com.flowfolio.feature.reader

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.flowfolio.core.pagecurl.page.PageCurlState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The left page of a spread (owner, 2026-10-18): dragging right pulls the previous sheet over from the spine, following
 * the finger like a grab on the right page, and releasing finishes the turn when the finger was still moving right
 * (direction decides, even a short swipe) or lays the sheet back. A swipe left turns forward, animated.
 */
internal fun Modifier.leftPageDrag(curl: PageCurlState, scope: CoroutineScope): Modifier = pointerInput(curl) {
    var start = Offset.Zero
    var dragged = 0f
    var lastDelta = 0f
    var following = false
    detectHorizontalDragGestures(
        onDragStart = {
            start = it
            dragged = 0f
            following = false
        },
        onHorizontalDrag = { change, delta ->
            dragged += delta
            lastDelta = delta
            if (dragged > 0f && curl.current > 0) {
                following = true
                change.consume()
                // In the curl's coordinates the finger has moved [dragged] from the spine.
                val position = Offset(dragged, change.position.y)
                scope.launch { curl.dragBackwardTo(Offset(0f, start.y), position) }
            }
        },
        onDragEnd = {
            when {
                following -> scope.launch {
                    curl.releaseBackward(turn = lastDelta >= 0f && dragged > SWIPE_THRESHOLD_PX)
                }

                dragged < -SWIPE_THRESHOLD_PX -> scope.launch { curl.next() }
            }
        },
        onDragCancel = { if (following) scope.launch { curl.releaseBackward(turn = false) } }
    )
}

/**
 * The spread whose left page lies under the turning sheet, taken from the curl itself so it changes in the same frame
 * as the sheet. While a sheet is lifted back off the left page (progress below zero), the previous spread's left page
 * shows underneath; otherwise the current one. The reader's own unit catches up a frame or more later.
 */
internal fun leftPaneUnit(curl: PageCurlState): Int = if (curl.progress < 0f) curl.current - 1 else curl.current

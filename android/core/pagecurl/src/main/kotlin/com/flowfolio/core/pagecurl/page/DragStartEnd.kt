/*
 * Vendored from oleksandrbalan/pagecurl 1.5.1 (https://github.com/oleksandrbalan/pagecurl), by Oleksandr Balan.
 * Licensed under the Apache License, Version 2.0 (see core/pagecurl/LICENSE). Modified by The FlowFolio Authors
 * (ADR-0011): package renamed, experimental opt-in removed, book-spread back pages added. See core/pagecurl/NOTICE.
 */
package com.flowfolio.core.pagecurl.page

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import com.flowfolio.core.pagecurl.config.PageCurlConfig
import com.flowfolio.core.pagecurl.config.PageCurlConfig.DragInteraction.PointerBehavior
import com.flowfolio.core.pagecurl.utils.multiply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun Modifier.dragStartEnd(
    dragInteraction: PageCurlConfig.StartEndDragInteraction,
    state: PageCurlState.InternalState,
    enabledForward: Boolean,
    enabledBackward: Boolean,
    scope: CoroutineScope,
    onChange: (Int) -> Unit,
): Modifier = this.composed {
    val isEnabledForward = rememberUpdatedState(enabledForward)
    val isEnabledBackward = rememberUpdatedState(enabledBackward)

    pointerInput(state) {
        val forwardStartRect by lazy { dragInteraction.forward.start.multiply(size) }
        val forwardEndRect by lazy { dragInteraction.forward.end.multiply(size) }
        val backwardStartRect by lazy { dragInteraction.backward.start.multiply(size) }
        val backwardEndRect by lazy { dragInteraction.backward.end.multiply(size) }

        val forwardConfig = DragConfig(
            edge = state.forward,
            start = state.rightEdge,
            end = state.leftEdge,
            isEnabled = { isEnabledForward.value },
            isDragSucceed = { _, end -> forwardEndRect.contains(end) },
            onChange = { onChange(+1) },
            onCommit = { state.turnStarting(forward = true) }
        )
        val backwardConfig = DragConfig(
            edge = state.backward,
            start = state.leftEdge,
            end = state.rightEdge,
            isEnabled = { isEnabledBackward.value },
            isDragSucceed = { _, end -> backwardEndRect.contains(end) },
            onChange = { onChange(-1) },
            onCommit = { state.turnStarting(forward = false) }
        )

        detectCurlGestures(
            scope = scope,
            newEdgeCreator = when (dragInteraction.pointerBehavior) {
                PointerBehavior.Default -> NewEdgeCreator.Default()
                PointerBehavior.PageEdge -> NewEdgeCreator.PageEdge()
            },
            getConfig = { start, _ ->
                val config = if (forwardStartRect.contains(start)) {
                    forwardConfig
                } else if (backwardStartRect.contains(start)) {
                    backwardConfig
                } else {
                    null
                }

                if (config != null) {
                    scope.launch {
                        state.animateJob?.cancel()
                        state.reset()
                    }
                }

                config
            },
        )
    }
}

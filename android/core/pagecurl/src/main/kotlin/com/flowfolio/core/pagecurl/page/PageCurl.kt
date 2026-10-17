/*
 * Vendored from oleksandrbalan/pagecurl 1.5.1 (https://github.com/oleksandrbalan/pagecurl), by Oleksandr Balan.
 * Licensed under the Apache License, Version 2.0 (see core/pagecurl/LICENSE). Modified by The FlowFolio Authors
 * (ADR-0011): package renamed, experimental opt-in removed, book-spread back pages added. See core/pagecurl/NOTICE.
 */
package com.flowfolio.core.pagecurl.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.flowfolio.core.pagecurl.config.PageCurlConfig
import com.flowfolio.core.pagecurl.config.rememberPageCurlConfig

/**
 * Shows the pages which may be turned by drag or tap gestures.
 *
 * @param count The count of pages.
 * @param modifier The modifier for this composable.
 * @param state The state of the PageCurl. Use this to programmatically change the current page or observe changes.
 * @param config The configuration for PageCurl.
 * @param backPage Optional image for the back of page n's sheet (book spreads: the next spread's left page). When
 * null, the back is a tinted mirror of the front, as upstream.
 * @param gradientShadow FlowFolio: draw cheap gradient shading along the fold instead of the blurred shadow layer.
 * @param content The content lambda to provide the page composable. Receives the page number.
 */
@Composable
public fun PageCurl(
    count: Int,
    modifier: Modifier = Modifier,
    state: PageCurlState = rememberPageCurlState(),
    config: PageCurlConfig = rememberPageCurlConfig(),
    backPage: ((Int) -> ImageBitmap?)? = null,
    gradientShadow: Boolean = false,
    content: @Composable (Int) -> Unit
) {
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier) {
        state.setup(count, constraints)

        val updatedCurrent by rememberUpdatedState(state.current)
        val internalState by rememberUpdatedState(state.internalState ?: return@BoxWithConstraints)

        val updatedConfig by rememberUpdatedState(config)

        val dragGestureModifier = when (val interaction = updatedConfig.dragInteraction) {
            is PageCurlConfig.GestureDragInteraction ->
                Modifier
                    .dragGesture(
                        dragInteraction = interaction,
                        state = internalState,
                        enabledForward = updatedConfig.dragForwardEnabled && updatedCurrent < state.max - 1,
                        enabledBackward = updatedConfig.dragBackwardEnabled && updatedCurrent > 0,
                        scope = scope,
                        onChange = { state.current = updatedCurrent + it }
                    )

            is PageCurlConfig.StartEndDragInteraction ->
                Modifier
                    .dragStartEnd(
                        dragInteraction = interaction,
                        state = internalState,
                        enabledForward = updatedConfig.dragForwardEnabled && updatedCurrent < state.max - 1,
                        enabledBackward = updatedConfig.dragBackwardEnabled && updatedCurrent > 0,
                        scope = scope,
                        onChange = { state.current = updatedCurrent + it }
                    )
        }

        Box(
            Modifier
                .then(dragGestureModifier)
                .tapGesture(
                    config = updatedConfig,
                    scope = scope,
                    onTapForward = state::next,
                    onTapBackward = state::prev,
                )
        ) {
            // Wrap in key to synchronize state updates
            key(updatedCurrent, internalState.forward.value, internalState.backward.value) {
                if (updatedCurrent + 1 < state.max) {
                    content(updatedCurrent + 1)
                }

                if (updatedCurrent < state.max) {
                    val forward = internalState.forward.value
                    Box(Modifier.drawCurl(updatedConfig, forward.top, forward.bottom, backPage?.invoke(updatedCurrent), gradientShadow)) {
                        content(updatedCurrent)
                    }
                }

                if (updatedCurrent > 0) {
                    val backward = internalState.backward.value
                    Box(Modifier.drawCurl(updatedConfig, backward.top, backward.bottom, backPage?.invoke(updatedCurrent - 1), gradientShadow)) {
                        content(updatedCurrent - 1)
                    }
                }
            }
        }
    }
}

/**
 * Shows the pages which may be turned by drag or tap gestures.
 *
 * @param count The count of pages.
 * @param key The lambda to provide stable key for each item. Useful when adding and removing items before current page.
 * @param modifier The modifier for this composable.
 * @param state The state of the PageCurl. Use this to programmatically change the current page or observe changes.
 * @param config The configuration for PageCurl.
 * @param content The content lambda to provide the page composable. Receives the page number.
 */
@Composable
public fun PageCurl(
    count: Int,
    key: (Int) -> Any,
    modifier: Modifier = Modifier,
    state: PageCurlState = rememberPageCurlState(),
    config: PageCurlConfig = rememberPageCurlConfig(),
    backPage: ((Int) -> ImageBitmap?)? = null,
    content: @Composable (Int) -> Unit
) {
    var lastKey by remember(state.current) { mutableStateOf(if (count > 0) key(state.current) else null) }

    remember(count) {
        val newKey = if (count > 0) key(state.current) else null
        if (newKey != lastKey) {
            val index = List(count, key).indexOf(lastKey).coerceIn(0, count - 1)
            lastKey = newKey
            state.current = index
        }
        count
    }

    PageCurl(
        count = count,
        state = state,
        config = config,
        backPage = backPage,
        content = content,
        modifier = modifier,
    )
}

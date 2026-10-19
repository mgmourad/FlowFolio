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
import androidx.compose.ui.draw.drawWithContent
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
            // FlowFolio: each page is keyed on its own number, so a turn only reorders the pages already built (the
            // next page becomes the current one, the current one the previous) instead of rebuilding all of them in
            // one frame. Upstream also keyed on the moving fold edges, which rebuilt every page on every frame of a
            // turn; the edges are read in the draw phase. Drawn bottom to top: next, current, previous.
            for (page in updatedCurrent + 1 downTo updatedCurrent - 1) {
                if (page < 0 || page >= state.max) continue
                key(page) {
                    val edge: (() -> Edge)? = when (page) {
                        updatedCurrent -> internalState.forward.let { { it.value } }
                        updatedCurrent - 1 -> internalState.backward.let { { it.value } }
                        else -> null
                    }
                    val curl = if (edge == null) {
                        // The next page lies wholly under the current one until the current sheet starts to turn;
                        // skipping it saves a full page of overdraw on every frame of a backward turn.
                        val forward = internalState.forward
                        val rest = internalState.rightEdge
                        Modifier.drawWithContent { if (forward.value != rest) drawContent() }
                    } else {
                        Modifier.drawCurl(updatedConfig, edge, backPage?.invoke(page), gradientShadow)
                    }
                    Box(curl) { content(page) }
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

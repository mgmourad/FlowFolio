/*
 * Vendored from oleksandrbalan/pagecurl 1.5.1 (https://github.com/oleksandrbalan/pagecurl), by Oleksandr Balan.
 * Licensed under the Apache License, Version 2.0 (see core/pagecurl/LICENSE). Modified by The FlowFolio Authors
 * (ADR-0011): package renamed, experimental opt-in removed, book-spread back pages added. See core/pagecurl/NOTICE.
 */
package com.flowfolio.core.pagecurl.page

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.flowfolio.core.pagecurl.config.PageCurlConfig
import com.flowfolio.core.pagecurl.utils.multiply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun Modifier.tapGesture(
    config: PageCurlConfig,
    scope: CoroutineScope,
    onTapForward: suspend () -> Unit,
    onTapBackward: suspend () -> Unit,
): Modifier = pointerInput(config) {
    val tapInteraction = config.tapInteraction as? PageCurlConfig.TargetTapInteraction ?: return@pointerInput

    awaitEachGesture {
        val down = awaitFirstDown().also { it.consume() }
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture

        if ((down.position - up.position).getDistance() > viewConfiguration.touchSlop) {
            return@awaitEachGesture
        }

        if (config.tapCustomEnabled && config.onCustomTap(this, size, up.position)) {
            return@awaitEachGesture
        }

        if (config.tapForwardEnabled && tapInteraction.forward.target.multiply(size).contains(up.position)) {
            scope.launch {
                onTapForward()
            }
            return@awaitEachGesture
        }

        if (config.tapBackwardEnabled && tapInteraction.backward.target.multiply(size).contains(up.position)) {
            scope.launch {
                onTapBackward()
            }
            return@awaitEachGesture
        }
    }
}

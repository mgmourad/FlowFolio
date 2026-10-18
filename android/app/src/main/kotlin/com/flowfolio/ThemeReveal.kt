package com.flowfolio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.model.ThemePreference
import kotlin.math.hypot
import kotlin.math.max

/**
 * The mock's theme switch (`::view-transition` circle): when [theme] changes, the new theme is revealed in a circle
 * that grows from the last touch over 700 ms (ease-in-out) while the old one stays outside it. The old frame is a
 * one-off snapshot of what was on screen, dropped as soon as the reveal ends.
 */
@Composable
fun ThemeRevealHost(theme: ThemePreference, content: @Composable (ThemePreference) -> Unit) {
    var shown by remember { mutableStateOf(theme) }
    var old by remember { mutableStateOf<ImageBitmap?>(null) }
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    val reveal = remember { Animatable(0f) }
    val layer = rememberGraphicsLayer()
    LaunchedEffect(theme) {
        if (theme == shown) return@LaunchedEffect
        old = runCatching { layer.toImageBitmap() }.getOrNull()
        shown = theme
        reveal.snapTo(0f)
        try {
            if (old != null) reveal.animateTo(1f, tween(REVEAL_MS, easing = Motion.EaseInOut))
        } finally {
            old = null
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.pressed }?.let { touch = it.position }
                    }
                }
            }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val snapshot = old ?: return@drawWithContent
                val c = if (touch.isSpecified) touch else center
                // The circle ends at the farthest corner, so the new theme covers the whole screen.
                val reach = max(
                    max(hypot(c.x, c.y), hypot(size.width - c.x, size.height - c.y)),
                    max(hypot(size.width - c.x, c.y), hypot(c.x, size.height - c.y))
                )
                val circle = Path().apply { addOval(Rect(c, reach * reveal.value)) }
                clipPath(circle, ClipOp.Difference) { drawImage(snapshot) }
            }
    ) { content(shown) }
}

private const val REVEAL_MS = 700

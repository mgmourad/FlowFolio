package com.flowfolio.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.flowfolio.core.designsystem.LocalAppStyle
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.PaperStyle
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A change of page colour, revealed like the app's theme switch: the new colour grows in a circle from the touch over
 * 700 ms while the old look stays outside it. The old look is a snapshot taken just before the change; the circle
 * starts once the pages in view are drawn in the new colour (or after a short wait), so it never reveals blank paper.
 */
@Stable
internal class PaperReveal(
    private val layer: GraphicsLayer,
    private val scope: CoroutineScope,
    private val appDark: () -> Boolean,
    /** The Rustic theme is on: Auto paper is Rustic or Sepia then. */
    private val rustic: () -> Boolean,
    private val currentTone: () -> PageTone?,
    private val pagesReady: () -> Boolean
) {
    var old by mutableStateOf<ImageBitmap?>(null)
        private set

    /** The tone being revealed away from; the status bar keeps it until the circle has covered the screen. */
    var fromTone by mutableStateOf<PageTone?>(null)
        private set
    var touch by mutableStateOf(Offset.Unspecified)
    val progress = Animatable(0f)

    /** Changes the page colour to [style] through [apply], with the reveal when the tone actually changes. */
    fun change(style: PaperStyle, apply: (PaperStyle) -> Unit) {
        val target = style.tone(appDark(), rustic())
        if (target == currentTone() || old != null) {
            apply(style)
            return
        }
        scope.launch {
            // The circle starts closed before the snapshot shows: left full from the last change, it hid the snapshot
            // while the new pages were drawn, so the new colour (or blank paper) flashed before the reveal began.
            progress.snapTo(0f)
            fromTone = currentTone()
            old = runCatching { layer.toImageBitmap() }.getOrNull()
            apply(style)
            try {
                withTimeoutOrNull(MAX_WAIT_MS) {
                    snapshotFlow { currentTone() == target && pagesReady() }.first { it }
                }
                if (old != null) progress.animateTo(1f, tween(REVEAL_MS, easing = Motion.EaseInOut))
            } finally {
                old = null
                fromTone = null
            }
        }
    }
}

internal val LocalPaperReveal = staticCompositionLocalOf<PaperReveal?> { null }

/** Hosts [PaperReveal] around the reader: records what is drawn and paints the old look outside the circle. */
@Composable
internal fun PaperRevealHost(
    appDark: Boolean,
    state: ReaderState,
    images: Map<Int, *>,
    content: @Composable () -> Unit
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    val latestImages by rememberUpdatedState(images)
    val dark by rememberUpdatedState(appDark)
    val rustic by rememberUpdatedState(LocalAppStyle.current == AppStyle.RUSTIC)
    val reveal = remember(layer) {
        PaperReveal(
            layer,
            scope,
            appDark = { dark },
            rustic = { rustic },
            currentTone = { (latestState as? ReaderState.Ready)?.tone },
            pagesReady = {
                val s = latestState as? ReaderState.Ready
                s != null && pagesInView(s).all { it in latestImages }
            }
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.pressed }?.let { reveal.touch = it.position }
                    }
                }
            }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val snapshot = reveal.old ?: return@drawWithContent
                val c = if (reveal.touch.isSpecified) reveal.touch else center
                // The circle ends at the farthest corner, so the new colour covers the whole screen.
                val reach = max(
                    max(hypot(c.x, c.y), hypot(size.width - c.x, size.height - c.y)),
                    max(hypot(size.width - c.x, c.y), hypot(c.x, size.height - c.y))
                )
                val circle = Path().apply { addOval(Rect(c, reach * reveal.progress.value)) }
                clipPath(circle, ClipOp.Difference) { drawImage(snapshot) }
            }
    ) {
        CompositionLocalProvider(LocalPaperReveal provides reveal, content = content)
    }
}

private const val REVEAL_MS = 700
private const val MAX_WAIT_MS = 900L

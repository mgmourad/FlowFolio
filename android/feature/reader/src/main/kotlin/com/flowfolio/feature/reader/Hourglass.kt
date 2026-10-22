package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.glass
import kotlinx.coroutines.delay

/**
 * Shown over the pages while an EPUB is laid out again (owner, 2026-10-21), in place of pages flickering as they are
 * redrawn: a soft veil and an hourglass whose sand runs down, then turns over and runs again. It appears only if the
 * work takes more than [SHOW_AFTER_MS], and once shown stays at least [MIN_SHOWN_MS], so it never just blinks.
 */
@Composable
internal fun BoxScope.RelayoutHourglass(working: Boolean) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(working) {
        if (working) {
            delay(SHOW_AFTER_MS)
            shown = true
        } else if (shown) {
            delay(MIN_SHOWN_MS)
            shown = false
        }
    }
    val p = FlowFolio.palette
    val label = stringResource(R.string.reader_relayout)
    AnimatedVisibility(
        shown,
        Modifier.matchParentSize(),
        enter = fadeIn(tween(FADE_MS)),
        exit = fadeOut(tween(FADE_MS))
    ) {
        Box(Modifier.fillMaxSize().background(LocalTone.current.paper.copy(alpha = VEIL_ALPHA))) {
            Box(
                Modifier
                    // In the upper part of the page: the "Aa" sheet, where text is changed, covers the lower half.
                    .align(BiasAlignment(0f, UPPER_BIAS))
                    .glass(RoundedCornerShape(28.dp))
                    .padding(22.dp)
                    .semantics { contentDescription = label }
                    .testTag(ReaderTestTags.RELAYOUT)
            ) {
                Hourglass(glass = p.hint, sand = p.title, modifier = Modifier.size(56.dp, 72.dp))
            }
        }
    }
}

/** What shows over the pages for a moment: the pinch's text size, and the hourglass while the book is set again. */
@Composable
internal fun BoxScope.PageOverlays(s: ReaderState.Ready, pinch: TextPinch, viewModel: ReaderViewModel) {
    TextPinchBadge(pinch)
    val relayingOut by viewModel.relayingOut.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalTone provides s.tone) { RelayoutHourglass(relayingOut) }
}

/** The hourglass: sand runs from the top bulb to the bottom for [RUN_MS], then the glass turns over in [TURN_MS]. */
@Composable
internal fun Hourglass(glass: Color, sand: Color, modifier: Modifier = Modifier) {
    val cycle = rememberInfiniteTransition(label = "hourglass")
    val t by cycle.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(RUN_MS + TURN_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "time"
    )
    Canvas(modifier) {
        val run = RUN_MS.toFloat() / (RUN_MS + TURN_MS)
        val fall = (t / run).coerceAtMost(1f)
        val turn = ((t - run) / (1f - run)).coerceIn(0f, 1f)
        // Eased half turn; once turned, the full bulb is on top again, as at the start.
        val angle = HALF_TURN * (turn * turn * (3 - 2 * turn))
        rotate(angle) { drawHourglass(glass, sand, if (turn > 0f) 1f else fall, streaming = turn == 0f && fall < 1f) }
    }
}

private fun DrawScope.drawHourglass(glass: Color, sand: Color, fall: Float, streaming: Boolean) {
    val w = size.width
    val h = size.height
    val cap = h * CAP
    val neck = w * NECK
    val top = cap
    val bottom = h - cap
    val mid = h / 2
    // The two bulbs as one outline, pinched to a neck in the middle.
    val bulbs = Path().apply {
        moveTo(w * SIDE, top)
        lineTo(w * (1 - SIDE), top)
        cubicTo(w * (1 - SIDE), mid - h * CURVE, w / 2 + neck, mid - h * NECK_SPAN, w / 2 + neck, mid)
        cubicTo(w / 2 + neck, mid + h * NECK_SPAN, w * (1 - SIDE), mid + h * CURVE, w * (1 - SIDE), bottom)
        lineTo(w * SIDE, bottom)
        cubicTo(w * SIDE, mid + h * CURVE, w / 2 - neck, mid + h * NECK_SPAN, w / 2 - neck, mid)
        cubicTo(w / 2 - neck, mid - h * NECK_SPAN, w * SIDE, mid - h * CURVE, w * SIDE, top)
        close()
    }
    val bulbHeight = mid - top
    clipPath(bulbs) {
        // Top sand sinks toward the neck; bottom sand rises from the base.
        val topLevel = top + bulbHeight * (SAND_START + (1 - SAND_START) * fall)
        drawRect(sand, Offset(0f, topLevel), Size(w, mid - topLevel))
        val bottomLevel = bottom - bulbHeight * (1 - SAND_START) * fall
        drawRect(sand, Offset(0f, bottomLevel), Size(w, bottom - bottomLevel))
    }
    if (streaming) {
        drawLine(
            sand,
            Offset(w / 2, mid),
            Offset(w / 2, bottom - bulbHeight * (1 - SAND_START) * fall),
            strokeWidth =
                STREAM * density
        )
    }
    drawPath(bulbs, glass, style = Stroke(GLASS_STROKE * density))
    // The caps, top and bottom.
    for (y in listOf(cap / 2, h - cap / 2)) {
        drawLine(
            sand,
            Offset(w * CAP_INSET, y),
            Offset(w * (1 - CAP_INSET), y),
            strokeWidth = cap,
            cap = StrokeCap.Round
        )
    }
}

private const val SHOW_AFTER_MS = 120L
private const val UPPER_BIAS = -0.45f
private const val MIN_SHOWN_MS = 500L
private const val FADE_MS = 180
private const val VEIL_ALPHA = 0.55f
private const val RUN_MS = 1_400
private const val TURN_MS = 450
private const val HALF_TURN = 180f
private const val CAP = 0.08f
private const val CAP_INSET = 0.06f
private const val SIDE = 0.14f
private const val NECK = 0.05f
private const val NECK_SPAN = 0.06f
private const val CURVE = 0.22f
private const val SAND_START = 0.3f
private const val STREAM = 1.5f
private const val GLASS_STROKE = 2f

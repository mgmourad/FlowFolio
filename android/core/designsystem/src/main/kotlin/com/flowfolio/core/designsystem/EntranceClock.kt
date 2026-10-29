package com.flowfolio.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** The clock of one visit to a screen, in milliseconds since it appeared; [reveal] times its elements from it. */
@Stable
class EntranceClock internal constructor(still: Boolean) {
    internal val ms: Animatable<Float, AnimationVector1D> = Animatable(if (still) WAVE_END_MS else 0f)
}

val LocalEntrance = staticCompositionLocalOf<EntranceClock?> { null }

/**
 * Starts the entrance of a screen (ADR-0034): every time the screen is shown, its elements marked with [reveal] fall
 * into place one after another, like icons settling on a phone's home screen after unlocking. The screen itself is
 * moved by the navigation transition (ADR-0033).
 *
 * With [holdForLandings], a screen that a cover is flying home to (Back from details, closing a book) stays still, so
 * the cover lands on a still shelf (owner, 2026-10-18). The details page passes false: its cover flies in while the
 * rest of the page settles around it.
 */
@Composable
fun ScreenEntrance(
    modifier: Modifier = Modifier,
    holdForLandings: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val flights = LocalCoverFlights.current
    val opener = LocalBookOpener.current
    val clock = remember {
        val landing = flights?.flight != null || opener?.opening?.closing == true
        EntranceClock(still = holdForLandings && landing)
    }
    LaunchedEffect(clock) { clock.ms.animateTo(WAVE_END_MS, tween(WAVE_END_MS.toInt(), easing = LinearEasing)) }
    CompositionLocalProvider(LocalEntrance provides clock) { Box(modifier, content = content) }
}

/**
 * Falls into place as the screen appears (ADR-0034): starting [order] x 40 ms in, the element fades in and shrinks
 * from 112% to its size over 560 ms on Material's emphasized curve, so a screen's sections, rows and tiles arrive as a
 * wave. Elements composed after their turn (rows scrolled into view later) simply appear. Outside a [ScreenEntrance]
 * nothing moves.
 *
 * The fade has no offscreen buffer, which would clip glows and shadows to the element's bounds; the layer is dropped
 * once the element has landed, so a still screen pays nothing (ADR-0013).
 */
fun Modifier.reveal(order: Int): Modifier = composed {
    val clock = LocalEntrance.current ?: return@composed this
    val start = order.coerceIn(0, MAX_ORDER) * STAGGER_MS
    val falling by remember(clock, start) { derivedStateOf { clock.ms.value < start + FALL_MS } }
    if (!falling) return@composed this
    graphicsLayer {
        val t = ((clock.ms.value - start) / FALL_MS).coerceIn(0f, 1f)
        val settle = Fall.transform(t)
        alpha = Fall.transform((t / FADE_SHARE).coerceAtMost(1f))
        val scale = 1f + (FALL_SCALE - 1f) * (1f - settle)
        scaleX = scale
        scaleY = scale
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/**
 * Drops into place as a list appears, a cascade: starting [order] x 35 ms in, the row fades in while sliding down the
 * last few dp to its place over 420 ms, so a panel's rows pour in from the top one after another. Like [reveal], it
 * keys off the [ScreenEntrance] around it, rows composed after their turn simply appear, and the layer goes once the
 * row has landed.
 */
fun Modifier.cascade(order: Int): Modifier = composed {
    val clock = LocalEntrance.current ?: return@composed this
    val start = order.coerceIn(0, MAX_ORDER) * CASCADE_STAGGER_MS
    val falling by remember(clock, start) { derivedStateOf { clock.ms.value < start + CASCADE_MS } }
    if (!falling) return@composed this
    val drop = with(LocalDensity.current) { CASCADE_DROP.toPx() }
    graphicsLayer {
        val t = ((clock.ms.value - start) / CASCADE_MS).coerceIn(0f, 1f)
        val settle = Fall.transform(t)
        alpha = Fall.transform((t / FADE_SHARE).coerceAtMost(1f))
        translationY = -drop * (1f - settle)
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** Material 3's emphasized decelerate curve. */
private val Fall = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private const val STAGGER_MS = 40f
private const val FALL_MS = 560f
private const val MAX_ORDER = 12
private const val FALL_SCALE = 1.12f
private const val FADE_SHARE = 0.6f
private const val WAVE_END_MS = MAX_ORDER * STAGGER_MS + FALL_MS
private const val CASCADE_STAGGER_MS = 35f
private const val CASCADE_MS = 420f
private val CASCADE_DROP = 14.dp

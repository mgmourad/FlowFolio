package com.flowfolio.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The mock's screen entrance (`.screen` → `.screen.active`): fades in over 380 ms and rises 16 dp over 650 ms, on the
 * ease-out curve. Left out to keep memory flat (ADR-0013): the mock's 6 px focus blur and its slight scale. Each
 * frame of a changing blur, or of shadows and glows at a changing scale, leaves its own texture in the GPU cache
 * (about 230 MB together on the unfolded Fold). The fade has no offscreen buffer for the same reason.
 */
@Composable
fun ScreenEntrance(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    // Only the first time: coming back to a screen (closing a book, Back from details) finds it where it was, so a
    // cover flying home lands on a still shelf instead of one rising into place (owner, 2026-10-18).
    var played by rememberSaveable { mutableStateOf(false) }
    val alpha = remember { Animatable(if (played) 1f else 0f) }
    val rise = remember { Animatable(if (played) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        played = true
        launch { alpha.animateTo(1f, tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) }
        launch { rise.animateTo(0f, tween(Motion.SLOW_MS, easing = Motion.EaseOut)) }
    }
    val density = LocalDensity.current
    Box(
        modifier
            .graphicsLayer {
                this.alpha = alpha.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
                translationY = with(density) { ENTRANCE_RISE.toPx() } * rise.value
            },
        content = content
    )
}

/**
 * The mock's staggered reveal (`[data-r]`): the element fades in over 700 ms and rises 20 dp over 900 ms, starting
 * [order] x 70 ms after the screen appears, so a screen's sections arrive one after another. Like [ScreenEntrance],
 * only the first time the screen appears.
 */
fun Modifier.reveal(order: Int): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(false) }
    val alpha = remember { Animatable(if (played) 1f else 0f) }
    val rise = remember { Animatable(if (played) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        played = true
        delay(order.toLong() * STAGGER_MS)
        launch { alpha.animateTo(1f, tween(REVEAL_FADE_MS, easing = Motion.EaseOut)) }
        launch { rise.animateTo(0f, tween(REVEAL_RISE_MS, easing = Motion.EaseOut)) }
    }
    val density = LocalDensity.current
    graphicsLayer {
        this.alpha = alpha.value
        // Fade without an offscreen buffer, which would clip glows and shadows to the element's bounds.
        compositingStrategy = CompositingStrategy.ModulateAlpha
        translationY = with(density) { REVEAL_RISE.toPx() } * rise.value
    }
}

private val ENTRANCE_RISE = 16.dp
private val REVEAL_RISE = 20.dp
private const val STAGGER_MS = 70L
private const val REVEAL_FADE_MS = 700
private const val REVEAL_RISE_MS = 900

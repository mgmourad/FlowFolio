@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.flowfolio.core.model.Ambient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A glimpse of the atmosphere just chosen (owner, M5): a scene that fades up over the page, holds for a moment and
 * fades away, so putting a mood on is felt rather than merely read in a menu.
 *
 * The scenes are **drawn, not photographed**: rain is thousands of streaks, a fire is layered glow and rising
 * embers, the sea is banded swell and foam. Photographs would mean shipping licensed images for every ambience and
 * would sit oddly over a page of text; drawn scenes tint and move with the theme and cost nothing to carry.
 *
 * Nothing is drawn for [Ambient.SILENCE], or once the moment has passed.
 */
@Suppress("CyclomaticComplexMethod") // one branch per ambience; each scene is its own small function
@Composable
fun AtmosphereOverlay(ambient: Ambient?, token: Int, modifier: Modifier = Modifier) {
    if (ambient == null || ambient == Ambient.SILENCE) return
    val fade = remember { Animatable(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    // A new token is a new choice: show it again even for the same ambience.
    LaunchedEffect(token, ambient) {
        fade.snapTo(0f)
        fade.animateTo(1f, tween(FADE_IN_MS, easing = LinearEasing))
        fade.animateTo(1f, tween(HOLD_MS))
        fade.animateTo(0f, tween(FADE_OUT_MS, easing = LinearEasing))
    }
    LaunchedEffect(token, ambient) {
        val started = withFrameNanos { it }
        while (fade.value > 0f || fade.isRunning) {
            withFrameNanos { now -> time = (now - started) / 1_000_000_000f }
        }
    }
    val alpha = fade.value
    if (alpha <= 0f) return
    val seeded = remember(ambient) { Random(ambient.ordinal * 7919) }
    Canvas(modifier.fillMaxSize()) {
        when (ambient) {
            Ambient.RAIN -> rain(alpha, time, seeded)
            Ambient.FIREPLACE -> fireplace(alpha, time, seeded)
            Ambient.OCEAN -> ocean(alpha, time)
            Ambient.FOREST -> forest(alpha, time)
            Ambient.STREAM -> stream(alpha, time)
            Ambient.NIGHT_GARDEN -> nightGarden(alpha, time)
            Ambient.CAFE -> cafe(alpha, time)
            Ambient.BROWN_NOISE -> brownNoise(alpha, time)
            Ambient.SILENCE -> Unit
        }
    }
}

private const val FADE_IN_MS = 420
private const val HOLD_MS = 1100
private const val FADE_OUT_MS = 900

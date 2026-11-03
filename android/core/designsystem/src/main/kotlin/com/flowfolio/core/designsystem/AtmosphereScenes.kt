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

// The scenes AtmosphereOverlay paints, one small painter each (owner, M5).
//
// They are drawn, not photographed: rain is thousands of streaks, a fire is layered glow and rising embers, the sea
// is banded swell and foam. Photographs would mean shipping licensed images for every ambience and would sit oddly
// over a page of text; drawn scenes tint and move with the theme and cost nothing to carry.

/** A wash over the page, so every scene sits on something rather than floating on the words. */
private fun DrawScope.wash(alpha: Float, top: Color, bottom: Color) {
    drawRect(
        Brush.verticalGradient(listOf(top.copy(alpha = top.alpha * alpha), bottom.copy(alpha = bottom.alpha * alpha)))
    )
}

/** Slanting streaks at three depths, the nearest longest and brightest, with a few bursts where they land. */
internal fun DrawScope.rain(alpha: Float, t: Float, random: Random) {
    wash(alpha, Color(0x66121A2B), Color(0x99070C16))
    val lean = size.height * 0.08f
    repeat(3) { layer ->
        val depth = (layer + 1) / 3f
        val count = (70 * depth).toInt() + 20
        val speed = 900f * depth + 320f
        val length = size.height * (0.05f + 0.07f * depth)
        val shade = Color.White.copy(alpha = alpha * (0.05f + 0.16f * depth))
        repeat(count) {
            val x = random.nextFloat() * (size.width + lean) - lean
            val offset = random.nextFloat()
            val y = ((offset + t * speed / size.height) % 1f) * (size.height + length) - length
            drawLine(
                shade,
                Offset(x, y),
                Offset(x + lean * (0.4f + depth * 0.6f), y + length),
                strokeWidth = 0.8f + depth * 1.4f,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Banked glow from below, a flicker on top of it, and embers climbing out of the heat. */
internal fun DrawScope.fireplace(alpha: Float, t: Float, random: Random) {
    wash(alpha, Color(0x55120600), Color(0xAA2B0C00))
    val flicker = 0.82f + 0.18f * sin(t * 9f) * cos(t * 5.5f)
    val heart = Offset(size.width / 2f, size.height * 1.02f)
    drawCircle(
        Brush.radialGradient(
            listOf(
                Color(0xFFFFB347).copy(alpha = alpha * 0.55f * flicker),
                Color(0xFFFF6A00).copy(alpha = alpha * 0.28f * flicker),
                Color.Transparent
            ),
            center = heart,
            radius = size.height * 0.95f
        ),
        radius = size.height * 0.95f,
        center = heart
    )
    repeat(46) {
        val seed = random.nextFloat()
        val rise = (seed + t * 0.22f) % 1f
        val x = size.width * (0.12f + 0.76f * random.nextFloat()) + sin((rise + seed) * 9f) * size.width * 0.05f
        val y = size.height * (1.02f - rise * 1.05f)
        val fade = (1f - rise) * alpha
        drawCircle(
            Color(0xFFFFC46B).copy(alpha = fade * 0.75f),
            radius = (1.2f + 2.4f * (1f - rise)) * density,
            center = Offset(x, y)
        )
    }
}

/**
 * A settled value in 0..1 for thing [i]'s [channel], the same on every frame.
 *
 * The scenes are redrawn from scratch each frame, so anything that asked a generator for its position would be
 * somewhere else by the next one -- which is flicker, not movement. Hashing the index instead lets each leaf, star
 * and light keep its place and have only its motion change (owner, M5 round).
 */
private fun noise(i: Int, channel: Int): Float {
    var h = i * 374761393 + channel * 668265263
    h = (h xor (h shr 13)) * 1274126177
    return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
}

/**
 * Swell in filled bands against a lit horizon, the furthest flattest and palest, with foam only on the rising
 * faces -- which is where it gathers on real water, and what tells a wave from a wire.
 */
internal fun DrawScope.ocean(alpha: Float, t: Float) {
    wash(alpha, Color(0x55062A3F), Color(0xAA02131F))
    val horizon = size.height * 0.34f
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color(0xFF7FC7E8).copy(alpha = alpha * 0.10f), Color.Transparent),
            startY = horizon - size.height * 0.14f,
            endY = horizon + size.height * 0.10f
        )
    )
    repeat(SWELLS) { band ->
        val depth = band / (SWELLS - 1f)
        val baseY = horizon + size.height * (0.05f + 0.72f * depth * depth)
        val amp = size.height * (0.005f + 0.028f * depth)
        val speed = 0.35f + depth * 1.3f
        val body = Color(0xFF0E4D6B).copy(alpha = alpha * (0.10f + 0.24f * depth))
        val crest = Color(0xFFBFE9F8).copy(alpha = alpha * (0.06f + 0.16f * depth))
        val water = Path().apply { moveTo(0f, size.height) }
        val step = size.width / 56f
        var x = 0f
        var previous = Offset(0f, baseY)
        while (x <= size.width + step) {
            val phase = x / size.width * (2f + depth * 2.6f) * PI.toFloat()
            val y = baseY + sin(phase + t * speed) * amp + sin(phase * 2.7f - t * speed * 0.7f) * amp * 0.3f
            val point = Offset(x, y)
            water.lineTo(x, y)
            if (cos(phase + t * speed) > 0.3f) {
                drawLine(crest, previous, point, strokeWidth = (1f + 2.4f * depth) * density, cap = StrokeCap.Round)
            }
            previous = point
            x += step
        }
        water.lineTo(size.width, size.height)
        water.close()
        drawPath(water, body)
    }
}

/** Light leaning through a canopy, motes hanging in it, and leaves that fall in their own lanes. */
internal fun DrawScope.forest(alpha: Float, t: Float) {
    wash(alpha, Color(0x55061A0C), Color(0x99020D06))
    repeat(SHAFTS) { i ->
        val sway = sin(t * 0.42f + i * 1.7f)
        val x = size.width * (0.04f + 0.26f * i) + sway * size.width * 0.03f
        val width = size.width * (0.07f + 0.05f * noise(i, 1))
        val strength = alpha * (0.06f + 0.05f * (0.5f + 0.5f * sin(t * 0.6f + i)))
        rotate(degrees = 8f + sway * 3f, pivot = Offset(x + width / 2f, 0f)) {
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFE8FFD4).copy(alpha = strength), Color.Transparent)),
                topLeft = Offset(x, -size.height * 0.05f),
                size = Size(width, size.height * 0.95f)
            )
        }
    }
    // Motes drift; they do not fall.
    repeat(MOTES) { i ->
        val across = noise(i, 2)
        val down = noise(i, 3)
        val x = size.width * across + sin(t * (0.25f + down * 0.3f) + i) * size.width * 0.035f
        val y = size.height * down + cos(t * (0.2f + across * 0.25f) + i) * size.height * 0.03f
        drawCircle(
            Color(0xFFF2FFE0).copy(alpha = alpha * (0.10f + 0.20f * noise(i, 4))),
            radius = (0.7f + noise(i, 5)) * density,
            center = Offset(x, y)
        )
    }
    repeat(LEAVES) { i ->
        val fall = (noise(i, 7) + t * (0.055f + 0.045f * noise(i, 8))) % 1f
        val x = size.width * noise(i, 6) + sin(fall * 7f + i) * size.width * 0.05f
        val y = fall * (size.height + LEAF_MARGIN * density) - LEAF_MARGIN * 0.5f * density
        val leaf = (3.4f + 3.4f * noise(i, 9)) * density
        val turning = abs(sin(fall * 9f + i))
        val shade = if (noise(i, 10) > 0.72f) Color(0xFFD8C06A) else Color(0xFF8FD06A)
        drawOval(
            shade.copy(alpha = alpha * (0.28f + 0.3f * noise(i, 11))),
            topLeft = Offset(x, y),
            size = Size(leaf * (0.35f + 0.65f * turning), leaf * 1.8f)
        )
    }
}

/** Water running past: lines of flow at their own speeds, and rings opening where it breaks over something. */
internal fun DrawScope.stream(alpha: Float, t: Float) {
    wash(alpha, Color(0x4A08323C), Color(0x8A03161C))
    repeat(FLOW_LINES) { i ->
        val speed = 0.18f + 0.3f * noise(i, 2)
        val y = ((noise(i, 3) + t * speed) % 1f) * (size.height + FLOW_MARGIN * density) -
            FLOW_MARGIN * 0.5f * density
        val amp = size.width * (0.008f + 0.014f * noise(i, 4))
        val run = size.width * (0.25f + 0.6f * noise(i, 5))
        val from = size.width * noise(i, 1) - run / 2f
        val shade = Color(0xFFBFE9F5).copy(alpha = alpha * (0.07f + 0.13f * noise(i, 6)))
        val step = run / 14f
        var x = from
        var previous = Offset(from, y)
        while (x <= from + run) {
            val point = Offset(x, y + sin(x / size.width * 8f + t * 2.2f + i) * amp)
            drawLine(shade, previous, point, strokeWidth = (1f + 1.4f * noise(i, 7)) * density, cap = StrokeCap.Round)
            previous = point
            x += step
        }
    }
    repeat(RINGS) { i ->
        val open = (noise(i, 8) + t * 0.42f) % 1f
        val centre = Offset(size.width * noise(i, 9), size.height * noise(i, 10))
        val left = (1f - open) * (1f - open)
        drawCircle(
            Color(0xFFDFF6FF).copy(alpha = alpha * 0.22f * left),
            radius = (6f + 54f * open) * density,
            center = centre,
            style = Stroke(width = (1.6f - open) * density)
        )
    }
}

/** A moon off to one side, stars that twinkle where they stand, and fireflies trailing their own slow loops. */
internal fun DrawScope.nightGarden(alpha: Float, t: Float) {
    wash(alpha, Color(0x99070A24), Color(0xBB03040F))
    val moon = Offset(size.width * 0.78f, size.height * 0.14f)
    val halo = size.minDimension * 0.34f
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFE8EEFF).copy(alpha = alpha * 0.16f), Color.Transparent),
            center = moon,
            radius = halo
        ),
        radius = halo,
        center = moon
    )
    drawCircle(Color(0xFFF4F7FF).copy(alpha = alpha * 0.45f), radius = 7f * density, center = moon)
    repeat(STARS) { i ->
        val twinkle = 0.35f + 0.65f * abs(sin(t * (0.5f + 1.6f * noise(i, 3)) + noise(i, 4) * 6.2f))
        drawCircle(
            Color.White.copy(alpha = alpha * (0.18f + 0.34f * noise(i, 5)) * twinkle),
            radius = (0.5f + 1.1f * noise(i, 6)) * density,
            center = Offset(size.width * noise(i, 1), size.height * 0.72f * noise(i, 2))
        )
    }
    repeat(FIREFLIES) { i ->
        val lane = noise(i, 7)
        val loop = noise(i, 8)
        val breath = (sin(t * (1.1f + loop) + lane * 7f) + 1f) / 2f
        // A short trail, so a firefly is seen to be going somewhere.
        repeat(TRAIL) { k ->
            val back = t - k * 0.09f
            val x = size.width * (0.1f + 0.8f * lane) + sin(back * (0.55f + loop * 0.5f) + lane * 5f) *
                size.width * 0.09f
            val y = size.height * (0.38f + 0.56f * loop) + cos(back * (0.42f + lane * 0.4f) + loop * 4f) *
                size.height * 0.07f
            val near = 1f - k / TRAIL.toFloat()
            drawCircle(
                Color(0xFFCBFF8F).copy(alpha = alpha * 0.5f * breath * near * near),
                radius = (0.8f + 1.6f * near) * density,
                center = Offset(x, y)
            )
            if (k == 0) {
                drawCircle(
                    Color(0xFFCBFF8F).copy(alpha = alpha * 0.14f * breath),
                    radius = 10f * density,
                    center = Offset(x, y)
                )
            }
        }
    }
}

/** Warm lights out of focus rising slowly, steam off a cup just out of sight, and darker corners. */
internal fun DrawScope.cafe(alpha: Float, t: Float) {
    wash(alpha, Color(0x55231308), Color(0x99140A04))
    repeat(BOKEH) { i ->
        val rise = (noise(i, 2) + t * (0.012f + 0.02f * noise(i, 3))) % 1f
        val centre = Offset(
            size.width * noise(i, 1) + sin(t * 0.3f + i) * size.width * 0.015f,
            size.height * (1f - rise)
        )
        val r = (16f + 40f * noise(i, 4)) * density
        val warm = if (noise(i, 5) > 0.78f) Color(0xFFFFF0C8) else Color(0xFFFFD9A0)
        drawCircle(
            Brush.radialGradient(
                listOf(warm.copy(alpha = alpha * (0.10f + 0.12f * noise(i, 6))), Color.Transparent),
                center = centre,
                radius = r
            ),
            radius = r,
            center = centre
        )
    }
    repeat(STEAM) { i ->
        val climb = (noise(i, 7) + t * 0.1f) % 1f
        val centre = Offset(
            size.width * (0.16f + 0.1f * i) + sin(climb * 6f + i) * size.width * 0.05f,
            size.height * (1f - climb * 0.55f)
        )
        val r = (10f + 26f * climb) * density
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = alpha * 0.07f * (1f - climb)), Color.Transparent),
                center = centre,
                radius = r
            ),
            radius = r,
            center = centre
        )
    }
    val middle = Offset(size.width / 2f, size.height / 2f)
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color(0xFF120803).copy(alpha = alpha * 0.4f)),
            center = middle,
            radius = size.maxDimension * 0.62f
        )
    )
}

/**
 * A haze with no edges in it: broad soft swells crossing each other, and one slow breath over the whole page.
 *
 * The bands this replaced read as a test pattern; a sound with no events in it should have no hard lines either.
 */
internal fun DrawScope.brownNoise(alpha: Float, t: Float) {
    wash(alpha, Color(0x66241C16), Color(0x88120D09))
    repeat(HAZE_SWELLS) { i ->
        val across = noise(i, 1)
        val down = noise(i, 2)
        val centre = Offset(
            size.width * (0.5f + 0.55f * sin(t * (0.07f + 0.06f * across) + across * 6.2f)),
            size.height * (0.5f + 0.55f * cos(t * (0.06f + 0.05f * down) + down * 5.1f))
        )
        val r = size.maxDimension * (0.28f + 0.22f * across)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFB9A38F).copy(alpha = alpha * (0.05f + 0.04f * down)), Color.Transparent),
                center = centre,
                radius = r
            ),
            radius = r,
            center = centre
        )
    }
    drawRect(Color(0xFFD8C8B4).copy(alpha = alpha * 0.03f * (0.5f + 0.5f * sin(t * 0.5f))))
}

/** How many of each thing a scene draws, and how far past the edges the falling ones start and end. */
private const val SWELLS = 6
private const val SHAFTS = 4
private const val MOTES = 40
private const val LEAVES = 26
private const val LEAF_MARGIN = 40f
private const val FLOW_LINES = 26
private const val FLOW_MARGIN = 60f
private const val RINGS = 7
private const val STARS = 110
private const val FIREFLIES = 12
private const val TRAIL = 6
private const val BOKEH = 20
private const val STEAM = 3
private const val HAZE_SWELLS = 7

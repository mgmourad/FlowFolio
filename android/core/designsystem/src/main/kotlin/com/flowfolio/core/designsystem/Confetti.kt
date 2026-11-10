// Confetti for anything that reaches 100% (owner, M4).
@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.flowfolio.core.model.BookmarkColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One piece of confetti: where it is thrown, how fast, and how it tumbles. Everything is in fractions of the burst's
 * own box, so the same piece works over a 6 dp bar and an 84 dp ring.
 */
private data class Piece(
    val fromX: Float,
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val width: Float,
    val ratio: Float,
    val colorIndex: Int
)

/**
 * A burst of confetti, thrown upward and out from the bottom of its box and pulled back down (owner, M4). It draws
 * nothing until [play] turns true, calls [onEnd] when the pieces have fallen, and takes its colours from the theme.
 *
 * It is laid over whatever is celebrating rather than inside it, so it is not clipped by a progress bar's rounded
 * corners; give it a box taller than the thing it celebrates.
 */
@Composable
fun FlowFolioConfetti(play: Boolean, modifier: Modifier = Modifier, onEnd: () -> Unit = {}) {
    if (!play) return
    // The ribbon hues: festive in every theme, where the palette itself is now quiet (ADR-0052).
    val colors = listOf(
        BookmarkColor.SUB_ITEM.color,
        BookmarkColor.TITLE.color,
        BookmarkColor.ITEM.color,
        BookmarkColor.GREEN.color,
        BookmarkColor.ACCENT.color
    )
    // Fixed once per burst: re-rolling them every frame would make the pieces jitter rather than fly.
    val pieces = remember {
        val random = Random(SEED)
        List(PIECES) {
            Piece(
                fromX = random.nextFloat(),
                // Up and out: -150 to -30 degrees, so nothing is thrown straight down.
                angle = (-150f + random.nextFloat() * 120f) * (PI.toFloat() / 180f),
                speed = 0.55f + random.nextFloat() * 0.75f,
                spin = (random.nextFloat() - 0.5f) * 12f,
                width = 0.018f + random.nextFloat() * 0.018f,
                ratio = 0.5f + random.nextFloat() * 1.2f,
                colorIndex = random.nextInt(colors.size)
            )
        }
    }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
        onEnd()
    }
    val t = clock.value
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        // The last quarter of the burst fades out, so pieces don't blink away mid-air.
        val fade = ((1f - t) / FADE_FROM).coerceIn(0f, 1f)
        pieces.forEach { piece ->
            val travel = piece.speed * t
            val x = piece.fromX * w + cos(piece.angle) * travel * w
            val y = h + sin(piece.angle) * travel * h * RISE + GRAVITY * t * t * h
            if (y > h * BELOW) return@forEach
            val pieceWidth = piece.width * w
            val pieceHeight = pieceWidth * piece.ratio
            rotate(degrees = piece.spin * t * FULL_TURN, pivot = Offset(x, y)) {
                drawRect(
                    colors[piece.colorIndex].copy(alpha = fade),
                    topLeft = Offset(x - pieceWidth / 2f, y - pieceHeight / 2f),
                    size = Size(pieceWidth, pieceHeight)
                )
            }
        }
    }
}

/**
 * Whether [progress] has just reached its end and deserves a burst (owner, M4: every progress bar that fills).
 *
 * A bar animates up from zero every time it is composed, so "the value crossed 1" on its own would fire on each visit
 * to the screen. This looks at the target instead, and fires when:
 *  - it watched the target go from short of the end to the end (the reader finished it while looking), or
 *  - it is the first time this run of the app has seen [id] already complete, so coming back to the library after
 *    reaching a goal still celebrates once.
 *
 * Without an [id] only the first case fires, since there is nothing to remember a bar by. Dropping back under 1
 * (a goal raised, a book reopened) clears it, so the next completion celebrates again.
 *
 * The caller draws [FlowFolioConfetti] while the returned state is true and sets it false when the burst ends.
 */
@Composable
fun rememberCelebration(progress: Float, id: Any? = null): MutableState<Boolean> {
    val complete = progress >= 1f
    val play = remember(id) { mutableStateOf(false) }
    var seen by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, complete) {
        if (!complete) {
            if (id != null) celebrated.remove(id)
            seen = true
            return@LaunchedEffect
        }
        val watched = seen
        seen = true
        if (watched || (id != null && celebrated.add(id))) play.value = true
    }
    return play
}

/**
 * Which completions this run of the app has already celebrated. Deliberately not stored: a burst is worth seeing once
 * per sitting, and keeping it would mean a schema change for something nobody would miss after a restart.
 */
private val celebrated = mutableSetOf<Any>()

/** How tall a burst should be over the thing it celebrates. */
val ConfettiHeight = 96.dp

private const val PIECES = 26
private const val BURST_MS = 1500
private const val SEED = 0x1F0
private const val FADE_FROM = 0.3f
private const val RISE = 1.6f
private const val GRAVITY = 1.5f
private const val BELOW = 1.4f
private const val FULL_TURN = 360f

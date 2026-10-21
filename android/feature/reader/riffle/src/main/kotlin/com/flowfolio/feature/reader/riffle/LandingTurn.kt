package com.flowfolio.feature.reader.riffle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlin.math.abs

/** Draws [page] (null: nothing, before the cover) filling the modifier's bounds: the host's pages or thumbnails. */
typealias PageContent = @Composable (page: Int?, modifier: Modifier) -> Unit

/**
 * The landing turn (spec 5.4), drawn over the host's page area, which already shows the target. Up to seven sheets
 * turn over the spine toward it with the units between sampled evenly ([LandingPlan]): 520 ms each, 70 ms apart, one
 * 700 ms turn for a single step; each sheet shades as it lifts. Until the sheets cover it, the old page on the side
 * they land on stays in view. In [spread] layout sheets cover half the area; in single pages, all of it.
 * [reducedMotion]: the old view fades out instead (spec 6). Calls [onDone] when finished.
 */
@Composable
fun LandingTurn(
    book: BookGeometry,
    from: Int,
    to: Int,
    spread: Boolean,
    reducedMotion: Boolean,
    page: PageContent,
    onDone: () -> Unit
) {
    if (from == to) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    if (reducedMotion) {
        Crossfade(book, from, spread, page, onDone)
        return
    }
    val plan = remember(from, to) { LandingPlan.between(from, to) }
    val time = remember(from, to) { Animatable(0f) }
    LaunchedEffect(from, to) {
        time.animateTo(plan.totalMs.toFloat(), tween(plan.totalMs, easing = LinearEasing))
        onDone()
    }
    val forward = (to > from) != book.rightToLeft
    val units = listOf(from) + plan.steps
    Box(Modifier.fillMaxSize()) {
        if (spread) {
            // The facing page the sheets land on, from the old spread, until they cover it.
            val (left, right) = miniSpread(book, book.pagesOf(from))
            page(if (forward) left else right, Modifier.half(this, start = forward).zIndex(1f))
        }
        for (k in 1 until units.size) {
            Sheet(book, units[k - 1], units[k], k, plan, forward, spread, time, page)
        }
    }
}

@Composable
private fun BoxScope.Sheet(
    book: BookGeometry,
    before: Int,
    after: Int,
    k: Int,
    plan: LandingPlan,
    forward: Boolean,
    spread: Boolean,
    time: Animatable<Float, *>,
    page: PageContent
) {
    val progress = ((time.value - (k - 1) * plan.staggerMs) / plan.sheetMs).coerceIn(0f, 1f)
    val angle = SHEET_EASING.transform(progress) * HALF_TURN
    // Sheets still waiting lie on top in order; turned sheets stack in the order they land.
    val z = if (progress > HALF) LANDED_Z + k else WAITING_Z - k
    val old = if (spread) miniSpread(book, book.pagesOf(before)) else book.pagesOf(before).first to null
    val new = if (spread) miniSpread(book, book.pagesOf(after)) else book.pagesOf(after).first to null
    val front = if (!spread) {
        old.first
    } else if (forward) {
        old.second
    } else {
        old.first
    }
    val back = if (!spread) {
        new.first
    } else if (forward) {
        new.first
    } else {
        new.second
    }
    val turned = angle > QUARTER_TURN
    Box(
        Modifier
            .half(this, start = !forward, whole = !spread)
            .zIndex(z.toFloat())
            .graphicsLayer {
                rotationY = if (forward) -angle else angle
                transformOrigin = TransformOrigin(if (forward) 0f else 1f, HALF)
                cameraDistance = CAMERA * density
            }
    ) {
        // The back face is seen mirrored: flip it back so its text reads the right way.
        page(if (turned) back else front, Modifier.fillMaxSize().graphicsLayer { if (turned) scaleX = -1f })
        val shade = (1f - abs(progress - HALF) / HALF).coerceIn(0f, 1f) * SHADE
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    if (forward ==
                        !turned
                    ) {
                        listOf(Color.Black.copy(alpha = shade), Color.Transparent)
                    } else {
                        listOf(Color.Transparent, Color.Black.copy(alpha = shade))
                    }
                )
            )
        )
    }
}

/** Reduced motion: the old view fades out over the new one. */
@Composable
private fun Crossfade(book: BookGeometry, from: Int, spread: Boolean, page: PageContent, onDone: () -> Unit) {
    val alpha = remember(from) { Animatable(1f) }
    LaunchedEffect(from) {
        alpha.animateTo(0f, tween(CROSSFADE_MS))
        onDone()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) {
        if (spread) {
            val (left, right) = miniSpread(book, book.pagesOf(from))
            page(left, Modifier.half(this, start = true))
            page(right, Modifier.half(this, start = false))
        } else {
            page(book.pagesOf(from).first, Modifier.fillMaxSize())
        }
    }
}

/** The left ([start]) or right half of the area, or all of it when [whole]. */
private fun Modifier.half(scope: BoxScope, start: Boolean, whole: Boolean = false): Modifier = with(scope) {
    if (whole) {
        fillMaxSize()
    } else {
        fillMaxHeight().fillMaxWidth(HALF).align(if (start) Alignment.CenterStart else Alignment.CenterEnd)
    }
}

private val SHEET_EASING = CubicBezierEasing(0.45f, 0.05f, 0.25f, 1f)
private const val HALF = 0.5f
private const val HALF_TURN = 180f
private const val QUARTER_TURN = 90f
private const val WAITING_Z = 20
private const val LANDED_Z = 30
private const val SHADE = 0.35f
private const val CAMERA = 40f
private const val CROSSFADE_MS = 220

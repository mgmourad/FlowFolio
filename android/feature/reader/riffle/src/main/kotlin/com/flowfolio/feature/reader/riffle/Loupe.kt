@file:Suppress("MagicNumber") // the mock's loupe: sizes, gradient stops and skeleton proportions, carried over

package com.flowfolio.feature.reader.riffle

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.DisplayFamily
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.UiFamily
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The loupe (spec 4.4): a card above the strip showing the spread under the ribbon as a miniature, with the chapter,
 * the printed pages, a progress bar and, while fine scrubbing, the speed. It follows the ribbon with a slight lag,
 * tilts against the motion, and riffles a mini sheet over its spine each time the spread changes (spec 5.5). Its
 * surface is translucent without a live blur: an animated blur fills the GPU cache (ADR-0022).
 */
@Composable
internal fun Loupe(
    state: RiffleScrubberState,
    thumbnails: PageThumbnailProvider?,
    stripLeft: Float,
    modifier: Modifier = Modifier
) {
    val palette = FlowFolio.palette
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val flips = remember { mutableStateListOf<MiniFlip>() }
    var flipIds by remember { mutableIntStateOf(0) }
    val unit = state.previewUnit
    LaunchedEffect(state) {
        var last = state.previewUnit
        var lastAt = SystemClock.uptimeMillis()
        snapshotFlow { state.previewUnit to state.live }.collect { (now, live) ->
            val at = SystemClock.uptimeMillis()
            if (live && now != last) {
                flips += MiniFlip(flipIds++, last, now, LoupeFlips.durationMs(at - lastAt))
                while (flips.size > LoupeFlips.MAX_IN_FLIGHT) flips.removeAt(0)
                thumbnails?.prefetch(prefetchAround(state.book, now, now - last))
            }
            last = now
            lastAt = at
        }
    }
    Box(
        modifier
            .onSizeChanged { size = it }
            .graphicsLayer { followRibbon(state, stripLeft, size) }
            .width(252.dp)
            .background(palette.surface.copy(alpha = 0.94f), RoundedCornerShape(20.dp))
            .border(1.dp, palette.edge, RoundedCornerShape(20.dp))
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(162.dp).clip(RoundedCornerShape(4.dp))) {
                Row(Modifier.fillMaxSize()) {
                    val (left, right) = miniSpread(state.book, state.book.pagesOf(unit))
                    MiniPage(state.book, left, thumbnails, Modifier.weight(1f).fillMaxHeight())
                    MiniPage(state.book, right, thumbnails, Modifier.weight(1f).fillMaxHeight())
                }
                flips.forEach { flip ->
                    key(flip.id) { FlipSheet(state.book, flip, thumbnails) { flips.remove(flip) } }
                }
                Gutter(Modifier.align(Alignment.Center))
            }
            LoupeCaption(state, unit)
        }
        Tail(state, stripLeft, size, Modifier.align(Alignment.BottomStart))
    }
}

/** Places the loupe above the ribbon, eased and tilted, faded and scaled by how shown it is (the mock's motion). */
private fun GraphicsLayerScope.followRibbon(state: RiffleScrubberState, stripLeft: Float, size: IntSize) {
    state.frame // moves with the strip's frames, without recomposing
    val shown = state.loupeShown
    alpha = shown
    val scale = 0.86f + 0.14f * shown
    scaleX = scale
    scaleY = scale
    rotationZ = state.loupeTilt
    transformOrigin = TransformOrigin(0.5f, 1f)
    val edge = 12 * density
    val maxX = ((state.geometry?.width ?: 0f) + stripLeft * 2 - size.width - edge).coerceAtLeast(edge)
    translationX = (stripLeft + state.loupeX - size.width / 2f).coerceIn(edge, maxX)
    translationY = -size.height - edge
}

/** A sheet turning over the loupe's spine, from [MiniFlip.from]'s spread to [MiniFlip.to]'s. */
private class MiniFlip(val id: Int, val from: Int, val to: Int, val durationMs: Long)

@Composable
private fun FlipSheet(book: BookGeometry, flip: MiniFlip, thumbnails: PageThumbnailProvider?, onDone: () -> Unit) {
    val angle = remember { Animatable(0f) }
    val forward = flip.to > flip.from
    LaunchedEffect(flip.id) {
        angle.animateTo(if (forward) -180f else 180f, tween(flip.durationMs.toInt(), easing = FLIP_EASING))
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        // Forward: the old right page turns over to become the new left page; backward the other way.
        val from = miniSpread(book, book.pagesOf(flip.from))
        val to = miniSpread(book, book.pagesOf(flip.to))
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                .graphicsLayer {
                    rotationY = angle.value
                    transformOrigin = TransformOrigin(if (forward) 0f else 1f, 0.5f)
                    cameraDistance = 12f * density
                }
        ) {
            val past = kotlin.math.abs(angle.value) > 90f
            val page = when {
                !past && forward -> from.second
                !past -> from.first
                forward -> to.first
                else -> to.second
            }
            // The back face is seen mirrored: flip it back so its text reads the right way.
            MiniPage(book, page, thumbnails, Modifier.fillMaxSize().graphicsLayer { if (past) scaleX = -1f })
        }
    }
}

/** One page of the miniature: its thumbnail when ready, else a skeleton of a page (spec 3.5). */
@Composable
private fun MiniPage(book: BookGeometry, page: Int?, thumbnails: PageThumbnailProvider?, modifier: Modifier) {
    val palette = FlowFolio.palette
    var image by remember(page) { mutableStateOf(page?.let { thumbnails?.cached(it) }) }
    // A thumbnail freed by a paper change is fetched again, in the new paper.
    val usable = image?.takeIf { !it.isRecycled() }
    if (usable == null && page != null && thumbnails != null) {
        LaunchedEffect(page, image) { image = thumbnails.load(page)?.takeIf { !it.isRecycled() } }
    }
    val paper = palette.paper
    val ink = palette.paperInk
    val accent = palette.title
    Canvas(modifier.background(if (page == null) palette.backgroundDeep else paper)) {
        val bmp = usable
        when {
            page == null -> Unit
            bmp != null && drawThumbnail(bmp) -> Unit
            else -> drawSkeleton(page, ink, accent, isRecto = page % 2 == 0)
        }
        if (page != null) {
            val fromSpine = book.rightToLeft != (page % 2 == 0)
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.28f),
                    1f to Color.Transparent,
                    startX = if (fromSpine) 0f else size.width,
                    endX = if (fromSpine) size.width * 0.18f else size.width * 0.82f
                )
            )
        }
    }
}

@Composable
private fun Gutter(modifier: Modifier) {
    val shade = Color.Black.copy(alpha = if (FlowFolio.palette.dark) 0.5f else 0.24f)
    Box(
        modifier
            .width(22.dp)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.45f to shade,
                    0.55f to shade,
                    1f to Color.Transparent
                )
            )
    )
}

@Composable
private fun LoupeCaption(state: RiffleScrubberState, unit: Int) {
    val palette = FlowFolio.palette
    Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            RiffleLabels.section(state.book, unit),
            Modifier.weight(1f),
            color = palette.subItem,
            style = TextStyle(
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 0.6.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            RiffleLabels.pages(state.book, unit),
            color = palette.title,
            style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        )
    }
    speedLabel(state.scrubSpeed)?.let {
        Text(
            it,
            color = palette.item,
            style = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        )
    }
    val fraction = state.mapping.fractionOf(unit).let { if (state.book.rightToLeft) 1 - it else it }
    Box(
        Modifier
            .padding(top = 7.dp)
            .fillMaxWidth()
            .height(4.dp)
            .background(palette.hint.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(palette.title, palette.subItem)), RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun speedLabel(speed: ScrubSpeed): String? = when (speed) {
    ScrubSpeed.NORMAL -> null
    ScrubSpeed.HALF -> stringResource(R.string.riffle_speed_half)
    ScrubSpeed.QUARTER -> stringResource(R.string.riffle_speed_quarter)
    ScrubSpeed.FINE -> stringResource(R.string.riffle_speed_fine)
}

/** The pointer under the loupe, aimed at the ribbon (or the hovered flag). */
@Composable
private fun Tail(state: RiffleScrubberState, stripLeft: Float, card: IntSize, modifier: Modifier) {
    val color = FlowFolio.palette.surface.copy(alpha = 0.94f)
    Box(
        modifier
            .graphicsLayer {
                state.frame
                val cardLeft = stripLeft + state.loupeX - card.width / 2f
                val clamped = cardLeft.coerceAtLeast(12 * density)
                // Before the card is measured there is nowhere to put the tail.
                val inset = 22 * density
                val x = if (card.width >
                    2 * inset
                ) {
                    (stripLeft + state.loupeX - clamped).coerceIn(inset, card.width - inset)
                } else {
                    0f
                }
                translationX = x - 9 * density - 12 * density
                translationY = 9 * density + 10 * density
                rotationZ = 45f
            }
            .size(18.dp)
            .background(color)
    )
}

private val FLIP_EASING = CubicBezierEasing(0.3f, 0.1f, 0.3f, 1f)

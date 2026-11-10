@file:Suppress("MagicNumber")

package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.glass
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Shown over the pages while a book is loading or an EPUB is laid out again: a statement square glass card with
 * blurred haze, an open book flipping pages like the riffle bar, and a sleek loading bar underneath whose progress
 * swings smoothly left and right.
 */
@Composable
internal fun BoxScope.RelayoutHourglass(working: Boolean = false, visible: Boolean = false) {
    var shown by remember { mutableStateOf(visible) }
    LaunchedEffect(working, visible) {
        if (visible) {
            shown = true
        } else if (working) {
            delay(SHOW_AFTER_MS)
            shown = true
        } else if (shown) {
            delay(MIN_SHOWN_MS)
            shown = false
        }
    }
    val p = FlowFolio.palette
    val tone = LocalTone.current
    val boards = remember(tone) { boardColors(tone) }
    val label = stringResource(R.string.reader_relayout)
    AnimatedVisibility(
        shown,
        Modifier.matchParentSize(),
        enter = fadeIn(tween(FADE_MS)),
        exit = fadeOut(tween(FADE_MS))
    ) {
        Box(Modifier.fillMaxSize().background(tone.paper.copy(alpha = VEIL_ALPHA))) {
            Box(
                Modifier
                    // In the upper-middle part of the page so the "Aa" sheet never covers it.
                    .align(if (visible) Alignment.Center else BiasAlignment(0f, UPPER_BIAS))
                    .size(LOADER_SQUARE_SIZE)
                    .glass(RoundedCornerShape(32.dp))
                    .padding(22.dp)
                    .semantics { contentDescription = label }
                    .testTag(ReaderTestTags.RELAYOUT),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FlippingBookGraphic(
                        coverColor = boards.main,
                        pageColor = boards.pageColor ?: if (p.dark) Color(0xFFD6CBB8) else Color(0xFFF3EBDB),
                        inkColor = if (p.dark) p.backgroundDeep else Color(0xFF1F1712),
                        accentColor = p.ink,
                        modifier = Modifier.size(88.dp, 60.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    SwingingLoadingBar(
                        trackColor = p.ink2.copy(alpha = 0.24f),
                        barStart = p.buttonTop,
                        barEnd = p.ink,
                        modifier = Modifier.width(104.dp).height(5.dp)
                    )
                }
            }
        }
    }
}

/** What shows over the pages for a moment: the pinch's text size, and the flipping book while the book is set again. */
@Composable
internal fun BoxScope.PageOverlays(s: ReaderState.Ready, pinch: TextPinch, viewModel: ReaderViewModel) {
    TextPinchBadge(pinch)
    val relayingOut by viewModel.relayingOut.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalTone provides s.tone) { RelayoutHourglass(working = relayingOut) }
}

/** Kept for compatibility with tests referencing [Hourglass]: draws the flipping book graphic. */
@Composable
internal fun Hourglass(glass: Color, sand: Color, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    FlippingBookGraphic(
        coverColor = sand,
        pageColor = if (p.dark) Color(0xFFD6CBB8) else Color(0xFFF3EBDB),
        inkColor = glass,
        accentColor = sand,
        modifier = modifier
    )
}

@Composable
private fun FlippingBookGraphic(
    coverColor: Color,
    pageColor: Color,
    inkColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val cycle = rememberInfiniteTransition(label = "flippingBook")
    val flipPhase by cycle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(FLIP_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "flipPhase"
    )
    val floatBob by cycle.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BOB_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatBob"
    )
    val pagesPath = remember { Path() }
    val coverPath = remember { Path() }
    val flippingPath = remember { Path() }

    Canvas(modifier) {
        drawFlippingBook(
            coverColor = coverColor,
            pageColor = pageColor,
            inkColor = inkColor,
            accentColor = accentColor,
            flipPhase = flipPhase,
            bob = floatBob,
            pagesPath = pagesPath,
            coverPath = coverPath,
            flippingPath = flippingPath
        )
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawFlippingBook(
    coverColor: Color,
    pageColor: Color,
    inkColor: Color,
    accentColor: Color,
    flipPhase: Float,
    bob: Float,
    pagesPath: Path,
    coverPath: Path,
    flippingPath: Path
) {
    val d = density
    val cx = size.width / 2f
    val cy = size.height / 2f + bob * 1.8f * d + 4f * d
    val halfW = size.width * 0.42f
    val halfH = size.height * 0.34f
    val lineStroke = Stroke(width = 1.8f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Soft ambient pedestal shadow under the open book
    drawOval(
        color = accentColor.copy(alpha = 0.14f),
        topLeft = Offset(cx - halfW * 0.9f, cy + halfH - 1f * d),
        size = Size(halfW * 1.8f, 7f * d)
    )

    // Cover board rim underneath the pages
    coverPath.rewind()
    coverPath.moveTo(cx - halfW - 3.5f * d, cy - halfH + 4.5f * d)
    coverPath.lineTo(cx - halfW - 3.5f * d, cy + halfH + 2f * d)
    coverPath.quadraticTo(cx - halfW * 0.5f, cy + halfH - 2f * d, cx, cy + halfH + 3f * d)
    coverPath.quadraticTo(cx + halfW * 0.5f, cy + halfH - 2f * d, cx + halfW + 3.5f * d, cy + halfH + 2f * d)
    coverPath.lineTo(cx + halfW + 3.5f * d, cy - halfH + 4.5f * d)
    coverPath.close()
    drawPath(coverPath, coverColor)
    drawPath(coverPath, inkColor, style = lineStroke)

    // Open left & right pages bowing upward from the spine
    pagesPath.rewind()
    pagesPath.moveTo(cx, cy - halfH + 3.5f * d)
    pagesPath.quadraticTo(cx - halfW * 0.5f, cy - halfH - 2f * d, cx - halfW, cy - halfH + 1.5f * d)
    pagesPath.lineTo(cx - halfW, cy + halfH - 2f * d)
    pagesPath.quadraticTo(cx - halfW * 0.5f, cy + halfH - 6f * d, cx, cy + halfH - 1f * d)
    pagesPath.quadraticTo(cx + halfW * 0.5f, cy + halfH - 6f * d, cx + halfW, cy + halfH - 2f * d)
    pagesPath.lineTo(cx + halfW, cy - halfH + 1.5f * d)
    pagesPath.quadraticTo(cx + halfW * 0.5f, cy - halfH - 2f * d, cx, cy - halfH + 3.5f * d)
    pagesPath.close()
    drawPath(pagesPath, pageColor)
    drawPath(pagesPath, inkColor, style = lineStroke)

    // Spine line
    val topY = cy - halfH + 3.5f * d
    val bottomY = cy + halfH - 1f * d
    drawLine(inkColor, Offset(cx, topY), Offset(cx, bottomY), strokeWidth = 1.6f * d, cap = StrokeCap.Round)

    // Flipping leaves arching gracefully across the spine (right-to-left like reading forward)
    for (k in 0 until FLIP_PAGES) {
        val p = (flipPhase + k.toFloat() / FLIP_PAGES) % 1f
        val angle = Math.PI.toFloat() * p
        val outer = cos(angle) * halfW
        val lift = sin(angle) * 8.5f * d
        val shade = lerp(pageColor, Color.White, sin(angle) * 0.18f)
        flippingPath.rewind()
        flippingPath.moveTo(cx, topY)
        flippingPath.quadraticTo(cx + outer * 0.5f, topY - lift - 2.5f * d, cx + outer, topY - lift)
        flippingPath.lineTo(cx + outer, bottomY - lift)
        flippingPath.quadraticTo(cx + outer * 0.5f, bottomY - lift * 0.6f, cx, bottomY)
        flippingPath.close()
        drawPath(flippingPath, shade)
        drawPath(flippingPath, inkColor, style = lineStroke, alpha = 0.88f)
    }
}

@Composable
private fun SwingingLoadingBar(trackColor: Color, barStart: Color, barEnd: Color, modifier: Modifier = Modifier) {
    val cycle = rememberInfiniteTransition(label = "swingBar")
    val swing by cycle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWING_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swing"
    )
    Canvas(modifier) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val thumbW = size.width * THUMB_FRACTION
        val thumbX = (size.width - thumbW) * swing
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(barStart, barEnd),
                startX = thumbX,
                endX = thumbX + thumbW
            ),
            topLeft = Offset(thumbX, 0f),
            size = Size(thumbW, size.height),
            cornerRadius = radius
        )
    }
}

private val LOADER_SQUARE_SIZE = 160.dp
private const val SHOW_AFTER_MS = 120L
private const val UPPER_BIAS = -0.45f
private const val MIN_SHOWN_MS = 500L
private const val FADE_MS = 180
private const val VEIL_ALPHA = 0.55f
private const val FLIP_CYCLE_MS = 1_350
private const val BOB_MS = 1_100
private const val SWING_MS = 950
private const val FLIP_PAGES = 3
private const val THUMB_FRACTION = 0.38f

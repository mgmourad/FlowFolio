// The icon art below is the mock's SVG (200-unit viewBox), redrawn verbatim; the numbers are its coordinates.
@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The mock's splash (`#s-splash`) on cold start: the icon pops in on the spring, its ribbon draws itself and the
 * loose page curls into place; then the "FlowFolio" wordmark and the tagline rise in. The mock's timings are halved so
 * it is over in about 1.4 s, then it fades (300 ms) and calls [onDone]. A tap skips it.
 */
@Composable
fun FlowFolioSplash(tagline: String, onDone: () -> Unit) {
    val p = FlowFolio.palette
    val done by rememberUpdatedState(onDone)
    val pop = remember { Animatable(0f) }
    val ribbon = remember { Animatable(0f) }
    val ribbon2 = remember { Animatable(0f) }
    val curl = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, tween(half(1000), easing = Motion.Spring)) }
        launch { ribbon.animateTo(1f, tween(half(1400), half(350), Motion.EaseOut)) }
        launch { ribbon2.animateTo(1f, tween(half(1400), half(900), Motion.EaseOut)) }
        launch { curl.animateTo(1f, tween(half(1100), half(500), Motion.EaseOut)) }
        launch { word.animateTo(1f, tween(half(1000), half(900), Motion.EaseOut)) }
        launch { tag.animateTo(1f, tween(half(1000), half(1150), Motion.EaseOut)) }
        delay(HOLD_MS)
        out.animateTo(0f, tween(FADE_MS, easing = Motion.EaseOut))
        done()
    }
    Box(
        Modifier
            .fillMaxSize()
            .appBackground(p)
            .graphicsLayer {
                alpha = out.value
                // Fades without a screen-sized offscreen buffer, which would linger in the GPU cache.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .clickable(remember { MutableInteractionSource() }, indication = null) { done() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Canvas(
                Modifier.size(150.dp).graphicsLayer {
                    // `@keyframes pop`: from scale .6, rotate -6deg, transparent.
                    val s = 0.6f + 0.4f * pop.value
                    scaleX = s
                    scaleY = s
                    rotationZ = -6f * (1f - pop.value)
                    alpha = pop.value.coerceIn(0f, 1f)
                    // Drawn at rest size into its own layer, then scaled and turned: the blurred shadows are not
                    // re-rasterized for every scale step (each step would leave a texture in the GPU cache).
                    compositingStrategy = CompositingStrategy.Offscreen
                }
            ) {
                scale(size.minDimension / ART, pivot = Offset.Zero) {
                    drawIcon(ribbon.value, ribbon2.value, curl.value)
                }
            }
            Wordmark(Modifier.rise(word.value))
            Text(
                tagline,
                style = FlowFolioType.sub.copy(fontSize = 14.sp).merge(FlowFolio.hint),
                modifier = Modifier.rise(tag.value)
            )
        }
    }
}

/** "Flow" in the item colour (italic) and "Folio" in the title colour, as in the mock's `.wordmark`. */
@Composable
private fun Wordmark(modifier: Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight(380))) {
                withStyle(FlowFolio.item.toSpanStyle()) { append("Flow") }
            }
            withStyle(FlowFolio.title.toSpanStyle()) { append("Folio") }
        },
        style = FlowFolioType.screenTitle.copy(fontSize = 44.sp, fontWeight = FontWeight(700)),
        modifier = modifier
    )
}

private fun half(ms: Int) = ms / 2

/** `@keyframes rise`: from 14 px lower and transparent. */
private fun Modifier.rise(progress: Float) = graphicsLayer {
    alpha = progress
    // No offscreen buffer while fading: it would clip the wordmark's glow to the text box.
    compositingStrategy = CompositingStrategy.ModulateAlpha
    translationY = 14.dp.toPx() * (1f - progress)
}

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private val ICON_PLATE_INNER =
    path("M47 6h106a41 41 0 0 1 41 41v106a41 41 0 0 1-41 41H47a41 41 0 0 1-41-41V47a41 41 0 0 1 41-41z")
private val BOOK_COVER =
    path("M38 44h124a10 10 0 0 1 10 10v102a10 10 0 0 1-10 10H38a10 10 0 0 1-10-10V54a10 10 0 0 1 10-10z")
private val PAGE_LEFT = path("M36 56 Q66 46 98 58 L98 156 Q66 146 36 154 Z")
private val PAGE_RIGHT = path("M102 58 Q134 46 164 56 L164 154 Q134 146 102 156 Z")
private val SPINE = path("M98 58 L102 58 L102 156 L98 156 Z")
private val TEXT_LINES = path("M46 70h40M46 80h34M46 90h40M46 100h28M114 112h34M114 122h40M114 132h30")
private val CURL_PAGE = path("M120 58 C140 46 160 38 168 50 C174 60 162 78 152 98 C144 114 142 128 148 142 L120 136 Z")
private val CURL_LINES = path("M130 70l22-8M128 82l20-6M128 94l16-4")
private val RIBBON = path("M58 172 C66 128 92 126 102 100 C112 72 120 52 152 44")
private val RIBBON_2 = path("M78 126 C96 112 118 112 138 100")

private val BG = Brush.linearGradient(
    0f to Color(0xFF2F8CF6),
    0.55f to Color(0xFF2556DC),
    1f to Color(0xFF1F2FB0),
    start = Offset.Zero,
    end = Offset(ART, ART)
)
private val PAGE = Brush.verticalGradient(listOf(Color(0xFFFFFAF0), Color(0xFFEFE5D0)), startY = 46f, endY = 156f)
private val RIB = Brush.linearGradient(
    0f to Color(0xFF3EE6E0),
    0.5f to Color(0xFF4F8FF0),
    1f to Color(0xFF8A4FE0),
    start = Offset(58f, 172f),
    end = Offset(152f, 44f)
)
private val SHADOW = Color(0x730A1A60)

private fun DrawScope.drawIcon(ribbon: Float, ribbon2: Float, curl: Float) {
    drawRoundRect(BG, size = Size(ART, ART), cornerRadius = CornerRadius(46f))
    drawPath(ICON_PLATE_INNER, Color.White.copy(alpha = 0.18f), style = Stroke(2f))
    translate(0f, 4f) { blurred(SHADOW, 8f) { drawPath(BOOK_COVER.asAndroidPath(), it) } }
    drawPath(BOOK_COVER, Color(0xFF3B7BF0))
    drawPath(BOOK_COVER, Color(0xFF7FB4FF), style = Stroke(3f))
    drawPath(PAGE_LEFT, PAGE)
    drawPath(PAGE_RIGHT, PAGE)
    drawPath(SPINE, Color(0xFFCBB994))
    drawPath(TEXT_LINES, Color(0xFFB9AD98).copy(alpha = 0.8f), style = Stroke(3f, cap = StrokeCap.Round))
    // `@keyframes curl`: the loose page swings in from -22 degrees around (110, 140).
    rotate(-22f * (1f - curl), pivot = Offset(110f, 140f)) {
        scale(0.9f + 0.1f * curl, pivot = Offset(110f, 140f)) {
            val a = curl.coerceIn(0f, 1f)
            translate(0f, 4f) {
                blurred(SHADOW.copy(alpha = SHADOW.alpha * a), 8f) { drawPath(CURL_PAGE.asAndroidPath(), it) }
            }
            drawPath(CURL_PAGE, Color(0xFFFFFDF7).copy(alpha = a))
            drawPath(CURL_LINES, Color(0xFFC9BDA7).copy(alpha = 0.8f * a), style = Stroke(2.6f, cap = StrokeCap.Round))
        }
    }
    // `@keyframes draw`: the ribbon strokes draw themselves (stroke-dashoffset 260 -> 0).
    drawPartial(RIBBON, ribbon, 17f, shadow = true)
    drawPartial(RIBBON_2, ribbon2, 13f, shadow = false)
}

private fun DrawScope.drawPartial(full: Path, progress: Float, width: Float, shadow: Boolean) {
    if (progress <= 0f) return
    val measure = PathMeasure().apply { setPath(full, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), part, true)
    if (shadow) translate(0f, 4f) { drawPath(part, SHADOW, style = Stroke(width, cap = StrokeCap.Round)) }
    drawPath(part, RIB, style = Stroke(width, cap = StrokeCap.Round))
}

private const val ART = 200f
private const val HOLD_MS = 1400L
private const val FADE_MS = 300

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The splash on cold start, with the owner's own icon artwork (`docs/brand`): the icon pops in on the mock's spring
 * (`@keyframes pop`), a band of light sweeps across it once, and the "FlowFolio" wordmark and the tagline rise in.
 * About 1.4 s, then it fades (300 ms) and calls [onDone]. A tap skips it.
 */
@Composable
fun FlowFolioSplash(tagline: String, onDone: () -> Unit) {
    val p = FlowFolio.palette
    val done by rememberUpdatedState(onDone)
    val icon = ImageBitmap.imageResource(R.drawable.flowfolio_icon)
    val pop = remember { Animatable(0f) }
    val shine = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, tween(POP_MS, easing = Motion.Spring)) }
        launch { shine.animateTo(1f, tween(SHINE_MS, SHINE_DELAY_MS, Motion.EaseInOut)) }
        launch { word.animateTo(1f, tween(RISE_MS, WORD_DELAY_MS, Motion.EaseOut)) }
        launch { tag.animateTo(1f, tween(RISE_MS, TAG_DELAY_MS, Motion.EaseOut)) }
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
            SplashIcon(icon, pop.value, shine.value)
            Wordmark(Modifier.rise(word.value))
            Text(
                tagline,
                style = FlowFolioType.sub.copy(fontSize = 14.sp).merge(FlowFolio.hint),
                modifier = Modifier.rise(tag.value)
            )
        }
    }
}

/** The icon popping in ([pop] 0..1) with the light band crossing it ([shine] 0..1). */
@Composable
private fun SplashIcon(icon: ImageBitmap, pop: Float, shine: Float) {
    Canvas(
        Modifier.size(ICON_SIZE).graphicsLayer {
            // `@keyframes pop`: from scale .6, rotate -6deg, transparent.
            val s = POP_FROM + (1f - POP_FROM) * pop
            scaleX = s
            scaleY = s
            rotationZ = POP_TILT * (1f - pop)
            alpha = pop.coerceIn(0f, 1f)
            // Its own layer, so the light below lands only on the icon's pixels.
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        drawImage(
            icon,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.High
        )
        // The light sweep: a soft diagonal band crossing from top left to bottom right, only where the icon
        // is (SrcAtop).
        val t = shine
        if (t > 0f && t < 1f) {
            val x = -size.width + SWEEP_SPAN * size.width * t
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = SHINE_ALPHA),
                    1f to Color.Transparent,
                    start = Offset(x - size.width * BAND, 0f),
                    end = Offset(x + size.width * BAND, size.height * BAND)
                ),
                blendMode = BlendMode.SrcAtop
            )
        }
    }
}

/** "Flow" in the item colour (italic) and "Folio" in the title colour, as in the mock's `.wordmark`. */
@Composable
private fun Wordmark(modifier: Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight(WORDMARK_ITALIC_WEIGHT))) {
                withStyle(FlowFolio.item.toSpanStyle()) { append("Flow") }
            }
            withStyle(FlowFolio.title.toSpanStyle()) { append("Folio") }
        },
        style = FlowFolioType.screenTitle.copy(fontSize = 44.sp, fontWeight = FontWeight(WORDMARK_WEIGHT)),
        modifier = modifier
    )
}

/** `@keyframes rise`: from 14 px lower and transparent. */
private fun Modifier.rise(progress: Float) = graphicsLayer {
    alpha = progress
    // No offscreen buffer while fading: it would clip the wordmark's glow to the text box.
    compositingStrategy = CompositingStrategy.ModulateAlpha
    translationY = RISE.toPx() * (1f - progress)
}

private val ICON_SIZE = 150.dp
private val RISE = 14.dp
private const val POP_FROM = 0.6f
private const val POP_TILT = -6f
private const val POP_MS = 500
private const val SHINE_MS = 650
private const val SHINE_DELAY_MS = 350
private const val SHINE_ALPHA = 0.55f
private const val SWEEP_SPAN = 3f
private const val BAND = 0.35f
private const val RISE_MS = 500
private const val WORD_DELAY_MS = 450
private const val TAG_DELAY_MS = 575
private const val HOLD_MS = 1400L
private const val FADE_MS = 300
private const val WORDMARK_WEIGHT = 700
private const val WORDMARK_ITALIC_WEIGHT = 380

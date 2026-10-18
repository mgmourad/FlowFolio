package com.flowfolio.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/**
 * The screen content that glass surfaces blur (Haze, owner-approved 2026-10-17). The app shell provides it; a glass
 * surface outside any provider falls back to its tint alone.
 */
val LocalGlassState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Overrides the glass look below it: [tint] over the blur and the [edge] hairline (null keeps the default). The reader
 * uses a lighter, frosted tint over dark pages, where the default dark tint would read as a hole rather than glass.
 */
@Immutable
data class GlassStyle(val tint: Color? = null, val edge: Color? = null)

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }

/** Marks this content as what glass surfaces above it blur (the app's screens, the reader's pages). */
@Composable
fun Modifier.glassSource(): Modifier = LocalGlassState.current?.let { hazeSource(it) } ?: this

/**
 * The mock's glass (`.glass`, `.tabbar`): what is behind is blurred ([blur], `saturate` approximated by the tint), then
 * tinted with [tint] (default: the background at 45%), with a hairline edge and a soft shadow. Real blur needs
 * Android 12+; older phones get the tint alone (Haze's fallback).
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    tint: Color = LocalGlassStyle.current.tint ?: FlowFolio.palette.background.copy(alpha = GLASS_ALPHA),
    blur: Dp = 16.dp
): Modifier {
    val p = FlowFolio.palette
    val edge = LocalGlassStyle.current.edge ?: p.edge
    val state = LocalGlassState.current
    val shadowed =
        softShadow(shape, Color.Black.copy(alpha = SHADOW_ALPHA), blur = 40.dp, offsetY = 20.dp, spread = (-14).dp)
    val blurred = if (state != null) {
        shadowed.clip(shape).hazeBlur(
            HazeInput.Sources(state),
            HazeBlurStyle {
                blurRadius(blur)
                backgroundColor(p.background)
                colorEffects(listOf(HazeColorEffect.tint(tint)))
                fallbackColorEffect(HazeColorEffect.tint(tint.copy(alpha = FALLBACK_ALPHA)))
                noiseFactor(NOISE)
            }
        )
    } else {
        shadowed.clip(shape).drawWithContent {
            drawRect(tint)
            drawContent()
        }
    }
    return blurred.drawWithContent {
        drawContent()
        drawOutline(shape.createOutline(size, layoutDirection, this), edge, style = Stroke(1.dp.toPx()))
    }
}

private const val GLASS_ALPHA = 0.45f
private const val FALLBACK_ALPHA = 0.9f
private const val SHADOW_ALPHA = 0.55f
private const val NOISE = 0.05f

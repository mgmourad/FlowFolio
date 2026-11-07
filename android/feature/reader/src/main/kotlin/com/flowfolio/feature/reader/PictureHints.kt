package com.flowfolio.feature.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.Glyph
import kotlin.math.roundToInt

/**
 * A pulsing magnifier at the bottom-right of each picture, so a reader sees that a picture opens when tapped
 * (Phase 4 M3 item 4). The tap is the picture's own link; this is only the affordance, drawn over the pages.
 */
@Composable
internal fun BoxScope.PictureHints(links: Map<Int, List<ReaderLink>>, framesFor: (IntSize) -> List<PageFrames.Frame>) {
    val size = LocalWindowInfo.current.containerSize
    val frames = framesFor(size)
    val pulse by rememberInfiniteTransition(label = "pictureHint").animateFloat(
        initialValue = HINT_ALPHA_LO,
        targetValue = HINT_ALPHA_HI,
        animationSpec = infiniteRepeatable(tween(HINT_PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pictureHintAlpha"
    )
    for (frame in frames) {
        for (link in links[frame.page].orEmpty()) {
            if (link.target !is LinkTarget.Picture) continue
            val x = frame.x + link.right * frame.w
            val y = frame.y + link.bottom * frame.h
            FlowFolioGlyph(
                Glyph.Search,
                stringResource(R.string.reader_picture_hint),
                color = FlowFolio.palette.hint.copy(alpha = pulse),
                size = HINT_GLYPH,
                glow = false,
                modifier = Modifier.offset {
                    IntOffset((x - HINT_GLYPH.toPx()).roundToInt(), (y - HINT_GLYPH.toPx()).roundToInt())
                }
            )
        }
    }
}

private val HINT_GLYPH = 16.dp
private const val HINT_PULSE_MS = 900
private const val HINT_ALPHA_LO = 0.3f
private const val HINT_ALPHA_HI = 0.85f

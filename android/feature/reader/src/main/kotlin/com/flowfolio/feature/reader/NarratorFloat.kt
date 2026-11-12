package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.Motion
import kotlin.math.roundToInt

/**
 * The floating narrator (owner, 2026-11-12): while Narrator Mode is under way, a small see-through pill with Play/Pause
 * and Stop sits over the page, top right at first, and can be dragged anywhere on it. Pausing or stopping then never
 * needs the dock and the Narrator panel. Stop ends the narration, and the pill goes with it.
 */
@Composable
internal fun BoxScope.NarratorFloat(session: Boolean, playing: Boolean, onToggle: () -> Unit, onStop: () -> Unit) {
    // Where the reader left it, as a shift from its top-right home; kept across fold and unfold.
    var dx by rememberSaveable { mutableFloatStateOf(0f) }
    var dy by rememberSaveable { mutableFloatStateOf(0f) }
    // Its home clears the top bar's buttons; no status-bar padding, which comes and goes with the chrome.
    BoxWithConstraints(Modifier.matchParentSize().padding(FLOAT_MARGIN).padding(top = TOP_CLEARANCE)) {
        val density = LocalDensity.current
        val maxX = with(density) { (maxWidth - FLOAT_WIDTH).toPx() }
        val maxY = with(density) { (maxHeight - FLOAT_HEIGHT).toPx() }
        AnimatedVisibility(
            session,
            Modifier
                .align(Alignment.TopEnd)
                // Kept on the page whatever its size: the home is the top-right corner, so x runs left (negative).
                .offset { IntOffset(dx.coerceIn(-maxX, 0f).roundToInt(), dy.coerceIn(0f, maxY).roundToInt()) }
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        dx = (dx + drag.x).coerceIn(-maxX, 0f)
                        dy = (dy + drag.y).coerceIn(0f, maxY)
                    }
                },
            enter = fadeIn(tween(Motion.MEDIUM_MS)) + scaleIn(tween(Motion.MEDIUM_MS), initialScale = START_SCALE),
            exit = fadeOut(tween(Motion.FAST_MS)) + scaleOut(tween(Motion.FAST_MS), targetScale = START_SCALE)
        ) {
            Row(
                Modifier
                    .clip(PillShape)
                    .background(Color.Black.copy(alpha = FLOAT_ALPHA), PillShape)
                    .border(1.dp, Color.White.copy(alpha = EDGE_ALPHA), PillShape)
                    .padding(FLOAT_PADDING)
                    .testTag(ReaderTestTags.NARRATOR_FLOAT),
                horizontalArrangement = Arrangement.spacedBy(FLOAT_PADDING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatButton(
                    if (playing) R.drawable.ic_narrator_pause else R.drawable.ic_narrator_play,
                    stringResource(
                        if (playing) R.string.reader_narrator_float_pause else R.string.reader_narrator_float_play
                    ),
                    filled = true,
                    onClick = onToggle
                )
                FloatButton(
                    R.drawable.ic_narrator_close,
                    stringResource(R.string.reader_narrator_float_stop),
                    filled = false,
                    onClick = onStop
                )
            }
        }
    }
}

@Composable
private fun FloatButton(icon: Int, label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(BUTTON)
            .clip(CircleShape)
            .background(if (filled) FlowFolio.palette.accent else Color.White.copy(alpha = GHOST_ALPHA))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = Color.White, modifier = Modifier.size(ICON))
    }
}

private val PillShape = RoundedCornerShape(50)
private val BUTTON = 40.dp
private val ICON = 22.dp
private val FLOAT_PADDING = 6.dp
private val FLOAT_MARGIN = 12.dp
private val TOP_CLEARANCE = 92.dp

/** The pill's size: two buttons, the gap and the padding. */
private val FLOAT_WIDTH = BUTTON * 2 + FLOAT_PADDING * 3
private val FLOAT_HEIGHT = BUTTON + FLOAT_PADDING * 2
private const val FLOAT_ALPHA = 0.45f
private const val EDGE_ALPHA = 0.18f
private const val GHOST_ALPHA = 0.16f
private const val START_SCALE = 0.8f

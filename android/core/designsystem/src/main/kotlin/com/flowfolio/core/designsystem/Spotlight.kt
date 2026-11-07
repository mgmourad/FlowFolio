package com.flowfolio.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

// A coach mark (owner, 2026-11-07): the screen dims behind a cut-out that spotlights one control, and a card beside
// it says what the control is for, with "Got it" and "Never show tips again". [CoachMark] wraps the control being
// pointed at; [SpotlightHost] sits at the top of the app and draws the overlay over everything.

/** What the overlay is currently saying. */
data class SpotlightContent(
    val title: String,
    val body: String,
    val dismissLabel: String,
    val neverLabel: String,
    val onDismiss: () -> Unit,
    val onNeverShow: () -> Unit
)

/** The app's one spotlight: which control is highlighted, and what it is being told about it. */
@Stable
class SpotlightState {
    var content: SpotlightContent? by mutableStateOf(null)
        private set

    var anchor: Rect by mutableStateOf(Rect.Zero)
        private set

    fun show(content: SpotlightContent, anchor: Rect) {
        this.content = content
        this.anchor = anchor
    }

    fun dismiss() {
        content = null
    }
}

val LocalSpotlight = staticCompositionLocalOf { SpotlightState() }

/**
 * Wraps the control a tip points at, measuring it and, while [visible], showing the overlay over it. [content] is the
 * control itself, drawn normally.
 */
@Composable
fun CoachMark(
    visible: Boolean,
    title: String,
    body: String,
    dismissLabel: String,
    neverLabel: String,
    onDismiss: () -> Unit,
    onNeverShow: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = LocalSpotlight.current
    var anchor by remember { mutableStateOf(Rect.Zero) }
    Box {
        Box(Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) { content() }
        LaunchedEffect(visible, anchor) {
            if (visible) {
                state.show(SpotlightContent(title, body, dismissLabel, neverLabel, onDismiss, onNeverShow), anchor)
            } else {
                state.dismiss()
            }
        }
    }
}

/** Draws the overlay over the whole app when a coach mark is showing; a no-op otherwise. */
@Composable
fun SpotlightHost() {
    val state = LocalSpotlight.current
    val content = state.content ?: return
    SpotlightOverlay(state.anchor, content, state::dismiss)
}

@Composable
private fun SpotlightOverlay(anchor: Rect, content: SpotlightContent, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(20.dp)
    val radiusPx = with(density) { 22.dp.toPx() }
    val scrim = Color.Black.copy(alpha = SCRIM_ALPHA)
    Dialog(
        onDismissRequest = { /* only the card's buttons dismiss */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            Canvas(Modifier.fillMaxSize()) {
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(RoundRect(anchor, CornerRadius(radiusPx, radiusPx)))
                }
                drawPath(path, scrim)
            }
            // The card sits below the control; above it when there is not enough room below.
            val cardWidth = minOf(screenW - PADDING_PX * 2, CARD_MAX_PX)
            val below = anchor.bottom + GAP_PX <= screenH - CARD_MIN_PX
            val top = if (below) {
                anchor.bottom + GAP_PX
            } else {
                (anchor.top - GAP_PX - CARD_MIN_PX).coerceAtLeast(PADDING_PX)
            }
            val left = (anchor.left + (anchor.width - cardWidth) / 2f).coerceIn(
                PADDING_PX,
                screenW - cardWidth - PADDING_PX
            )
            Column(
                Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .widthIn(max = with(density) { cardWidth.toDp() })
                    .fillMaxWidth()
                    .clip(shape)
                    .background(FlowFolio.palette.background, shape)
                    .border(1.dp, FlowFolio.palette.edge, shape)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(content.title, style = FlowFolioType.sectionTitle.merge(FlowFolio.title))
                Text(content.body, style = FlowFolioType.sub.merge(FlowFolio.hint))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowFolioPrimaryButton(text = content.dismissLabel, onClick = content.onDismiss)
                    FlowFolioGhostButton(text = content.neverLabel, onClick = content.onNeverShow)
                }
            }
        }
    }
}

private const val PADDING_PX = 20f
private const val GAP_PX = 14f
private const val CARD_MAX_PX = 420f
private const val CARD_MIN_PX = 150f
private const val SCRIM_ALPHA = 0.72f

package com.flowfolio.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small segmented control (`.mini-seg`, `.seg`): a sunken track; the chosen option sits on a raised surface and is
 * lit in the title colour.
 */
@Composable
fun FlowFolioSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedStyle: TextStyle = FlowFolio.title
) {
    val p = FlowFolio.palette
    val track = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(9.dp)
    Row(
        modifier
            .clip(track)
            .drawBehind { drawRect(p.background.copy(alpha = SEGMENT_TRACK_ALPHA)) }
            .pressedInset(track)
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val raised = if (on) {
                Modifier.softElevation(chip).clip(chip).drawBehind { drawRect(p.selected) }
            } else {
                Modifier.clip(chip)
            }
            Box(
                raised
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    style = FlowFolioType.pill.copy(fontSize = FlowFolioType.chip.fontSize)
                        .merge(if (on) selectedStyle else FlowFolio.hint)
                )
            }
        }
    }
}

/**
 * Full-width tabs (`.tabs`) with an indicator that slides between them on the mock's spring (`.tab-ind`).
 */
@Composable
fun FlowFolioTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val density = LocalDensity.current
    val track = RoundedCornerShape(18.dp)
    val bounds =
        remember(tabs) { mutableStateListOf<Pair<Float, Float>>().apply { repeat(tabs.size) { add(0f to 0f) } } }
    val target = bounds.getOrNull(selected) ?: (0f to 0f)
    var ready by remember { mutableStateOf(false) }
    val spring = tween<Float>(if (ready) Motion.SPRING_MS else 0, easing = Motion.Spring)
    val left by animateFloatAsState(target.first, spring, label = "tabLeft")
    val width by animateFloatAsState(target.second, spring, label = "tabWidth")
    LaunchedEffect(target) { if (target.second > 0f) ready = true }
    Box(modifier.card(track).clip(track).pressedInset(track).padding(5.dp)) {
        val indicator = RoundedCornerShape(13.dp)
        Box(
            Modifier
                .offset(x = with(density) { left.toDp() })
                .width(with(density) { width.toDp() })
                .height(TAB_HEIGHT)
                .softElevation(indicator)
                .clip(indicator)
                .drawBehind { drawRect(p.selected) }
        )
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(TAB_HEIGHT)
                        .onGloballyPositioned { bounds[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .clickable(role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = FlowFolioType.chip.copy(fontWeight = FlowFolioType.button.fontWeight)
                            .merge(if (i == selected) FlowFolio.title else FlowFolio.hint)
                    )
                }
            }
        }
    }
}

private const val SEGMENT_TRACK_ALPHA = 0.55f
private val TAB_HEIGHT = 38.dp

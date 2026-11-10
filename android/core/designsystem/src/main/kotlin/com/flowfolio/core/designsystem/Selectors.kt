package com.flowfolio.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A segmented control (`.mini`, ADR-0052): a pill track in surface 2; the chosen option sits on the surface with a
 * small shadow, in ink, and the raised part springs from one option to the next. [selectedStyle] is kept for callers.
 */
@Composable
fun FlowFolioSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") selectedStyle: TextStyle = FlowFolio.title
) {
    val p = FlowFolio.palette
    val density = LocalDensity.current
    val pill = RoundedCornerShape(50)
    val bounds =
        remember(options) { mutableStateListOf<Pair<Float, Float>>().apply { repeat(options.size) { add(0f to 0f) } } }
    val target = bounds.getOrNull(selected) ?: (0f to 0f)
    var ready by remember { mutableStateOf(false) }
    val left by animateFloatAsState(target.first, if (ready) Motion.springSpec() else snap(), label = "segLeft")
    val width by animateFloatAsState(target.second, if (ready) Motion.springSpec() else snap(), label = "segWidth")
    LaunchedEffect(target) { if (target.second > 0f) ready = true }
    Box(
        modifier
            .clip(pill)
            .background(p.surface2, pill)
            .padding(3.dp)
            .height(IntrinsicSize.Min)
    ) {
        if (width > 0f) {
            Box(
                Modifier
                    .offset(x = with(density) { left.toDp() })
                    .width(with(density) { width.toDp() })
                    .fillMaxHeight()
                    .softShadow(pill, Color.Black.copy(alpha = 0.14f), blur = 3.dp, offsetY = 1.dp)
                    .background(p.surface, pill)
            )
        }
        Row {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val ink by animateColorAsState(if (on) p.ink else p.ink2, Motion.colorSpec(), label = "segInk")
                Box(
                    Modifier
                        .onGloballyPositioned { bounds[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .clip(pill)
                        .selectable(on, role = Role.Tab) { onSelect(i) }
                        .heightIn(min = 34.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = FlowFolioType.chip.copy(
                            fontSize = 12.5.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = ink
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Tabs (`.tabs`, ADR-0052): labels in the third ink over a hairline; the chosen one is in ink with a 2 dp accent
 * underline that springs between tabs. [counts] adds a small count pill after a label.
 */
@Composable
fun FlowFolioTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    counts: List<Int?> = emptyList()
) {
    val p = FlowFolio.palette
    val bounds =
        remember(tabs) { mutableStateListOf<Pair<Float, Float>>().apply { repeat(tabs.size) { add(0f to 0f) } } }
    val target = bounds.getOrNull(selected) ?: (0f to 0f)
    var ready by remember { mutableStateOf(false) }
    val left by animateFloatAsState(target.first, if (ready) Motion.springSpec() else snap(), label = "tabLeft")
    val width by animateFloatAsState(target.second, if (ready) Motion.springSpec() else snap(), label = "tabWidth")
    LaunchedEffect(target) { if (target.second > 0f) ready = true }
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val h = 1.dp.toPx()
                drawRect(p.line, Offset(0f, size.height - h), Size(size.width, h))
                if (width > 0f) {
                    val t = 2.dp.toPx()
                    drawRect(p.accent, Offset(left, size.height - t), Size(width, t))
                }
            }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            tabs.forEachIndexed { i, label ->
                val on = i == selected
                Row(
                    Modifier
                        .onGloballyPositioned { bounds[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .heightIn(min = 44.dp)
                        .selectable(on, role = Role.Tab) { onSelect(i) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        label,
                        style = FlowFolioType.chip.copy(
                            fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) p.ink else p.ink3
                        ),
                        maxLines = 1
                    )
                    counts.getOrNull(i)?.let { n ->
                        Text(
                            n.toString(),
                            style = FlowFolioType.pill.copy(fontSize = 11.sp, color = p.ink2),
                            modifier = Modifier
                                .background(p.surface2, RoundedCornerShape(50))
                                .padding(horizontal = 7.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

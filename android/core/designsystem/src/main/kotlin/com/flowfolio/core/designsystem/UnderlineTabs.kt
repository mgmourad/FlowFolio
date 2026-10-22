package com.flowfolio.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Tabs as words on a hairline, the chosen one in the title colour with an underline that slides to it: a quiet tab
 * bar for content pages (the book's details), where pill tabs would read as more buttons.
 */
@Composable
fun FlowFolioUnderlineTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val bounds =
        remember(tabs) { mutableStateListOf<Pair<Float, Float>>().apply { repeat(tabs.size) { add(0f to 0f) } } }
    val target = bounds.getOrNull(selected) ?: (0f to 0f)
    val left by animateFloatAsState(target.first, tween(Motion.MEDIUM_MS, easing = Motion.EaseOut), label = "tabLeft")
    val width by animateFloatAsState(
        target.second,
        tween(Motion.MEDIUM_MS, easing = Motion.EaseOut),
        label = "tabWidth"
    )
    Row(
        modifier
            .fillMaxWidth()
            .selectableGroup()
            .drawBehind {
                val hair = 1.dp.toPx()
                drawRect(p.edge, Offset(0f, size.height - hair), Size(size.width, hair))
                val bar = INDICATOR.toPx()
                drawRoundRect(p.title, Offset(left, size.height - bar), Size(width, bar), CornerRadius(bar))
            }
    ) {
        tabs.forEachIndexed { i, label ->
            val on = i == selected
            val color by animateColorAsState(if (on) p.title else p.hint, label = "tabText")
            Box(
                Modifier
                    .weight(1f)
                    .onPlaced { bounds[i] = it.positionInParent().x to it.size.width.toFloat() }
                    .selectable(on, role = Role.Tab) { onSelect(i) }
                    .heightIn(min = TAB_HEIGHT)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = FlowFolioType.rowLabel.copy(color = color),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val TAB_HEIGHT = 48.dp
private val INDICATOR = 3.dp

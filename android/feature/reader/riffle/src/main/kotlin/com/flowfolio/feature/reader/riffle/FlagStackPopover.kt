package com.flowfolio.feature.reader.riffle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.flowfolio.core.designsystem.FlowFolio

/**
 * The flags of a stack, side by side (spec 3.3): each with its colour, its page and the start of its note. Tapping one
 * chooses it; tapping outside closes the popover. Shown in its own window above the strip, centred on the stack.
 */
@Composable
internal fun FlagStackPopover(state: RiffleScrubberState, events: RiffleEvents) {
    val stack = state.openStack ?: return
    val g = state.geometry ?: return
    DisposableEffect(stack) {
        events.onPause(ChromePause.POPOVER, true)
        onDispose { events.onPause(ChromePause.POPOVER, false) }
    }
    val x = state.leaves.x[state.leafOfPage(stack.flags.first().page)]
    val gap = (POPOVER_GAP_DP * g.density).toInt()
    val position = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize
        ): IntOffset {
            val left = (anchorBounds.left + x - popupContentSize.width / 2f).toInt()
            return IntOffset(
                left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                (anchorBounds.top + g.top).toInt() - popupContentSize.height - gap
            )
        }
    }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = { state.openStack = null },
        properties = PopupProperties(focusable = true)
    ) {
        val palette = FlowFolio.palette
        Row(
            Modifier
                .widthIn(max = MAX_WIDTH.dp)
                .background(palette.surface.copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                .border(1.dp, palette.edge, RoundedCornerShape(18.dp))
                .horizontalScroll(rememberScrollState())
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stack.flags.forEach { flag ->
                FlagItem(state.book, flag) {
                    state.openStack = null
                    events.onFlag(flag)
                }
            }
        }
    }
}

@Composable
private fun FlagItem(book: BookGeometry, flag: RiffleFlag, onClick: () -> Unit) {
    val palette = FlowFolio.palette
    val page = RiffleLabels.pageLabel(book, flag.page)
    Row(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = listOfNotNull(page, flag.note).joinToString(", ") }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(10.dp).background(Color(flag.color), CircleShape))
        Column {
            Text(page, color = palette.title, style = FlowFolio.item.copy(color = palette.title))
            flag.note?.let {
                Text(
                    it,
                    color = palette.hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )
            }
        }
    }
}

private const val POPOVER_GAP_DP = 30f
private const val MAX_WIDTH = 420

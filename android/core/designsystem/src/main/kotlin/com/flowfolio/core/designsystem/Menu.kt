package com.flowfolio.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * A long-press / overflow menu with the design's own outline and shadow (owner, Phase 4 M3): a hairline in the
 * accent colour (bright orange in FlowFolio dark) around the menu, a soft shadow, and the theme's surface behind.
 * Items add [FlowFolioMenuDivider] between themselves for a subtle separation.
 */
@Composable
fun FlowFolioMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val p = FlowFolio.palette
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        containerColor = p.surface,
        border = BorderStroke(1.dp, p.title.copy(alpha = MENU_BORDER_ALPHA)),
        shadowElevation = MENU_SHADOW,
        content = content
    )
}

/** A hairline between menu items (Phase 4 M3). */
@Composable
fun FlowFolioMenuDivider() {
    HorizontalDivider(color = FlowFolio.palette.edge)
}

private const val MENU_BORDER_ALPHA = 0.55f
private val MENU_SHADOW = 12.dp

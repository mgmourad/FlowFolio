package com.flowfolio.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An anchored popup menu (`.menu`, ADR-0052): the surface, a hairline, 18 dp corners and a deep soft shadow, at least
 * 220 dp wide. It grows from its anchor (Material's own scale and fade). Fill it with [FlowFolioMenuItem],
 * [FlowFolioMenuHeader] and [FlowFolioMenuDivider].
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
        modifier = modifier.widthIn(min = MENU_MIN_WIDTH).padding(horizontal = 6.dp),
        offset = offset,
        shape = RoundedCornerShape(18.dp),
        containerColor = p.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(EDGE_WIDTH, p.line),
        shadowElevation = MENU_SHADOW,
        content = content
    )
}

/**
 * One entry in a [FlowFolioMenu] (`.menu button`): an optional [glyph] in the third ink, the [text], then a
 * [trailing] note, or a check in the accent when [checked]. [destructive] entries are in the danger colour.
 */
@Composable
fun FlowFolioMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    trailing: String? = null,
    checked: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    val p = FlowFolio.palette
    val ink = if (destructive) p.danger else p.ink
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (checked) selected = true }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (glyph != null) {
            FlowFolioGlyph(glyph, null, color = if (destructive) p.danger else p.ink3, size = 18.dp, glow = false)
        }
        Text(
            text,
            style = FlowFolioType.body.copy(fontSize = 14.sp, color = ink.copy(alpha = if (enabled) 1f else 0.4f)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        when {
            checked -> FlowFolioGlyph(Glyph.Check, null, color = p.accent, size = 18.dp, glow = false)
            trailing != null -> Text(trailing, style = FlowFolioType.body.copy(fontSize = 12.sp, color = p.ink3))
        }
    }
}

/** A section heading inside a [FlowFolioMenu] (`.menu .mh`): small capitals in the third ink. */
@Composable
fun FlowFolioMenuHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = FlowFolioType.body.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.05.sp,
            color = FlowFolio.palette.ink3
        ),
        modifier = modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
    )
}

/** A hairline between groups of menu items (`.menu hr`). */
@Composable
fun FlowFolioMenuDivider() {
    val line = FlowFolio.palette.line
    Box(
        Modifier
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind { drawRect(line) }
    )
}

private val MENU_MIN_WIDTH = 220.dp
private val MENU_SHADOW = 18.dp

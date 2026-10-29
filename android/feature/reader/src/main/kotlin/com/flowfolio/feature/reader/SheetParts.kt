@file:Suppress("MatchingDeclarationName") // the reading sheet's small parts, of which SheetTab is one

package com.flowfolio.feature.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph

/** One tab of the reading sheet: its name and glyph. */
internal class SheetTab(val label: String, val glyph: Glyph, val tag: String)

/** The sheet's tabs: a glyph and a short name each, the chosen one on a raised pill. */
@Composable
internal fun SheetTabs(tabs: List<SheetTab>, selected: Int, onSelect: (Int) -> Unit) {
    val p = FlowFolio.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.hint.copy(alpha = TRACK_ALPHA))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { i, tab ->
            val on = i == selected
            val fill by animateColorAsState(if (on) p.selected else Color.Transparent, label = "tab")
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fill)
                    .selectable(on, role = Role.Tab) { onSelect(i) }
                    .height(TAB_HEIGHT)
                    .testTag(tab.tag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                FlowFolioGlyph(tab.glyph, null, color = if (on) p.title else p.hint, size = 18.dp, glow = false)
                Text(
                    tab.label,
                    style = FlowFolioType.pill.merge(if (on) FlowFolio.title else FlowFolio.hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/**
 * A setting's glyph and name on the left and its control on the right, one compact line. When both don't fit (a long
 * name beside a wide control on the cover screen), the control goes under the name rather than cutting the name short.
 * [stacked] always puts it under the name (line spacing and margins, alike; owner, M3T1).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabeledRow(
    label: String,
    glyph: Glyph? = null,
    stacked: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    if (stacked) {
        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            SettingName(label, glyph)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        }
        return
    }
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        SettingName(label, glyph, Modifier.padding(end = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** A setting's glyph and name. */
@Composable
private fun SettingName(label: String, glyph: Glyph?, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) {
            FlowFolioGlyph(glyph, null, size = ROW_GLYPH, glow = false, modifier = Modifier.padding(end = 10.dp))
        }
        Text(label, style = FlowFolioType.rowLabel.merge(FlowFolio.item), maxLines = 1)
    }
}

/** A button drawn like [IconChoice]'s boxes, a rounded square, for the sheet's one-off actions (smaller, larger). */
@Composable
internal fun SquareButton(label: String, tag: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(ICON_BOX)
            .clip(shape)
            .border(1.dp, p.hint.copy(alpha = OUTLINE_ALPHA), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** An on/off setting as a pill: filled and outlined in the accent when on. */
@Composable
internal fun ToggleChip(
    label: String,
    checked: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: Glyph? = null,
    onChange: (Boolean) -> Unit
) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(50)
    val fill by animateColorAsState(if (checked) p.selected else Color.Transparent, label = "chip")
    Box(
        modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(fill)
            .border(if (checked) 1.5.dp else 1.dp, if (checked) p.title else p.hint.copy(alpha = OUTLINE_ALPHA), shape)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                FlowFolioGlyph(
                    glyph,
                    null,
                    color = if (checked) p.title else p.hint,
                    size = CHIP_GLYPH,
                    glow = false,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            Text(
                label,
                style = FlowFolioType.pill.merge(if (checked) FlowFolio.title else FlowFolio.hint),
                maxLines = 1
            )
        }
    }
}

private val ROW_GLYPH = 20.dp
private val CHIP_GLYPH = 16.dp

/**
 * A choice drawn as small pictures (line spacing, margins): [count] options, each drawn by [icon] with its index and
 * colour, described by [labels] for screen readers.
 */
@Composable
internal fun IconChoice(
    labels: List<String>,
    selected: Int,
    tag: String,
    onSelect: (Int) -> Unit,
    icon: DrawScope.(index: Int, color: Color) -> Unit
) {
    val p = FlowFolio.palette
    Row(
        Modifier.selectableGroup().testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .size(ICON_BOX)
                    .clip(shape)
                    .background(if (on) p.selected else Color.Transparent)
                    .border(if (on) 1.5.dp else 1.dp, if (on) p.title else p.hint.copy(alpha = OUTLINE_ALPHA), shape)
                    .selectable(on, role = Role.RadioButton) { onSelect(i) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center
            ) {
                val color = if (on) p.title else p.item
                Canvas(Modifier.size(22.dp)) { icon(i, color) }
            }
        }
    }
}

private val TAB_HEIGHT = 38.dp
private val ROW_HEIGHT = 44.dp
private val ICON_BOX = 40.dp
private const val TRACK_ALPHA = 0.12f
private const val OUTLINE_ALPHA = 0.35f
private const val DISABLED_ALPHA = 0.4f

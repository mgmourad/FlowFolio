package com.flowfolio.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioSlider
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph

/** `.set-row` with a toggle; the whole row toggles. */
@Composable
internal fun SwitchRow(
    title: Int,
    detail: Int?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Label(title, detail, Modifier.weight(1f).padding(end = 14.dp))
        FlowFolioToggle(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A sound's switch, with its loudness slider beneath (ADR-0025). */
@Composable
internal fun SoundRow(
    title: Int,
    detail: Int,
    on: Boolean,
    volume: Float,
    actions: Pair<(Boolean) -> Unit, (Float) -> Unit>
) {
    SwitchRow(title, detail, on, actions.first)
    FlowFolioSlider(
        volume,
        onValueChangeFinished = actions.second,
        label = stringResource(title),
        enabled = on,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
    )
}

/** `.set-row` with a `.mini-seg` of [options]; [stacked] puts the options under the label, for long lists. */
@Composable
internal fun ChoiceRow(
    title: Int,
    detail: Int,
    options: List<Int>,
    selected: Int,
    stacked: Boolean = false,
    onSelect: (Int) -> Unit
) {
    if (stacked) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
            Label(title, detail, Modifier.padding(bottom = 10.dp))
            FlowFolioSegmented(options.map { stringResource(it) }, selected, onSelect)
        }
        return
    }
    // Beside the label when the options leave it room, else under it: three options on the folded screen left the
    // label a few letters wide ("Librar / y stats", owner 2026-11-12).
    Layout(
        content = {
            Label(title, detail, Modifier)
            FlowFolioSegmented(options.map { stringResource(it) }, selected, onSelect)
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)
    ) { (label, segmented), constraints ->
        val gap = 12.dp.roundToPx()
        val seg = segmented.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val room = constraints.maxWidth - seg.width - gap
        if (room >= MIN_LABEL_WIDTH.roundToPx()) {
            val text = label.measure(constraints.copy(minWidth = 0, maxWidth = room, minHeight = 0))
            val height = maxOf(text.height, seg.height)
            layout(constraints.maxWidth, height) {
                text.placeRelative(0, (height - text.height) / 2)
                seg.placeRelative(constraints.maxWidth - seg.width, (height - seg.height) / 2)
            }
        } else {
            val text = label.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val below = 10.dp.roundToPx()
            layout(constraints.maxWidth, text.height + below + seg.height) {
                text.placeRelative(0, 0)
                seg.placeRelative(0, text.height + below)
            }
        }
    }
}

/** The narrowest a row's label may get beside its options before they move under it. */
private val MIN_LABEL_WIDTH = 140.dp

@Composable
internal fun Label(title: Int, detail: Int?, modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(title), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
        detail?.let { Text(stringResource(it), style = FlowFolioType.rowDetail.merge(FlowFolio.hint)) }
    }
}

@Composable
internal fun Divider() {
    HorizontalDivider(thickness = 1.dp, color = FlowFolio.palette.edge)
}

/** A settings row that does something when tapped, rather than holding a value (owner, M5). */
@Composable
internal fun ActionRow(title: Int, detail: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
            Text(stringResource(detail), style = FlowFolioType.rowDetail.merge(FlowFolio.hint))
        }
        FlowFolioGlyph(
            Glyph.GoTo,
            contentDescription = null,
            color = FlowFolio.palette.ink2,
            size = 18.dp,
            glow = false
        )
    }
}

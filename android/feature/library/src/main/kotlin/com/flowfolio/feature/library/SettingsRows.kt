package com.flowfolio.feature.library

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioSlider
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType

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
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Label(title, detail, Modifier.weight(1f).padding(end = 12.dp))
        FlowFolioSegmented(options.map { stringResource(it) }, selected, onSelect)
    }
}

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

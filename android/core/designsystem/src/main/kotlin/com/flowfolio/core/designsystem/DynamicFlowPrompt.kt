package com.flowfolio.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.flowfolio.core.model.FlowCarry

/**
 * Switching a PDF book between the PDF and Dynamic Flow (ADR-0045), from the reader's "Aa" sheet or the library's
 * menus: the same questions everywhere. While [toFlow] is set (true into Dynamic Flow, false back to the PDF) it asks,
 * as [carry] says: whether to bring the view's highlights, notes and bookmarks across ("convert the changes"), with
 * "Don't show this again"; agreeing says that runs in the background. Then [onSwitch] (on, bring them) and [onClose].
 * It switches at once when the view being left has nothing to bring ([hasNotes] answers for the view: true Dynamic
 * Flow), and when the reader said not to ask, with the answer remembered ([onRemember] saves a new one).
 */
@Composable
fun DynamicFlowPrompt(
    toFlow: Boolean?,
    carry: FlowCarry,
    hasNotes: suspend (Boolean) -> Boolean,
    onSwitch: (Boolean, Boolean) -> Unit,
    onRemember: (FlowCarry) -> Unit,
    onClose: () -> Unit
) {
    var telling by remember { mutableStateOf(false) }
    // Whether to ask: known once the view being left has been looked at; null while looking.
    var asking by remember(toFlow) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(toFlow, carry) {
        if (toFlow == null) return@LaunchedEffect
        val notes = hasNotes(!toFlow)
        if (!notes || carry != FlowCarry.ASK) {
            onSwitch(toFlow, notes && carry == FlowCarry.CONVERT)
            onClose()
        } else {
            asking = true
        }
    }
    if (toFlow == null || asking != true) return
    if (telling) {
        val done = {
            telling = false
            onSwitch(toFlow, true)
            onClose()
        }
        FlowFolioAlertDialog(
            onDismissRequest = done,
            confirmButton = { FlowFolioPrimaryButton(stringResource(R.string.flow_carry_ok), onClick = done) },
            title = { Text(stringResource(R.string.flow_carry_title)) },
            text = { Text(stringResource(R.string.flow_carry_body)) }
        )
    } else {
        SwitchNotice(
            toFlow = toFlow,
            onConvert = { always ->
                if (always) onRemember(FlowCarry.CONVERT)
                telling = true
            },
            onSkip = { always ->
                if (always) onRemember(FlowCarry.SKIP)
                onSwitch(toFlow, false)
                onClose()
            },
            onCancel = onClose
        )
    }
}

/** "Your notes stay with each view": convert the changes, switch without them, or stay; and don't show this again. */
@Composable
private fun SwitchNotice(
    toFlow: Boolean,
    onConvert: (Boolean) -> Unit,
    onSkip: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var dontAsk by remember { mutableStateOf(false) }
    val p = FlowFolio.palette
    FlowFolioAlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            FlowFolioPrimaryButton(stringResource(R.string.flow_notice_convert), onClick = { onConvert(dontAsk) })
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.flow_notice_cancel), style = FlowFolio.hint)
                }
                TextButton(onClick = { onSkip(dontAsk) }) {
                    Text(stringResource(R.string.flow_notice_skip), style = FlowFolio.item)
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowFolioGlyph(Glyph.DynamicFlow, contentDescription = null, size = 22.dp)
                Text(stringResource(R.string.flow_notice_title), modifier = Modifier.padding(start = 10.dp))
            }
        },
        text = {
            Column {
                Text(stringResource(if (toFlow) R.string.flow_notice_to_flow else R.string.flow_notice_to_pdf))
                Row(
                    Modifier
                        .padding(top = 14.dp)
                        .toggleable(dontAsk, role = Role.Checkbox) { dontAsk = it }
                        .testTag(FLOW_DONT_ASK_TAG),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontAsk,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = p.title,
                            uncheckedColor = p.hint,
                            checkmarkColor = p.background
                        )
                    )
                    Text(
                        stringResource(R.string.flow_notice_dont_ask),
                        style = FlowFolio.hint,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    )
}

/** The "Don't show this again" checkbox, for tests. */
const val FLOW_DONT_ASK_TAG = "flow_dont_ask"

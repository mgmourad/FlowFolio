package com.flowfolio.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.SelectButton
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.SelectionBar
import com.flowfolio.core.designsystem.SelectionCheck

/**
 * A tab header's controls for select mode (owner, 2026-10-22): the Select button, or while selecting, the count,
 * Select all, Delete and Done. [normal] are the header's other buttons, hidden while selecting.
 */
@Composable
internal fun SelectControls(
    selection: Selection,
    allKeys: List<String>,
    onDelete: () -> Unit,
    normal: @Composable () -> Unit = {}
) {
    // Items removed elsewhere leave the choice.
    LaunchedEffect(allKeys) { selection.keepOnly(allKeys) }
    // The buttons and the selection bar cross-fade with a short rise (ADR-0033).
    AnimatedContent(
        selection.active,
        transitionSpec = {
            (fadeIn(tween(Motion.FAST_MS, delayMillis = SWAP_DELAY_MS)) + slideInVertically { it / SWAP_RISE })
                .togetherWith(fadeOut(tween(SWAP_DELAY_MS)))
                .using(SizeTransform(clip = false))
        },
        label = "selectControls"
    ) { active ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (active) SelectionControlsBar(selection, allKeys, onDelete) else IdleControls(selection, allKeys, normal)
        }
    }
}

@Composable
private fun SelectionControlsBar(selection: Selection, allKeys: List<String>, onDelete: () -> Unit) {
    run {
        SelectionBar(
            selection,
            countLabel = pluralStringResource(R.plurals.selected_count, selection.chosen.size, selection.chosen.size),
            selectAllLabel = stringResource(R.string.action_select_all),
            doneLabel = stringResource(R.string.action_done),
            deleteLabel = stringResource(R.string.action_delete),
            onSelectAll = { selection.setAll(allKeys) },
            onDelete = onDelete,
            modifier = Modifier.testTag(LibraryTestTags.SELECTION_BAR)
        )
    }
}

@Composable
private fun IdleControls(selection: Selection, allKeys: List<String>, normal: @Composable () -> Unit) {
    normal()
    if (allKeys.isNotEmpty()) {
        SelectButton(
            stringResource(R.string.action_select),
            selection::start,
            Modifier.testTag(LibraryTestTags.SELECT)
        )
    }
}

/** An item's tick in select mode, at its top end. */
@Composable
internal fun BoxScope.SelectionMark(selection: Selection, key: String) {
    AnimatedVisibility(
        selection.active,
        Modifier.align(Alignment.TopEnd).padding(4.dp),
        enter = scaleIn(tween(Motion.FAST_MS, easing = Motion.Spring)) + fadeIn(tween(Motion.FAST_MS)),
        exit = scaleOut(tween(Motion.FAST_MS)) + fadeOut(tween(Motion.FAST_MS))
    ) { SelectionCheck(selection.isChosen(key)) }
}

private const val SWAP_DELAY_MS = 90
private const val SWAP_RISE = 3

/** "Remove N …?": asks before a bulk delete; [body] says what stays or goes. */
@Composable
internal fun BulkDeleteDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    FlowFolioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(LibraryTestTags.CONFIRM_BULK_DELETE)) {
                Text(stringResource(R.string.action_remove), style = FlowFolio.subItem)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), style = FlowFolio.hint) }
        }
    )
}

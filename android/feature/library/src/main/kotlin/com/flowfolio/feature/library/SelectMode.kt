package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
    if (selection.active) {
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
    } else {
        normal()
        if (allKeys.isNotEmpty()) {
            SelectButton(
                stringResource(R.string.action_select),
                selection::start,
                Modifier.testTag(LibraryTestTags.SELECT)
            )
        }
    }
}

/** An item's tick in select mode, at its top end. */
@Composable
internal fun BoxScope.SelectionMark(selection: Selection, key: String) {
    if (selection.active) {
        Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { SelectionCheck(selection.isChosen(key)) }
    }
}

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

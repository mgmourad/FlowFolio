package com.flowfolio.feature.library

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.flowFolioRadioColors
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.StorageMode

@Composable
internal fun LibraryDialogs(dialog: LibraryDialog?, viewModel: LibraryViewModel) {
    when (dialog) {
        null -> Unit

        is LibraryDialog.ChooseStorage -> StorageChoiceDialog(
            title = stringResource(if (dialog.firstTime) R.string.storage_title else R.string.storage_title_each),
            initial = dialog.initial,
            sizeBytes = dialog.sizeBytes,
            showChangeLater = dialog.firstTime,
            onConfirm = { viewModel.onStorageChosen(dialog, it) },
            onDismiss = viewModel::dismissDialog
        )

        is LibraryDialog.AskLookup -> FlowFolioAlertDialog(
            onDismissRequest = { viewModel.onLookupAnswer(dialog, allow = false) },
            title = { Text(stringResource(R.string.lookup_ask_title)) },
            text = { Text(stringResource(R.string.lookup_ask_body)) },
            confirmButton = {
                DialogButton(R.string.lookup_ask_yes) { viewModel.onLookupAnswer(dialog, allow = true) }
            },
            dismissButton = { DialogButton(R.string.lookup_ask_no) { viewModel.onLookupAnswer(dialog, allow = false) } }
        )

        is LibraryDialog.LinkNotPossible -> FlowFolioAlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.link_not_possible_title)) },
            text = { Text(stringResource(R.string.link_not_possible_body)) },
            confirmButton = { DialogButton(R.string.action_copy_instead) { viewModel.onCopyInstead(dialog.uri) } },
            dismissButton = { DialogButton(R.string.action_cancel, viewModel::dismissDialog) }
        )

        is LibraryDialog.ConfirmDelete -> FlowFolioAlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.delete_title, dialog.book.title)) },
            text = {
                Text(
                    stringResource(
                        if (dialog.book.storage is BookStorage.Copied) {
                            R.string.delete_body_copy
                        } else {
                            R.string.delete_body_link
                        }
                    )
                )
            },
            confirmButton = { DialogButton(R.string.action_delete) { viewModel.onConfirmDelete(dialog.book) } },
            dismissButton = { DialogButton(R.string.action_cancel, viewModel::dismissDialog) }
        )
    }
}

/** Both storage options with their full disclaimers; used for the first import and for "ask every time". */
@Composable
fun StorageChoiceDialog(
    title: String,
    initial: StorageMode,
    sizeBytes: Long?,
    showChangeLater: Boolean,
    onConfirm: (StorageMode) -> Unit,
    onDismiss: () -> Unit
) {
    var choice by rememberSaveable { mutableStateOf(initial) }
    FlowFolioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StorageOptions(choice, onChoose = { choice = it }, sizeBytes = sizeBytes)
                if (showChangeLater) {
                    Text(
                        stringResource(R.string.storage_change_later),
                        style = MaterialTheme.typography.bodySmall.merge(FlowFolio.hint),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = { DialogButton(R.string.action_continue) { onConfirm(choice) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** The two storage options, each with its disclaimer. Shared by the import dialog and the settings screen. */
@Composable
fun StorageOptions(selected: StorageMode, onChoose: (StorageMode) -> Unit, sizeBytes: Long? = null) {
    val context = LocalContext.current
    val copyDisclaimer = sizeBytes
        ?.let { stringResource(R.string.storage_copy_disclaimer_sized, Formatter.formatShortFileSize(context, it)) }
        ?: stringResource(R.string.storage_copy_disclaimer)
    Column {
        StorageOption(
            selected = selected == StorageMode.COPY,
            label = stringResource(R.string.storage_copy),
            disclaimer = copyDisclaimer,
            onClick = { onChoose(StorageMode.COPY) }
        )
        StorageOption(
            selected = selected == StorageMode.LINK,
            label = stringResource(R.string.storage_link),
            disclaimer = stringResource(R.string.storage_link_disclaimer),
            onClick = { onChoose(StorageMode.LINK) }
        )
        Text(
            stringResource(R.string.storage_shared_note),
            style = MaterialTheme.typography.bodySmall.merge(FlowFolio.subItem),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun StorageOption(selected: Boolean, label: String, disclaimer: String, onClick: () -> Unit) {
    Row(
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null, colors = flowFolioRadioColors())
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall.merge(FlowFolio.item))
            Text(disclaimer, style = MaterialTheme.typography.bodySmall.merge(FlowFolio.hint))
        }
    }
}

@Composable
private fun DialogButton(label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(label)) }
}

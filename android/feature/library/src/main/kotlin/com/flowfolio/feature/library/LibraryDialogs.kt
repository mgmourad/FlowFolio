package com.flowfolio.feature.library

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
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

        is LibraryDialog.FoldersToShelvesAsk -> FoldersToShelvesDialog(
            folders = dialog.shelves,
            books = dialog.books.size,
            onYes = { never -> viewModel.onFoldersToShelves(dialog, never) },
            onNo = { never -> viewModel.onFoldersToShelvesDeclined(never) }
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
            confirmButton = { DialogButton(R.string.action_copy_instead) { viewModel.onCopyInstead(dialog.request) } },
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

/** Import: files (one or several) or a whole folder, from a small menu under the + button (owner, 2026-10-26). */
@Composable
internal fun ImportMenu(actions: LibraryActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlowFolioIconButton(onClick = { open = true }, modifier = Modifier.testTag(LibraryTestTags.IMPORT)) {
            FlowFolioGlyph(Glyph.Add, contentDescription = stringResource(R.string.action_import))
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            ImportEntry(R.string.import_files) {
                open = false
                actions.onImport()
            }
            ImportEntry(R.string.import_folder) {
                open = false
                actions.onImportFolder()
            }
        }
    }
}

@Composable
private fun ImportEntry(label: Int, onClick: () -> Unit) {
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(label), style = FlowFolio.title) },
        onClick = onClick
    )
}

/** An import running: how far a batch has got, or an open-ended bar for a single book. */
@Composable
internal fun ImportProgress(progress: Pair<Int, Int>?) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
        if (progress == null) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth(),
                color = FlowFolio.palette.title,
                trackColor = FlowFolio.palette.edge
            )
        } else {
            val (done, total) = progress
            LinearProgressIndicator(
                progress = { done.toFloat() / total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
                color = FlowFolio.palette.title,
                trackColor = FlowFolio.palette.edge
            )
            Text(
                stringResource(R.string.import_progress, done + 1, total),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 6.dp).testTag(LibraryTestTags.IMPORT_PROGRESS)
            )
        }
    }
}

/** Asks before a folder's books leave the library. */
@Composable
internal fun RemoveFolderDialog(folder: FolderEntry, onConfirm: () -> Unit, onClose: () -> Unit) {
    BulkDeleteDialog(
        androidx.compose.ui.res.pluralStringResource(R.plurals.delete_books_title, folder.bookCount, folder.bookCount),
        stringResource(R.string.remove_folder_body, folder.name),
        onConfirm = {
            onConfirm()
            onClose()
        },
        onDismiss = onClose
    )
}

/**
 * The library's order: the same six the shelf page offers (owner, M4). It orders the books inside each shelf row
 * rather than across the library, so the books stay on their shelves.
 */
@Composable
internal fun SortMenu(current: ShelfSort, onSort: (ShelfSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlowFolioIconButton(onClick = { open = true }) {
            FlowFolioGlyph(Glyph.Sort, contentDescription = stringResource(R.string.action_sort))
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            ShelfSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        val label = when (sort) {
                            ShelfSort.CUSTOM -> R.string.sort_custom
                            ShelfSort.NAME -> R.string.sort_name
                            ShelfSort.DATE_ADDED -> R.string.sort_date_added
                            ShelfSort.PROGRESS -> R.string.sort_progress
                            ShelfSort.READ_TIME -> R.string.sort_read_time
                            ShelfSort.READ_GOAL -> R.string.sort_read_goal
                        }
                        Text(
                            stringResource(label),
                            style = if (sort == current) FlowFolio.title else FlowFolio.item
                        )
                    },
                    onClick = {
                        open = false
                        onSort(sort)
                    }
                )
            }
        }
    }
}

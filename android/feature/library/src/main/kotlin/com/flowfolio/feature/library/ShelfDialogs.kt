package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Shelf

/**
 * "Add to shelf": every shelf with a tick for the ones [bookTitle] is on, and a "New shelf" row that makes one and
 * puts the book on it. Confirming sets the book's shelves to the ticked ones.
 */
@Composable
internal fun ShelfPickerDialog(
    bookTitle: String,
    shelves: List<Shelf>,
    bookId: String,
    onConfirm: (Set<Long>) -> Unit,
    onNewShelf: () -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by rememberSaveable(bookId) {
        mutableStateOf(shelves.filter { bookId in it.bookIds }.map { it.id }.toLongArray())
    }
    FlowFolioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shelf_picker_title, bookTitle)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (shelves.isEmpty()) {
                    Text(stringResource(R.string.shelf_picker_empty), style = FlowFolio.hint)
                }
                shelves.forEach { shelf ->
                    val on = shelf.id in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(on, role = Role.Checkbox) {
                                chosen = if (it) {
                                    chosen + shelf.id
                                } else {
                                    chosen.filterNot { id -> id == shelf.id }
                                        .toLongArray()
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = FlowFolio.palette.title,
                                uncheckedColor = FlowFolio.palette.hint,
                                checkmarkColor = FlowFolio.palette.background
                            )
                        )
                        Text(shelf.name, style = FlowFolio.item, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                TextButton(onClick = onNewShelf, modifier = Modifier.testTag(LibraryTestTags.NEW_SHELF)) {
                    Text(stringResource(R.string.shelf_new), style = FlowFolio.title)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen.toSet()) }) {
                Text(stringResource(R.string.action_done), style = FlowFolio.title)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), style = FlowFolio.hint) }
        }
    )
}

/** Name a new shelf, or rename one ([initial] is its current name). */
@Composable
internal fun ShelfNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val p = FlowFolio.palette
    FlowFolioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.shelf_name_hint), style = FlowFolio.hint) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = p.title,
                    unfocusedBorderColor = p.edge,
                    cursorColor = p.title,
                    focusedTextColor = p.text,
                    unfocusedTextColor = p.text
                ),
                modifier = Modifier.fillMaxWidth().testTag(LibraryTestTags.SHELF_NAME)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save), style = FlowFolio.title)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), style = FlowFolio.hint) }
        }
    )
}

private const val MAX_NAME = 60

/**
 * "Add to shelf" for [book]: the picker, and from its "New shelf" row a name dialog that makes the shelf with the
 * book on it. Calls [onDone] when either closes.
 */
@Composable
internal fun ShelveBookFlow(book: Book, shelves: List<Shelf>, viewModel: ShelvesViewModel, onDone: () -> Unit) {
    var naming by rememberSaveable(book.id) { mutableStateOf(false) }
    if (naming) {
        ShelfNameDialog(stringResource(R.string.shelf_new), "", onConfirm = {
            viewModel.create(it, book.id)
            onDone()
        }, onDismiss = onDone)
    } else {
        ShelfPickerDialog(
            bookTitle = book.title,
            shelves = shelves,
            bookId = book.id,
            onConfirm = {
                viewModel.setShelves(book.id, it)
                onDone()
            },
            onNewShelf = { naming = true },
            onDismiss = onDone
        )
    }
}

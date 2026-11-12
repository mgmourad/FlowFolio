package com.flowfolio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlatCover
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.rememberSearchKeyboard
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Shelf

/** One entry of a press-and-hold menu; [warn] entries (removing) are in the warning colour. */
@Composable
internal fun HoldEntry(label: Int, warn: Boolean = false, glyph: Glyph? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label), style = if (warn) FlowFolio.subItem else FlowFolio.title) },
        // A glyph where one says the action plainer than the words do (owner, M5 round: the bin on Remove).
        leadingIcon = glyph?.let {
            {
                FlowFolioGlyph(
                    it,
                    contentDescription = null,
                    color = if (warn) FlowFolio.palette.danger else FlowFolio.palette.ink2,
                    size = 18.dp,
                    glow = false
                )
            }
        },
        onClick = onClick
    )
}

/**
 * "Add books" to [shelf] (owner, 2026-10-26): every book in the library, searchable, each with a tick; the books
 * already on the shelf are ticked and stay. Done puts the newly ticked ones on the shelf, first.
 */
@Composable
internal fun AddBooksDialog(shelf: Shelf, books: List<Book>, onConfirm: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var chosen by rememberSaveable(shelf.id) { mutableStateOf(emptyList<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = books.filter { it.matches(query) }
    FlowFolioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_books_title, shelf.name)) },
        text = {
            Column {
                if (books.size > SEARCH_FROM) BookSearch(query) { query = it }
                LazyColumn(Modifier.heightIn(max = LIST_MAX).testTag(LibraryTestTags.ADD_BOOKS_LIST)) {
                    items(shown, key = { it.id }) { book ->
                        val already = book.id in shelf.bookIds
                        PickRow(book, ticked = already || book.id in chosen, enabled = !already) { on ->
                            chosen = if (on) chosen + book.id else chosen - book.id
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }, modifier = Modifier.testTag(LibraryTestTags.ADD_BOOKS_DONE)) {
                Text(
                    if (chosen.isEmpty()) {
                        stringResource(R.string.action_done)
                    } else {
                        pluralStringResource(R.plurals.add_books_confirm, chosen.size, chosen.size)
                    },
                    style = FlowFolio.title
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), style = FlowFolio.hint) }
        }
    )
}

@Composable
private fun BookSearch(query: String, onQuery: (String) -> Unit) {
    val p = FlowFolio.palette
    val searchKeyboard = rememberSearchKeyboard()
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        keyboardOptions = searchKeyboard.options,
        keyboardActions = searchKeyboard.actions,
        placeholder = { Text(stringResource(R.string.search_hint), style = FlowFolio.hint) },
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.ink,
            unfocusedBorderColor = p.edge,
            cursorColor = p.ink,
            focusedTextColor = p.text,
            unfocusedTextColor = p.text
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

/** A book with its little cover, title and author, and a tick. */
@Composable
private fun PickRow(book: Book, ticked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val cover by rememberCover(book)
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(ticked, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = ticked, onCheckedChange = null, enabled = enabled, colors = tickColors())
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(PICK_COVER_WIDTH, PICK_COVER_HEIGHT)
                .clip(RoundedCornerShape(3.dp))
                .background(FlowFolio.palette.surface2)
        ) { FlatCover(book.face(cover), Modifier.size(PICK_COVER_WIDTH, PICK_COVER_HEIGHT)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                book.title,
                style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            book.author?.let {
                Text(
                    it,
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * "Add to shelf" for the books chosen in select mode (owner, 2026-10-26): tick shelves to put all [count] books on;
 * nothing is taken off any shelf. "New shelf" names one and puts them on it.
 */
@Composable
internal fun ShelveBooksDialog(
    count: Int,
    shelves: List<Shelf>,
    onConfirm: (Set<Long>) -> Unit,
    onNewShelf: () -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    FlowFolioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.shelve_books_title, count, count)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (shelves.isEmpty()) Text(stringResource(R.string.shelf_picker_empty), style = FlowFolio.hint)
                shelves.forEach { shelf ->
                    val on = shelf.id in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(on, role = Role.Checkbox) {
                                chosen =
                                    if (it) chosen + shelf.id else chosen - shelf.id
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = on, onCheckedChange = null, colors = tickColors())
                        Text(shelf.name, style = FlowFolio.item, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                TextButton(onClick = onNewShelf) { Text(stringResource(R.string.shelf_new), style = FlowFolio.title) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(chosen.toSet())
            }, modifier = Modifier.testTag(LibraryTestTags.SHELVE_BOOKS_DONE)) {
                Text(stringResource(R.string.action_done), style = FlowFolio.title)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), style = FlowFolio.hint) }
        }
    )
}

/** [ShelveBooksDialog], and naming a new shelf from it; calls [onDone] when either closes. */
@Composable
internal fun ShelveBooksFlow(
    bookIds: List<String>,
    shelves: List<Shelf>,
    viewModel: ShelvesViewModel,
    onDone: () -> Unit
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        ShelfDialog(stringResource(R.string.shelf_new), "", null, onConfirm = { name, color ->
            viewModel.createWith(name, color, bookIds)
            onDone()
        }, onDismiss = onDone)
    } else {
        ShelveBooksDialog(bookIds.size, shelves, onConfirm = {
            viewModel.addToShelves(bookIds, it)
            onDone()
        }, onNewShelf = { naming = true }, onDismiss = onDone)
    }
}

@Composable
private fun tickColors() = CheckboxDefaults.colors(
    checkedColor = FlowFolio.palette.ink,
    uncheckedColor = FlowFolio.palette.ink2,
    checkmarkColor = FlowFolio.palette.background,
    disabledCheckedColor = FlowFolio.palette.ink2
)

/** With more books than this, the picker offers a search field. */
private const val SEARCH_FROM = 8
private val LIST_MAX = 440.dp
private val PICK_COVER_WIDTH = 30.dp
private val PICK_COVER_HEIGHT = 42.dp

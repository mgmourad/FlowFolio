package com.flowfolio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Shelf
import com.flowfolio.core.model.ShelfColor

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

/** Name a new shelf, or rename a shelf or book ([initial] is its current name, [hint] the empty-field hint). */
@Composable
internal fun ShelfNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: String = stringResource(R.string.shelf_name_hint)
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    FlowFolioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NameField(name, hint) { name = it.take(MAX_NAME) } },
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

/**
 * Make or edit a shelf: its name and its colour (owner, 2026-10-22): the theme's, or one of Material You's six. The
 * shelf's card is outlined in it, and its row in the library takes it.
 */
@Composable
internal fun ShelfDialog(
    title: String,
    initialName: String,
    initialColor: ShelfColor?,
    onConfirm: (String, ShelfColor?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableStateOf(initialColor) }
    FlowFolioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                NameField(name, stringResource(R.string.shelf_name_hint)) { name = it.take(MAX_NAME) }
                Text(
                    stringResource(R.string.shelf_colour),
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                ShelfColorRow(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), color) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save), style = FlowFolio.title)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), style = FlowFolio.hint) }
        }
    )
}

@Composable
private fun NameField(name: String, hint: String, onName: (String) -> Unit) {
    val p = FlowFolio.palette
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        singleLine = true,
        placeholder = { Text(hint, style = FlowFolio.hint) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.title,
            unfocusedBorderColor = p.edge,
            cursorColor = p.title,
            focusedTextColor = p.text,
            unfocusedTextColor = p.text
        ),
        modifier = Modifier.fillMaxWidth().testTag(LibraryTestTags.SHELF_NAME)
    )
}

/** The seven shelf colours as round swatches: the theme's (null, marked with a ring) and Material You's six. */
@Composable
private fun ShelfColorRow(selected: ShelfColor?, onSelect: (ShelfColor?) -> Unit) {
    val p = FlowFolio.palette
    val choices = listOf<ShelfColor?>(null) + ShelfColor.entries
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { c ->
            val on = c == selected
            val label = stringResource(c?.label ?: R.string.shelf_colour_theme)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .widthIn(max = SWATCH)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .border(if (on) 3.dp else 0.dp, p.text, CircleShape)
                        .padding(if (on) 5.dp else 0.dp)
                        .clip(CircleShape)
                        .background(c.color)
                        .selectable(on, role = Role.RadioButton) { onSelect(c) }
                        .semantics { contentDescription = label }
                        .testTag(LibraryTestTags.SHELF_COLOUR_PREFIX + (c?.name ?: "THEME")),
                    contentAlignment = Alignment.Center
                ) {
                    if (on) {
                        val swatch = c.color
                        FlowFolioGlyph(
                            Glyph.Check,
                            contentDescription = null,
                            color = if (swatch.luminance() > INK_FLIP) Color.Black else Color.White,
                            size = SWATCH_TICK,
                            glow = false
                        )
                    }
                }
            }
        }
    }
}

private val ShelfColor.label: Int
    get() = when (this) {
        ShelfColor.BLUE -> R.string.seed_blue
        ShelfColor.TEAL -> R.string.seed_teal
        ShelfColor.GREEN -> R.string.seed_green
        ShelfColor.AMBER -> R.string.seed_amber
        ShelfColor.ROSE -> R.string.seed_rose
        ShelfColor.VIOLET -> R.string.seed_violet
    }

private const val MAX_NAME = 60
private val SWATCH = 40.dp
private val SWATCH_TICK = 18.dp

/** Above this luminance a swatch takes black ink, below it white. */
private const val INK_FLIP = 0.5f

/**
 * "Add to shelf" for [book]: the picker, and from its "New shelf" row a name dialog that makes the shelf with the
 * book on it. Calls [onDone] when either closes.
 */
@Composable
internal fun ShelveBookFlow(book: Book, shelves: List<Shelf>, viewModel: ShelvesViewModel, onDone: () -> Unit) {
    var naming by rememberSaveable(book.id) { mutableStateOf(false) }
    if (naming) {
        ShelfDialog(stringResource(R.string.shelf_new), "", null, onConfirm = { name, color ->
            viewModel.create(name, book.id, color)
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

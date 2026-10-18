package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.data.Found
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookType
import kotlinx.coroutines.launch

/** Edit details: the fields the details page shows, as the reader knows them. Opened from the details page's menu. */
@Composable
fun EditDetailsScreen(
    onDone: () -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
    lookup: LookupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val book = state.book ?: return
    EditDetailsContent(book, onCancel = onDone, onLookUp = lookup::find) { title, author, details ->
        viewModel.saveDetails(title, author, details, onDone)
    }
}

/** The form, filled from [book] once; Save hands back the edited title, author and details. */
@Composable
fun EditDetailsContent(
    book: Book,
    onCancel: () -> Unit,
    onLookUp: (suspend (Book) -> Found?)? = null,
    onSave: (String, String?, BookDetails) -> Unit
) {
    val d = book.details
    val form = rememberSaveable(book.id, saver = EditForm.Saver) { EditForm.of(book) }
    var type by rememberSaveable(book.id) { mutableStateOf(d.type) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(insets)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag(LibraryTestTags.EDIT_DETAILS),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = FORM_WIDTH).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowFolioIconButton(onClick = onCancel, glass = true) {
                    FlowFolioGlyph(Glyph.Back, stringResource(R.string.action_back), color = FlowFolio.palette.text)
                }
                Text(
                    stringResource(R.string.edit_details_title),
                    style = FlowFolioType.screenTitle.merge(FlowFolio.title),
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
            Section(R.string.edit_section_book) { BookFields(form, type) { type = it } }
            Section(R.string.edit_section_author) {
                Field(R.string.edit_author_role, form.authorRole) { form.authorRole = it }
                Field(R.string.edit_author_bio, form.authorBio, lines = DESCRIPTION_LINES) { form.authorBio = it }
            }
            onLookUp?.let { LookUpRow(book, form, type, it) { found -> type = type ?: found } }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                FlowFolioGhostButton(stringResource(R.string.action_cancel), onCancel)
                FlowFolioPrimaryButton(
                    stringResource(R.string.edit_save),
                    onClick = { if (form.title.isNotBlank()) onSave(form.title, form.author, form.details(type)) },
                    modifier = Modifier.testTag(LibraryTestTags.SAVE_DETAILS)
                )
            }
        }
    }
}

/** The Book section: title, subtitle, author, edition, release, publisher, ISBN, language, type and description. */
@Composable
private fun BookFields(form: EditForm, type: BookType?, onType: (BookType?) -> Unit) {
    Field(R.string.edit_title, form.title) { form.title = it }
    Field(R.string.edit_subtitle, form.subtitle) { form.subtitle = it }
    Field(R.string.info_author, form.author) { form.author = it }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(R.string.info_edition, form.edition, Modifier.weight(1f)) { form.edition = it }
        Field(R.string.info_released, form.released, Modifier.weight(1f)) { form.released = it }
    }
    Field(R.string.info_publisher, form.publisher) { form.publisher = it }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(R.string.info_isbn, form.isbn, Modifier.weight(1f), KeyboardType.Ascii) { form.isbn = it }
        Field(R.string.info_language, form.language, Modifier.weight(1f)) { form.language = it }
    }
    Text(
        stringResource(R.string.info_type),
        style = FlowFolioType.rowLabel.merge(FlowFolio.item),
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
    )
    FlowFolioSegmented(
        BookType.entries.map { stringResource(it.label) },
        BookType.entries.indexOf(type),
        { onType(BookType.entries[it].takeIf { t -> t != type }) }
    )
    Field(R.string.edit_description, form.description, lines = DESCRIPTION_LINES) { form.description = it }
}

/**
 * "Look up online" (ADR-0017): asks Open Library about the book as typed so far and fills only the empty fields, for
 * the reader to check before saving.
 */
@Composable
private fun LookUpRow(
    book: Book,
    form: EditForm,
    type: BookType?,
    onLookUp: suspend (Book) -> Found?,
    onType: (BookType?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Int?>(null) }
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        FlowFolioGhostButton(stringResource(R.string.edit_lookup), onClick = {
            if (busy) return@FlowFolioGhostButton
            busy = true
            scope.launch {
                val typed = book.copy(
                    title = form.title,
                    author = form.author.ifBlank {
                        null
                    },
                    details = form.details(type)
                )
                val found = onLookUp(typed)
                outcome =
                    if (found != null &&
                        form.fillEmpty(found)
                    ) {
                        R.string.edit_lookup_found
                    } else {
                        R.string.edit_lookup_none
                    }
                found?.details?.type?.let { onType(it) }
                busy = false
            }
        }, modifier = Modifier.testTag(LibraryTestTags.LOOKUP))
        outcome?.let {
            Text(
                stringResource(it),
                style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Text(
        stringResource(title).uppercase(),
        style = FlowFolioType.eyebrow.merge(FlowFolio.subItem),
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp)
    )
    FlowFolioCard(Modifier.fillMaxWidth(), radius = 24.dp) { Column { content() } }
}

@Composable
private fun Field(
    label: Int,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
    lines: Int = 1,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = lines == 1,
        minLines = lines,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = keyboard),
        modifier = modifier.padding(vertical = 4.dp)
    )
}

private val FORM_WIDTH = 640.dp
private const val DESCRIPTION_LINES = 4

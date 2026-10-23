package com.flowfolio.feature.library

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.marker
import com.flowfolio.core.designsystem.rememberSelection
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.shareQuote

/**
 * One book's annotations, in page order: each passage in its colour, its note, page and date. Tap to open the book
 * there; swipe right to share or edit the note, left to delete.
 */
@Composable
fun BookAnnotationsScreen(
    onBack: () -> Unit,
    onRead: (Book) -> Unit,
    viewModel: AnnotationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Annotation?>(null) }
    val book = state.book
    val selection = rememberSelection()
    var confirmBulk by remember { mutableStateOf(false) }
    BookAnnotationsContent(
        state,
        onBack,
        AnnotationActions(
            onOpen = { a -> book?.let { b -> viewModel.open(a) { onRead(b) } } },
            onShare = { a -> book?.let { shareAnnotation(context, a, it) } },
            onNote = { editing = it },
            onDelete = viewModel::remove
        ),
        selection
    ) { confirmBulk = true }
    if (confirmBulk) {
        val n = selection.chosen.size
        BulkDeleteDialog(
            pluralStringResource(R.plurals.delete_annotations_title, n, n),
            stringResource(R.string.delete_cannot_undo),
            onConfirm = {
                viewModel.removeAll(selection.chosen.map(String::toLong))
                selection.done()
                confirmBulk = false
            },
            onDismiss = { confirmBulk = false }
        )
    }
    BackHandler(selection.active) { selection.done() }
    editing?.let { a -> NoteDialog(a, onSave = { viewModel.setNote(a, it) }) { editing = null } }
}

@Composable
fun BookAnnotationsContent(
    state: AnnotationsUiState,
    onBack: () -> Unit,
    actions: AnnotationActions,
    selection: Selection = rememberSelection(),
    onDeleteChosen: () -> Unit = {}
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        Modifier.fillMaxSize().testTag(LibraryTestTags.BOOK_ANNOTATIONS),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = insets.calculateTopPadding(),
            bottom = insets.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(Modifier.widthIn(max = LIST_WIDTH).fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowFolioIconButton(onClick = onBack, glass = true, modifier = Modifier.padding(end = 14.dp)) {
                        FlowFolioGlyph(Glyph.Back, stringResource(R.string.action_back), color = FlowFolio.palette.text)
                    }
                    Text(
                        stringResource(R.string.annotations_title),
                        style = FlowFolioType.screenTitle.merge(FlowFolio.title),
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectControls(selection, state.annotations.map { it.id.toString() }, onDeleteChosen)
                    }
                }
                state.book?.let {
                    Text(
                        it.title,
                        style = FlowFolioType.sub.merge(FlowFolio.hint),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        items(state.annotations, key = { it.id }) { a ->
            Row(Modifier.animateItem().widthIn(max = LIST_WIDTH).fillMaxWidth()) {
                if (selection.active) {
                    // Select mode: a tap chooses the card (it stays still here); no swiping.
                    val key = a.id.toString()
                    Box {
                        AnnotationCard(a, state.chapters[a.id]) { selection.toggle(key) }
                        SelectionMark(selection, key)
                    }
                } else {
                    AnnotationRow(a, state.chapters[a.id], actions)
                }
            }
        }
    }
}

@Composable
private fun AnnotationRow(a: Annotation, chapter: String?, actions: AnnotationActions) {
    SwipeRow(
        startWidth = ACTION * 2,
        endWidth = ACTION,
        start = { close ->
            SwipeAction(R.string.action_share, Glyph.GoTo, FlowFolio.palette.item) {
                close()
                actions.onShare(a)
            }
            SwipeAction(R.string.action_note, Glyph.Edit, FlowFolio.palette.title) {
                close()
                actions.onNote(a)
            }
        },
        end = { close ->
            SwipeAction(R.string.action_delete, Glyph.Trash, FlowFolio.palette.subItem) {
                close()
                actions.onDelete(a)
            }
        },
        modifier = Modifier.testTag(LibraryTestTags.annotation(a.id))
    ) {
        AnnotationCard(a, chapter) { actions.onOpen(a) }
    }
}

/** The chapter, then the passage with its colour bar, the note under it, and its page and date. */
@Composable
private fun AnnotationCard(a: Annotation, chapter: String?, onOpen: () -> Unit) {
    val context = LocalContext.current
    val bar = a.color.marker
    FlowFolioCard(Modifier.fillMaxWidth().clickable(onClick = onOpen), radius = 22.dp, padding = PaddingValues(16.dp)) {
        Row {
            Canvas(Modifier.size(4.dp, 56.dp)) { drawRect(bar) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                // The chapter it belongs to, on top (owner, 2026-10-22).
                chapter?.let {
                    Text(
                        it,
                        style = FlowFolioType.eyebrow.merge(FlowFolio.title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Text(
                    "“${a.text}”",
                    style = FlowFolioType.sub.copy(
                        fontFamily = FlowFolioType.heroTitle.fontFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ).merge(FlowFolio.item)
                )
                a.note?.let {
                    Text(
                        it,
                        style = FlowFolioType.rowLabel.merge(FlowFolio.subItem),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Text(
                    stringResource(
                        R.string.annotation_where,
                        a.label ?: (a.page + 1).toString(),
                        DateFormat.getMediumDateFormat(context).format(a.updatedAtMillis)
                    ),
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** Edit or remove (blank) a note. */
@Composable
private fun NoteDialog(a: Annotation, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(a.id) { mutableStateOf(a.note.orEmpty()) }
    FlowFolioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_note)) },
        text = {
            OutlinedTextField(value = text, onValueChange = {
                text = it
            }, minLines = 3, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text)
                onDismiss()
            }) { Text(stringResource(R.string.edit_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Shares the passage in quotation marks with the book, author and page under it. */
internal fun shareAnnotation(context: Context, a: Annotation, book: Book) {
    val text = shareQuote(a.text, book.title, book.author, a.label ?: (a.page + 1).toString())
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private val ACTION = 84.dp
private val LIST_WIDTH = 720.dp

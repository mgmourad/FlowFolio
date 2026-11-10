package com.flowfolio.feature.library

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioMenuDivider
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.VoiceBar
import com.flowfolio.core.designsystem.marker
import com.flowfolio.core.designsystem.rememberSelection
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.shareQuote
import kotlinx.coroutines.delay

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
    val playingPath by viewModel.voicePlaying.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(playingPath) {
        while (playingPath != null) {
            tick++
            delay(VOICE_TICK_MS)
        }
    }
    val playback = VoicePlayback(
        playingPath = playingPath,
        positionMillis = if (tick >= 0 && playingPath != null) viewModel.voiceMillis else 0,
        onPlayPause = viewModel::playVoice
    )
    val book = state.book
    val selection = rememberSelection()
    var confirmBulk by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalVoicePlayback provides playback) {
        BookAnnotationsContent(
            state,
            onBack,
            AnnotationActions(
                onOpen = { a -> book?.let { b -> viewModel.open(a) { onRead(b) } } },
                onShare = { a -> book?.let { shareAnnotation(context, a, it) } },
                onNote = { editing = it },
                onDelete = viewModel::remove,
                onSelect = { selection.startWith(it.id.toString()) }
            ),
            selection
        ) { confirmBulk = true }
    }
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

@Suppress("LongMethod")
@Composable
fun BookAnnotationsContent(
    state: AnnotationsUiState,
    onBack: () -> Unit,
    actions: AnnotationActions,
    selection: Selection = rememberSelection(),
    onDeleteChosen: () -> Unit = {}
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Notes (default) are the written ones; Voicemarks are the spoken ones (owner, 2026-11-07).
    val visible = remember(state.annotations, tab) {
        state.annotations.filter { a -> if (tab == 0) a.voice == null else a.voice != null }
    }
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
            Column(Modifier.widthIn(max = LIST_WIDTH).fillMaxWidth().padding(top = 10.dp, bottom = 4.dp).reveal(0)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowFolioIconButton(
                        onClick = onBack,
                        glass = true,
                        modifier = Modifier.padding(end = 14.dp),
                        tooltip = stringResource(R.string.action_back)
                    ) {
                        FlowFolioGlyph(Glyph.Back, stringResource(R.string.action_back), color = FlowFolio.palette.text)
                    }
                    Text(
                        stringResource(R.string.annotations_title),
                        style = FlowFolioType.screenTitle.merge(FlowFolio.title),
                        modifier = Modifier.weight(1f)
                    )
                    SelectControls(selection, visible.map { it.id.toString() })
                }
                state.book?.let {
                    Text(
                        it.title,
                        style = FlowFolioType.sub.merge(FlowFolio.hint),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                NotesTabs(tab) { tab = it }
                SelectionRow(selection, visible.map { it.id.toString() }, onDeleteChosen)
            }
        }
        itemsIndexed(visible, key = { _, entry -> entry.id }) { i, a ->
            Row(Modifier.animateItem().reveal(1 + i).widthIn(max = LIST_WIDTH).fillMaxWidth()) {
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

/** Notes / VoiceMarks, right-aligned with a small glyph beside each (owner, 2026-11-07). */
@Composable
private fun NotesTabs(tab: Int, onTab: (Int) -> Unit) {
    val p = FlowFolio.palette
    val track = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Row(
            Modifier.clip(track).background(p.background.copy(alpha = TRACK_ALPHA)).padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NotesTab(Glyph.Edit, stringResource(R.string.annotations_notes), tab == 0) { onTab(0) }
            NotesTab(Glyph.Mic, stringResource(R.string.annotations_voicemarks), tab == 1) { onTab(1) }
        }
    }
}

@Composable
private fun NotesTab(glyph: Glyph, label: String, on: Boolean, onClick: () -> Unit) {
    val p = FlowFolio.palette
    val chip = RoundedCornerShape(9.dp)
    Row(
        Modifier
            .clip(chip)
            .then(if (on) Modifier.background(p.selected, chip) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowFolioGlyph(
            glyph,
            contentDescription = null,
            color = if (on) p.title else p.hint,
            size = 14.dp,
            glow = false
        )
        Text(
            label,
            style = FlowFolioType.pill.copy(fontSize = FlowFolioType.chip.fontSize)
                .merge(if (on) FlowFolio.title else FlowFolio.hint)
        )
    }
}

private const val TRACK_ALPHA = 0.5f

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
            SwipeAction(R.string.action_delete, Glyph.Trash, FlowFolio.palette.danger) {
                close()
                actions.onDelete(a)
            }
        },
        modifier = Modifier.testTag(LibraryTestTags.annotation(a.id))
    ) {
        var menu by remember { mutableStateOf(false) }
        Box {
            AnnotationCard(a, chapter, onHold = { menu = true }) { actions.onOpen(a) }
            FlowFolioMenu(expanded = menu, onDismissRequest = { menu = false }) {
                actions.onSelect?.let { select ->
                    HoldEntry(R.string.action_select) {
                        menu = false
                        select(a)
                    }
                    FlowFolioMenuDivider()
                }
                HoldEntry(R.string.action_delete, warn = true) {
                    menu = false
                    actions.onDelete(a)
                }
            }
        }
    }
}

/** The chapter, then the passage with its colour bar, the note under it, and its page and date. */
@Suppress("LongMethod")
@Composable
private fun AnnotationCard(a: Annotation, chapter: String?, onHold: (() -> Unit)? = null, onOpen: () -> Unit) {
    val voice = LocalVoicePlayback.current
    val context = LocalContext.current
    val bar = a.color.marker
    FlowFolioCard(
        Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onHold),
        radius = 22.dp,
        padding = PaddingValues(16.dp)
    ) {
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
                // A spoken note plays here as well as in the book (owner, M5).
                a.voice?.let { note ->
                    VoiceBar(
                        levels = note.levels,
                        millis = note.millis,
                        playing = voice.playingPath == note.path,
                        positionMillis = if (voice.playingPath == note.path) voice.positionMillis else 0,
                        onPlayPause = { voice.onPlayPause(note.path) },
                        onStop = { voice.onPlayPause(note.path) },
                        modifier = Modifier.padding(top = 10.dp)
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

/**
 * What the annotation cards need to play a voice note back (owner, M5), so a card stays a drawing and the player
 * stays in the ViewModel.
 *
 * Passed through the composition rather than down three layers of parameters: it is the same for every card on the
 * screen, and nothing between the screen and a card has any use for it.
 */
internal data class VoicePlayback(
    val playingPath: String? = null,
    val positionMillis: Int = 0,
    val onPlayPause: (String) -> Unit = {}
)

internal val LocalVoicePlayback = staticCompositionLocalOf { VoicePlayback() }

/** How often the playback bar redraws while a note plays. */
private const val VOICE_TICK_MS = 120L

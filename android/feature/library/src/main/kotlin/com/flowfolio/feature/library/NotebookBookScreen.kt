package com.flowfolio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.data.MarkKind
import com.flowfolio.core.data.countByBook
import com.flowfolio.core.designsystem.BookCover
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioMenuHeader
import com.flowfolio.core.designsystem.FlowFolioMenuItem
import com.flowfolio.core.designsystem.FlowFolioSnackbarHost
import com.flowfolio.core.designsystem.FlowFolioTabs
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.card
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.designsystem.marker
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.designsystem.showFlowFolio
import com.flowfolio.core.designsystem.swipeTabs
import com.flowfolio.core.model.Book
import kotlinx.coroutines.launch

/**
 * A book's notebook (ADR-0052): its cover and name, tabs for All and each kind with counts, Sort (page, newest,
 * type), then each mark with its page and kind, and Open (at its page), Share and Delete (with Undo).
 */
@Suppress("LongMethod")
@Composable
fun NotebookBookScreen(
    bookId: String,
    initialKind: MarkKind?,
    onBack: () -> Unit,
    onRead: (Book) -> Unit,
    viewModel: NotebookViewModel = hiltViewModel()
) {
    val p = FlowFolio.palette
    val marks by viewModel.all.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == bookId }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var sort by rememberSaveable { mutableStateOf(EntrySort.PAGE) }
    val counts = countByBook(
        marks.annotations.filter { it.bookId == bookId },
        marks.bookmarks.filter { it.bookId == bookId },
        marks.words.filter { it.bookId == bookId }
    )[bookId] ?: com.flowfolio.core.data.BookMarkCounts()
    val entries = viewModel.entriesOf(bookId, marks, kind, sort)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val gutter = libraryGutter()
    var playing by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().chromeStart()) {
        val kinds = listOf<MarkKind?>(null) + MARK_ORDER
        LazyColumn(
            Modifier
                .fillMaxSize()
                .swipeTabs(kinds.indexOf(kind), kinds.size) { kind = kinds[it] }
                .testTag(LibraryTestTags.NOTEBOOK_BOOK),
            contentPadding = PaddingValues(
                start = gutter,
                end = gutter,
                top = insets.calculateTopPadding() + 10.dp,
                bottom = insets.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "head") {
                Column(Modifier.reveal(0)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlowFolioIconButton(onClick = onBack, tooltip = stringResource(R.string.action_back)) {
                            FlowFolioGlyph(Glyph.Back, null, size = 20.dp)
                        }
                        Spacer(Modifier.weight(1f))
                        EntrySortMenu(sort) { sort = it }
                    }
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (book != null) {
                            val cover by rememberCover(book)
                            BookCover(book.face(cover), Modifier.width(72.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                book?.title.orEmpty(),
                                style = FlowFolioType.heroTitle.copy(color = p.ink),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            book?.author?.let {
                                Text(it, style = FlowFolioType.body.copy(fontSize = 13.sp, color = p.ink2))
                            }
                            CountPills(counts, Modifier.padding(top = 10.dp))
                        }
                    }
                    val tabs = listOf<MarkKind?>(null) + MARK_ORDER
                    Box(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState())) {
                        FlowFolioTabs(
                            tabs.map { stringResource(it?.label ?: R.string.filter_all) },
                            tabs.indexOf(kind),
                            { kind = tabs[it] },
                            counts = tabs.map { it?.let { k -> counts[k] } ?: counts.total }
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.notebook_book_empty),
                        style = FlowFolioType.body.copy(color = p.ink3),
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
            items(entries, key = { it.key }) { entry ->
                EntryCard(
                    entry = entry,
                    playing = playing == entry.key,
                    onOpen = { book?.let { b -> viewModel.open(entry) { onRead(b) } } },
                    onShare = { share(context, shareText(entry, book), "text/plain") },
                    onPlay = {
                        if (playing == entry.key) {
                            viewModel.stopVoice()
                            playing = null
                        } else {
                            playing = entry.key
                            viewModel.play(entry) { playing = null }
                        }
                    },
                    onDelete = {
                        val (commit, undo) = viewModel.remove(entry)
                        scope.launch {
                            val r = snackbar.showFlowFolio(
                                resources.getString(R.string.notebook_removed, resources.getString(entry.kind.single)),
                                Glyph.Trash,
                                resources.getString(R.string.action_undo)
                            )
                            if (r == SnackbarResult.ActionPerformed) undo() else commit()
                        }
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
        FlowFolioSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}

/** One mark (ADR-0052): its page and kind, its words (a quote ruled in its highlight colour), and its actions. */
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod") // one card for each kind of mark
@Composable
private fun EntryCard(
    entry: NotebookEntry,
    playing: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .card(shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(
                    entry.page?.let { stringResource(R.string.page_short, it + 1) },
                    stringResource(entry.kind.single)
                ).joinToString(" · ").uppercase(),
                style = FlowFolioType.eyebrow.copy(fontSize = 10.5.sp, color = p.ink3),
                modifier = Modifier.weight(1f)
            )
        }
        val a = entry.annotation
        when (entry.kind) {
            MarkKind.HIGHLIGHT, MarkKind.NOTE, MarkKind.VOICE -> if (a != null) {
                Row(Modifier.padding(top = 8.dp).height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(a.color.marker, RoundedCornerShape(2.dp))
                    )
                    Text(
                        a.text,
                        style = FlowFolioType.prose.copy(fontSize = 15.sp, lineHeight = 22.sp, color = p.ink),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                    )
                }
                a.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = FlowFolioType.body.copy(fontStyle = FontStyle.Italic, color = p.ink2),
                        modifier = Modifier.padding(top = 8.dp, end = 8.dp)
                    )
                }
                a.voice?.let { v ->
                    Row(
                        Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(p.surface2)
                            .clickable(role = Role.Button, onClick = onPlay)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FlowFolioGlyph(if (playing) Glyph.Pause else Glyph.Play, null, color = p.accent, size = 16.dp)
                        Text(
                            "%d:%02d".format(v.millis / MS_MIN, (v.millis / MS_SEC) % SEC_MIN),
                            style = FlowFolioType.body.copy(fontSize = 12.5.sp, color = p.ink2)
                        )
                    }
                }
            }

            MarkKind.BOOKMARK -> Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowFolioGlyph(Glyph.Bookmark, null, color = entry.bookmark?.color?.color ?: p.accent, size = 18.dp)
                Text(
                    stringResource(R.string.notebook_ribbon, (entry.page ?: 0) + 1),
                    style = FlowFolioType.body.copy(color = p.ink)
                )
            }

            MarkKind.WORD -> entry.word?.let { w ->
                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(w.word, style = FlowFolioType.heroTitle.copy(fontSize = 19.sp, color = p.ink))
                    val pos = w.partOfSpeech ?: if (w.definition == null && w.translation != null) {
                        stringResource(R.string.word_translation_label)
                    } else {
                        null
                    }
                    pos?.let {
                        Text(
                            it,
                            style = FlowFolioType.pill.copy(color = p.ink2),
                            modifier = Modifier
                                .background(p.surface2, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                w.definition?.let {
                    Text(
                        it,
                        style = FlowFolioType.body.copy(color = p.ink2),
                        modifier = Modifier.padding(top = 4.dp, end = 8.dp)
                    )
                }
                w.translation?.let {
                    Text(
                        listOfNotNull(w.fromLang, w.toLang).joinToString(" → ").let { l ->
                            if (l.isEmpty()) it else "$l: $it"
                        },
                        style = FlowFolioType.body.copy(fontSize = 13.sp, color = p.ink2),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            FlowFolioIconButton(
                onOpen,
                size = 36.dp,
                ghost = true,
                tooltip = stringResource(R.string.notebook_open_page)
            ) {
                FlowFolioGlyph(Glyph.OpenBook, null, color = p.ink2, size = 18.dp)
            }
            FlowFolioIconButton(onShare, size = 36.dp, ghost = true, tooltip = stringResource(R.string.action_share)) {
                FlowFolioGlyph(Glyph.Share, null, color = p.ink2, size = 18.dp)
            }
            FlowFolioIconButton(
                onDelete,
                size = 36.dp,
                ghost = true,
                tooltip = stringResource(R.string.action_delete),
                modifier = Modifier.testTag(LibraryTestTags.notebookDelete(entry.key))
            ) {
                FlowFolioGlyph(Glyph.Trash, null, color = p.danger, size = 18.dp)
            }
        }
    }
}

@Composable
private fun EntrySortMenu(sort: EntrySort, onSort: (EntrySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlowFolioIconButton(onClick = { open = true }, tooltip = stringResource(R.string.action_sort)) {
            FlowFolioGlyph(Glyph.Sort, null, size = 20.dp)
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            FlowFolioMenuHeader(stringResource(R.string.library_sort_by))
            listOf(
                EntrySort.PAGE to R.string.entry_sort_page,
                EntrySort.NEWEST to R.string.entry_sort_newest,
                EntrySort.TYPE to R.string.entry_sort_type
            ).forEach { (s, label) ->
                FlowFolioMenuItem(stringResource(label), {
                    open = false
                    onSort(s)
                }, checked = s == sort)
            }
        }
    }
}

/** A mark as a reader would share it: the quote and note, or the word, with the book and page. */
private fun shareText(entry: NotebookEntry, book: Book?): String = buildString {
    entry.annotation?.let { a ->
        append("“").append(a.text).append("”")
        a.note?.takeIf { it.isNotBlank() }?.let { append("\n— ").append(it) }
    }
    entry.word?.let { w -> append(w.word).append(": ").append(w.definition ?: w.translation ?: "") }
    if (entry.kind == MarkKind.BOOKMARK) append("Ribbon")
    book?.let { append("\n\n").append(it.title) }
    entry.page?.let { append(", p. ").append(it + 1) }
}

private const val MS_SEC = 1_000L
private const val MS_MIN = 60_000L
private const val SEC_MIN = 60L

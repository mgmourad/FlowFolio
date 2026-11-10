package com.flowfolio.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioMenuDivider
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.ScrollToTopOnTab
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.marker
import com.flowfolio.core.designsystem.rememberSelection
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Book

/**
 * The Annotations tab (owner, 2026-10-18): the books you have highlighted in, most recent first, each with how many
 * highlights and notes it has, their colours and the latest passage. Tap a book for its annotations.
 */
@Composable
fun AnnotationsScreen(onOpenBook: (Book) -> Unit, viewModel: AnnotationsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection = rememberSelection()
    var confirmBulk by remember { mutableStateOf(false) }
    AnnotationsContent(state, onOpenBook, selection) { confirmBulk = true }
    if (confirmBulk) {
        val n = selection.chosen.size
        BulkDeleteDialog(
            pluralStringResource(R.plurals.delete_annotated_books_title, n, n),
            stringResource(R.string.delete_cannot_undo),
            onConfirm = {
                viewModel.removeBooks(selection.chosen)
                selection.done()
                confirmBulk = false
            },
            onDismiss = { confirmBulk = false }
        )
    }
    BackHandler(selection.active) { selection.done() }
}

@Suppress("LongMethod")
@Composable
fun AnnotationsContent(
    state: AnnotationsUiState,
    onOpenBook: (Book) -> Unit,
    selection: Selection = rememberSelection(),
    onDeleteChosen: () -> Unit = {}
) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredBooks = remember(state.books, state.chapters, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            state.books
        } else {
            state.books.mapNotNull { item ->
                val score = maxOf(
                    Fuzzy.score(q, item.book.title),
                    Fuzzy.score(q, item.book.author),
                    Fuzzy.score(q, state.chapters[item.latest.id]),
                    Fuzzy.score(q, item.latest.text),
                    Fuzzy.score(q, item.latest.note)
                )
                if (score > 0) item to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }
    }
    Box(Modifier.fillMaxSize().chromeStart().testTag(LibraryTestTags.ANNOTATIONS)) {
        val grid = rememberLazyGridState()
        // Tapping the tab shows this page from the top (owner, M4c).
        ScrollToTopOnTab("annotations", grid)
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(CARD_MIN),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + chrome.calculateBottomPadding() + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val keys = filteredBooks.map { it.book.id }
                Column(Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp).reveal(0)) {
                    ScreenHeader(
                        title = stringResource(R.string.annotations_title),
                        slogan = stringResource(R.string.annotations_slogan),
                        actions = {
                            SelectControls(selection, keys) {
                                FlowFolioIconButton(
                                    onClick = {
                                        searchOpen = !searchOpen
                                        if (!searchOpen) query = ""
                                    },
                                    modifier = Modifier.testTag(LibraryTestTags.SEARCH),
                                    tooltip = stringResource(R.string.action_search)
                                ) {
                                    FlowFolioGlyph(Glyph.Search, stringResource(R.string.action_search))
                                }
                            }
                        }
                    )
                    AnimatedVisibility(
                        visible = searchOpen,
                        enter = expandVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) +
                            slideInVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { -it / 3 } +
                            fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)),
                        exit = shrinkVertically(tween(Motion.FAST_MS, easing = Motion.EaseInOut)) +
                            fadeOut(tween(Motion.FAST_MS, easing = Motion.EaseInOut))
                    ) {
                        SearchField(
                            query = query,
                            onQuery = { query = it },
                            hintRes = R.string.search_annotations_hint,
                            autoFocus = true
                        )
                    }
                    SelectionRow(selection, keys, onDeleteChosen)
                    if (state.total > 0) {
                        Text(
                            stringResource(
                                R.string.bookmarks_sub,
                                pluralStringResource(R.plurals.highlight_count, state.total, state.total),
                                pluralStringResource(R.plurals.book_count, state.books.size, state.books.size)
                            ),
                            style = FlowFolioType.sub.merge(FlowFolio.hint),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            if (!state.loading && state.total == 0) {
                item(span = { GridItemSpan(maxLineSpan) }) { NoAnnotations(Modifier.reveal(1)) }
            }
            itemsIndexed(filteredBooks, key = { _, entry -> entry.book.id }) { i, item ->
                Box(Modifier.animateItem().reveal(1 + i)) {
                    AnnotatedBookItem(item, state.chapters[item.latest.id], selection, onOpenBook)
                }
            }
        }
    }
}

/** A book's card in the Annotations tab: a tap opens it (or chooses it); press and hold offers Select and Open. */
@Composable
private fun AnnotatedBookItem(item: AnnotatedBook, chapter: String?, selection: Selection, onOpenBook: (Book) -> Unit) {
    val key = item.book.id
    var menu by remember { mutableStateOf(false) }
    Box {
        AnnotatedBookCard(item, chapter, onHold = { if (!selection.active) menu = true }) {
            if (selection.active) selection.toggle(key) else onOpenBook(item.book)
        }
        SelectionMark(selection, key)
        FlowFolioMenu(expanded = menu, onDismissRequest = { menu = false }) {
            HoldEntry(R.string.action_select) {
                menu = false
                selection.startWith(key)
            }
            FlowFolioMenuDivider()
            HoldEntry(R.string.action_open) {
                menu = false
                onOpenBook(item.book)
            }
        }
    }
}

/**
 * A book with annotations: its cover, title and author, the counts, its colours, and the latest passage under the
 * chapter it is in.
 */
@Suppress("LongMethod")
@Composable
private fun AnnotatedBookCard(
    item: AnnotatedBook,
    chapter: String?,
    onHold: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val cover by rememberCover(item.book)
    FlowFolioCard(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onHold)
            .testTag(LibraryTestTags.annotatedBook(item.book.id)),
        radius = 22.dp,
        padding = PaddingValues(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MiniCover(item.book, cover, Modifier)
            Column(Modifier.weight(1f)) {
                Text(
                    item.book.title,
                    style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.book.author?.let {
                    Text(
                        it,
                        style = FlowFolioType.sub.merge(FlowFolio.hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.colours.forEach { c -> Dot(c.marker) }
                    Spacer(Modifier.weight(1f))
                    Count(Glyph.Highlight, item.highlights)
                    Count(Glyph.Edit, item.notes)
                    Count(Glyph.Mic, item.voicemarks)
                }
                chapter?.let {
                    Text(
                        it,
                        style = FlowFolioType.rowDetail.merge(FlowFolio.title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Text(
                    "“${item.latest.text}”",
                    style = FlowFolioType.sub.copy(
                        fontFamily = FlowFolioType.heroTitle.fontFamily,
                        fontStyle = FontStyle.Italic
                    )
                        .merge(FlowFolio.hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun Dot(colour: Color) {
    Canvas(Modifier.size(10.dp)) { drawCircle(colour) }
}

/** A glyph with its count beside it (M2b): highlight, note and voicemark counts on a book's card. */
@Composable
private fun Count(glyph: Glyph, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        FlowFolioGlyph(glyph, contentDescription = null, color = FlowFolio.palette.ink2, size = 14.dp, glow = false)
        Text("$n", style = FlowFolioType.pill.merge(FlowFolio.subItem))
    }
}

@Composable
private fun NoAnnotations(modifier: Modifier) {
    FlowFolioCard(modifier.fillMaxWidth().padding(top = 12.dp), radius = 24.dp, padding = PaddingValues(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            FlowFolioGlyph(Glyph.Highlight, contentDescription = null, color = FlowFolio.palette.ink, size = 34.dp)
            Text(
                stringResource(R.string.annotations_empty_title),
                style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                stringResource(R.string.annotations_empty_body),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private val CARD_MIN = 300.dp

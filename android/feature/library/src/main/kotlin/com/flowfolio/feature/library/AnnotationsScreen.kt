package com.flowfolio.feature.library

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
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

@Composable
fun AnnotationsContent(
    state: AnnotationsUiState,
    onOpenBook: (Book) -> Unit,
    selection: Selection = rememberSelection(),
    onDeleteChosen: () -> Unit = {}
) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().chromeStart().testTag(LibraryTestTags.ANNOTATIONS)) {
        LazyVerticalGrid(
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
                Column(Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp).reveal(0)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.annotations_title),
                            style = FlowFolioType.screenTitle.merge(FlowFolio.title),
                            modifier = Modifier.weight(1f)
                        )
                        SelectControls(selection, state.books.map { it.book.id })
                    }
                    SelectionRow(selection, state.books.map { it.book.id }, onDeleteChosen)
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
            itemsIndexed(state.books, key = { _, entry -> entry.book.id }) { i, item ->
                val key = item.book.id
                Box(Modifier.animateItem().reveal(1 + i)) {
                    AnnotatedBookCard(item, state.chapters[item.latest.id]) {
                        if (selection.active) selection.toggle(key) else onOpenBook(item.book)
                    }
                    SelectionMark(selection, key)
                }
            }
        }
    }
}

/**
 * A book with annotations: its cover, title and author, the counts, its colours, and the latest passage under the
 * chapter it is in.
 */
@Composable
private fun AnnotatedBookCard(item: AnnotatedBook, chapter: String?, onClick: () -> Unit) {
    val cover by rememberCover(item.book)
    FlowFolioCard(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(LibraryTestTags.annotatedBook(item.book.id)),
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
                Text(
                    stringResource(
                        R.string.annotations_counts,
                        pluralStringResource(R.plurals.highlight_count, item.highlights, item.highlights),
                        pluralStringResource(R.plurals.note_count, item.notes, item.notes)
                    ),
                    style = FlowFolioType.pill.merge(FlowFolio.subItem),
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.colours.forEach { c -> Dot(c.marker) }
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

@Composable
private fun NoAnnotations(modifier: Modifier) {
    FlowFolioCard(modifier.fillMaxWidth().padding(top = 12.dp), radius = 24.dp, padding = PaddingValues(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            FlowFolioGlyph(Glyph.Highlight, contentDescription = null, color = FlowFolio.palette.title, size = 34.dp)
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

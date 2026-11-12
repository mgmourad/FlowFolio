package com.flowfolio.feature.library

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.data.BookMarkCounts
import com.flowfolio.core.data.MarkKind
import com.flowfolio.core.designsystem.BookCover
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioMenuDivider
import com.flowfolio.core.designsystem.FlowFolioMenuHeader
import com.flowfolio.core.designsystem.FlowFolioMenuItem
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.ScrollToTopOnTab
import com.flowfolio.core.designsystem.card
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.designsystem.swipeTabs
import com.flowfolio.core.model.Book

/**
 * The Notebook (ADR-0052): "Everything you marked, book by book". Search, Sort and ⋯ (export, share) in the header;
 * chips for each kind of mark with its count; then one card per book with its count pills, two columns unfolded with
 * the latest mark's words. A card opens the book's own notebook ([onOpenBook]).
 */
@Suppress("LongMethod")
@Composable
fun NotebookHome(onOpenBook: (Book, MarkKind?) -> Unit, viewModel: NotebookViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val chrome = LocalChromePadding.current
    val wide = libraryWide()
    val gutter = libraryGutter()
    val grid = rememberLazyGridState()
    var searching by rememberSaveable { mutableStateOf(false) }
    ScrollToTopOnTab("notebook", grid)
    // The kind chips are the Notebook's tabs: a swipe across the page moves to the next or previous kind (owner,
    // 2026-11-12). The chip row scrolls sideways by itself, so a swipe on it scrolls the chips instead.
    val kinds = remember { listOf<MarkKind?>(null) + MARK_ORDER }
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Fixed(if (wide) 2 else 1),
        modifier = Modifier
            .fillMaxSize()
            .chromeStart()
            .swipeTabs(kinds.indexOf(state.filter).coerceAtLeast(0), kinds.size) { viewModel.onFilter(kinds[it]) }
            .testTag(LibraryTestTags.NOTEBOOK),
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = insets.calculateTopPadding(),
            bottom = insets.calculateBottomPadding() + chrome.calculateBottomPadding() + 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.reveal(0)) {
                ScreenHeader(
                    title = stringResource(R.string.notebook_title),
                    slogan = stringResource(R.string.notebook_slogan),
                    actions = {
                        FlowFolioIconButton(
                            onClick = {
                                searching = !searching
                                if (!searching) viewModel.onQuery("")
                            },
                            glowing = searching,
                            tooltip = stringResource(R.string.action_search),
                            modifier = Modifier.testTag(LibraryTestTags.SEARCH)
                        ) { FlowFolioGlyph(Glyph.Search, null, size = 20.dp) }
                        NotebookSortMenu(state.sort, viewModel::onSort)
                        NotebookMoreMenu(
                            onMarkdown = {
                                share(
                                    context,
                                    notebookMarkdown(viewModel.books.value, viewModel.all.value),
                                    "text/markdown"
                                )
                            },
                            onCsv = {
                                share(context, notebookCsv(viewModel.books.value, viewModel.all.value), "text/csv")
                            },
                            onShare = {
                                share(
                                    context,
                                    notebookMarkdown(viewModel.books.value, viewModel.all.value),
                                    "text/plain"
                                )
                            }
                        )
                    }
                )
                if (searching) {
                    SearchField(state.query, viewModel::onQuery, R.string.notebook_search_hint, autoFocus = true)
                }
                KindChips(state.totals, state.filter, viewModel::onFilter, Modifier.padding(top = 14.dp, bottom = 6.dp))
            }
        }
        if (!state.loading && state.books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.notebook_empty),
                    style = FlowFolioType.body.copy(color = FlowFolio.palette.ink3),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                )
            }
        }
        itemsIndexed(state.books, key = { _, b -> b.book.id }) { i, row ->
            NotebookBookCard(row, wide, { onOpenBook(row.book, state.filter) }, Modifier.reveal(1 + i))
        }
    }
}

/** The kind chips (ADR-0052): All and each kind, each with its count. */
@Composable
internal fun KindChips(
    totals: BookMarkCounts,
    selected: MarkKind?,
    onSelect: (MarkKind?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowFolioChip(stringResource(R.string.filter_all), selected == null, { onSelect(null) }, count = totals.total)
        MARK_ORDER.forEach { k ->
            FlowFolioChip(
                stringResource(k.label),
                selected == k,
                { onSelect(if (selected == k) null else k) },
                glyph = k.glyph,
                count = totals[k],
                modifier = Modifier.testTag(LibraryTestTags.notebookKind(k.name))
            )
        }
    }
}

/** A book in the Notebook: cover, title, author, a pill per kind (dim at zero) and, unfolded, its latest words. */
@Composable
private fun NotebookBookCard(row: NotebookBook, wide: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(22.dp)
    val cover by rememberCover(row.book)
    Row(
        modifier
            .fillMaxWidth()
            .card(shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(14.dp)
            .testTag(LibraryTestTags.notebookBook(row.book.id)),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BookCover(row.book.face(cover), Modifier.width(if (wide) 64.dp else 56.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.book.title,
                style = FlowFolioType.rowLabel.copy(color = p.ink),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            row.book.author?.let {
                Text(it, style = FlowFolioType.body.copy(fontSize = 12.5.sp, color = p.ink2), maxLines = 1)
            }
            CountPills(row.counts, Modifier.padding(top = 10.dp))
            if (wide && row.latest != null) {
                Text(
                    "“${row.latest}”",
                    style = FlowFolioType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = p.ink2),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

/** A pill per kind with its count; zero is dimmed (ADR-0052). */
@Composable
internal fun CountPills(counts: BookMarkCounts, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MARK_ORDER.forEach { k ->
            val n = counts[k]
            val alpha = if (n == 0) DIM else 1f
            Row(
                Modifier
                    .background(p.surface2.copy(alpha = alpha), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlowFolioGlyph(
                    k.glyph,
                    stringResource(k.label),
                    color = p.ink2.copy(alpha = alpha),
                    size = 12.dp,
                    glow = false
                )
                Text(
                    "$n",
                    style = FlowFolioType.body.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.ink.copy(alpha = alpha)
                    )
                )
            }
        }
    }
}

@Composable
private fun NotebookSortMenu(sort: NotebookSort, onSort: (NotebookSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlowFolioIconButton(onClick = { open = true }, tooltip = stringResource(R.string.action_sort)) {
            FlowFolioGlyph(Glyph.Sort, null, size = 20.dp)
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            FlowFolioMenuHeader(stringResource(R.string.library_sort_by))
            listOf(
                NotebookSort.RECENT to R.string.notebook_sort_recent,
                NotebookSort.TITLE to R.string.notebook_sort_title,
                NotebookSort.MOST to R.string.notebook_sort_most
            ).forEach { (s, label) ->
                FlowFolioMenuItem(stringResource(label), {
                    open = false
                    onSort(s)
                }, checked = s == sort)
            }
        }
    }
}

@Composable
private fun NotebookMoreMenu(onMarkdown: () -> Unit, onCsv: () -> Unit, onShare: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun run(action: () -> Unit): () -> Unit = {
        open = false
        action()
    }
    Box {
        FlowFolioIconButton(onClick = { open = true }, tooltip = stringResource(R.string.action_more)) {
            FlowFolioGlyph(Glyph.More, null, size = 20.dp)
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            FlowFolioMenuHeader(stringResource(R.string.notebook_export))
            FlowFolioMenuItem(stringResource(R.string.notebook_export_md), run(onMarkdown), glyph = Glyph.Page)
            FlowFolioMenuItem(stringResource(R.string.notebook_export_csv), run(onCsv), glyph = Glyph.Contents)
            FlowFolioMenuDivider()
            FlowFolioMenuItem(stringResource(R.string.notebook_share_all), run(onShare), glyph = Glyph.Share)
        }
    }
}

/** Hands [text] to the system's share sheet: nothing leaves the phone unless the reader picks where it goes. */
internal fun share(context: Context, text: String, mime: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.notebook_title))
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** The kinds in the order the Notebook shows them. */
internal val MARK_ORDER = listOf(MarkKind.HIGHLIGHT, MarkKind.NOTE, MarkKind.BOOKMARK, MarkKind.VOICE, MarkKind.WORD)

internal val MarkKind.label: Int
    get() = when (this) {
        MarkKind.HIGHLIGHT -> R.string.kind_highlights
        MarkKind.NOTE -> R.string.kind_notes
        MarkKind.BOOKMARK -> R.string.kind_bookmarks
        MarkKind.VOICE -> R.string.kind_voicemarks
        MarkKind.WORD -> R.string.kind_words
    }

internal val MarkKind.single: Int
    get() = when (this) {
        MarkKind.HIGHLIGHT -> R.string.kind_highlight
        MarkKind.NOTE -> R.string.kind_note
        MarkKind.BOOKMARK -> R.string.kind_bookmark
        MarkKind.VOICE -> R.string.kind_voicemark
        MarkKind.WORD -> R.string.kind_word
    }

internal val MarkKind.glyph: Glyph
    get() = when (this) {
        MarkKind.HIGHLIGHT -> Glyph.Highlight
        MarkKind.NOTE -> Glyph.EditBox
        MarkKind.BOOKMARK -> Glyph.Bookmark
        MarkKind.VOICE -> Glyph.Mic
        MarkKind.WORD -> Glyph.Translate
    }

private const val DIM = 0.4f

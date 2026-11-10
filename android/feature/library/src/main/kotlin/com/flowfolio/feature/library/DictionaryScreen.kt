package com.flowfolio.feature.library

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioPill
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.ScrollToTopOnTab
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.DictionaryWord
import com.flowfolio.core.model.pageLabel

/**
 * The Dictionary tab (owner, M3T3): laid out like Annotations, one group per book, where each entry shows the word,
 * its translation (when translated) and its English definition (when defined). Searchable, and tapping an entry opens
 * its place in the book.
 */
@Composable
fun DictionaryScreen(
    onRead: (Book) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: DictionaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val booksById = remember(state.groups) {
        state.groups.mapNotNull { it.book }.associateBy { it.id }
    }
    DictionaryContent(
        state = state,
        onQuery = viewModel::onQuery,
        onOpenWord = { word -> viewModel.open(word, booksById, onRead) },
        onOpenBook = onRead,
        onBack = onBack
    )
}

@Composable
fun DictionaryContent(
    state: DictionaryUiState,
    onQuery: (String) -> Unit,
    onOpenWord: (DictionaryWord) -> Unit,
    onOpenBook: (Book) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().chromeStart().testTag(LibraryTestTags.DICTIONARY)) {
        val grid = rememberLazyGridState()
        // Tapping the tab shows this page from the top (owner, M4c).
        ScrollToTopOnTab("dictionary", grid)
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(DICT_CARD_MIN),
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
                DictionaryHeader(state, onQuery, onBack, Modifier.reveal(0))
            }
            if (!state.loading && state.totalWords == 0) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyDictionary(Modifier.reveal(1)) }
            } else if (!state.loading && state.groups.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { NoMatch() }
            }
            itemsIndexed(state.groups, key = { _, g -> g.key }) { i, group ->
                Box(Modifier.animateItem().reveal(1 + i)) {
                    DictionaryGroupCard(group, onOpenWord, onOpenBook)
                }
            }
        }
    }
}

@Composable
private fun DictionaryHeader(
    state: DictionaryUiState,
    onQuery: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp)) {
        ScreenHeader(
            title = stringResource(R.string.dictionary_title),
            slogan = stringResource(R.string.dictionary_slogan),
            leading = onBack?.let { back ->
                {
                    FlowFolioIconButton(
                        onClick = back,
                        glass = true,
                        modifier = Modifier.padding(end = 14.dp),
                        tooltip = stringResource(R.string.action_back)
                    ) {
                        FlowFolioGlyph(Glyph.Back, stringResource(R.string.action_back), color = FlowFolio.palette.text)
                    }
                }
            }
        )
        state.book?.let { book ->
            Text(
                book.title,
                style = FlowFolioType.sub.merge(FlowFolio.item),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (state.totalWords > 0) {
            val wordsText = pluralStringResource(R.plurals.word_total, state.totalWords, state.totalWords)
            val subText = if (state.book == null && state.bookCount > 0) {
                stringResource(
                    R.string.bookmarks_sub,
                    wordsText,
                    pluralStringResource(R.plurals.book_count, state.bookCount, state.bookCount)
                )
            } else {
                wordsText
            }
            Text(
                subText,
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 6.dp)
            )
            SearchField(state.query, onQuery)
        }
    }
}

@Composable
private fun DictionaryGroupCard(
    group: DictionaryBookGroup,
    onOpenWord: (DictionaryWord) -> Unit,
    onOpenBook: (Book) -> Unit
) {
    val book = group.book
    FlowFolioCard(
        Modifier
            .fillMaxWidth()
            .testTag(LibraryTestTags.dictionaryGroup(group.key)),
        radius = 22.dp,
        padding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (book != null) Modifier.clickable { onOpenBook(book) } else Modifier),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (book != null) {
                    val cover by rememberCover(book)
                    MiniCover(book, cover, Modifier)
                } else {
                    FlowFolioGlyph(
                        Glyph.Translate,
                        contentDescription = null,
                        color = FlowFolio.palette.title,
                        size = 28.dp
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        book?.title ?: stringResource(R.string.dictionary_general_group),
                        style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        pluralStringResource(R.plurals.word_total, group.words.size, group.words.size),
                        style = FlowFolioType.pill.merge(FlowFolio.subItem),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            group.words.forEachIndexed { idx, word ->
                if (idx > 0) HorizontalDivider(color = FlowFolio.palette.edge)
                DictionaryWordRow(word, book) { onOpenWord(word) }
            }
        }
    }
}

@Composable
private fun DictionaryWordRow(word: DictionaryWord, book: Book?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (book != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                word.word,
                style = FlowFolioType.rowLabel.copy(
                    fontFamily = FlowFolioType.heroTitle.fontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 16.sp
                ).merge(FlowFolio.title),
                modifier = Modifier.weight(1f, fill = false)
            )
            word.partOfSpeech?.takeIf { it.isNotBlank() }?.let { pos ->
                FlowFolioPill(pos, FlowFolio.subItem)
            }
            val page = word.page
            if (book != null && page != null && page >= 0) {
                Text(
                    stringResource(R.string.page_short, book.pageLabel(page)),
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
                )
            }
        }
        word.translation?.let { tr ->
            val langs = if (word.fromLang != null && word.toLang != null) "${word.fromLang} → ${word.toLang}: " else ""
            Text(
                "$langs$tr",
                style = FlowFolioType.sub.merge(FlowFolio.item),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        word.definition?.let { def ->
            Text(
                def,
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyDictionary(modifier: Modifier = Modifier) {
    FlowFolioCard(modifier.fillMaxWidth().padding(top = 12.dp), radius = 24.dp, padding = PaddingValues(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            FlowFolioGlyph(Glyph.Translate, contentDescription = null, color = FlowFolio.palette.title, size = 34.dp)
            Text(
                stringResource(R.string.dictionary_empty_title),
                style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                stringResource(R.string.dictionary_empty_body),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private val DICT_CARD_MIN = 300.dp

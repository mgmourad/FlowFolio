package com.flowfolio.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.model.pageLabel

/**
 * The search's results (owner, M3T2) in place of the shelves while a query is typed: a card per group, its glyph,
 * name and count, its best few, and "Show all" for the rest. A book opens its details; a shelf its page; a highlight
 * or a ribbon its book's list of them; a word its book, when it came from one.
 */
internal fun LazyListScope.searchResults(
    results: SearchResults,
    actions: LibraryActions,
    expanded: MutableState<Set<SearchGroup>>
) {
    if (results.empty) {
        item(key = "search-none") { NoMatch() }
        return
    }
    results.groups.forEach { (group, hits) ->
        item(key = "search-${group.name}") {
            val open = group in expanded.value
            SearchGroupCard(group, hits, open, actions) {
                expanded.value = if (open) expanded.value - group else expanded.value + group
            }
        }
    }
}

@Composable
private fun SearchGroupCard(
    group: SearchGroup,
    hits: List<SearchHit>,
    open: Boolean,
    actions: LibraryActions,
    onMore: () -> Unit
) {
    FlowFolioCard(
        Modifier.fillMaxWidth().padding(
            horizontal = 18.dp,
            vertical = 6.dp
        ).testTag(LibraryTestTags.searchGroup(group)),
        radius = 20.dp,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 10.dp)
    ) {
        Column {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FlowFolioGlyph(group.glyph, contentDescription = null, size = 20.dp, glow = false)
                Text(
                    stringResource(group.label),
                    style = FlowFolioType.rowLabel.merge(FlowFolio.title),
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
                Text(hits.size.toString(), style = FlowFolioType.rowDetail.merge(FlowFolio.hint))
            }
            (if (open) hits else hits.take(FIRST_HITS)).forEach { HitRow(it, actions) }
            if (hits.size > FIRST_HITS) {
                Text(
                    if (open) {
                        stringResource(
                            R.string.search_show_fewer
                        )
                    } else {
                        stringResource(R.string.search_show_all, hits.size)
                    },
                    style = FlowFolioType.chip.merge(FlowFolio.item),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onMore)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** A found thing: its line, what it is from under it, opening its place. */
@Composable
private fun HitRow(hit: SearchHit, actions: LibraryActions) {
    val (line, from) = hit.lines()
    val onClick: (() -> Unit)? = when (hit) {
        is SearchHit.OfBook -> ({ actions.onOpen(LibraryItem(hit.book), SEARCH_ORIGIN) })
        is SearchHit.OfShelf -> ({ actions.onOpenShelf(hit.shelf.id) })
        is SearchHit.OfAnnotation -> actions.onAnnotations?.let { { it(hit.book) } }
        is SearchHit.OfBookmark -> actions.onBookmarks?.let { { it(hit.book) } }
        is SearchHit.OfWord -> hit.book?.let { b -> { actions.onOpen(LibraryItem(b), SEARCH_ORIGIN) } }
        is SearchHit.OfDefinition -> hit.book?.let { b -> { actions.onOpen(LibraryItem(b), SEARCH_ORIGIN) } }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            line,
            style = FlowFolioType.sub.merge(FlowFolio.item),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        from?.let {
            Text(
                it,
                style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A hit's main line and the line under it. */
@Composable
private fun SearchHit.lines(): Pair<String, String?> = when (this) {
    is SearchHit.OfBook -> book.title to book.author

    is SearchHit.OfShelf ->
        shelf.name to
            androidx.compose.ui.res.pluralStringResource(R.plurals.book_count, shelf.bookIds.size, shelf.bookIds.size)

    is SearchHit.OfAnnotation -> (annotation.note?.takeIf { it.isNotBlank() } ?: annotation.text) to
        stringResource(R.string.search_in_book, book.title, book.pageLabel(annotation.page))

    is SearchHit.OfBookmark -> stringResource(R.string.search_ribbon_page, book.pageLabel(bookmark.page)) to book.title

    is SearchHit.OfWord -> "${translation.source} → ${translation.translated}" to book?.title

    is SearchHit.OfDefinition -> "${definition.word} — ${definition.definition}" to book?.title
}

private val SearchGroup.glyph: Glyph
    get() = when (this) {
        SearchGroup.FAVORITES -> Glyph.Star
        SearchGroup.BOOKS -> Glyph.Book
        SearchGroup.SHELVES -> Glyph.Shelves
        SearchGroup.ANNOTATIONS -> Glyph.Highlight
        SearchGroup.BOOKMARKS -> Glyph.Bookmark
        SearchGroup.DICTIONARY -> Glyph.Translate
    }

@get:StringRes
private val SearchGroup.label: Int
    get() = when (this) {
        SearchGroup.FAVORITES -> R.string.shelf_favorites
        SearchGroup.BOOKS -> R.string.search_books
        SearchGroup.SHELVES -> R.string.search_shelves
        SearchGroup.ANNOTATIONS -> R.string.search_annotations
        SearchGroup.BOOKMARKS -> R.string.search_bookmarks
        SearchGroup.DICTIONARY -> R.string.search_dictionary
    }

private const val FIRST_HITS = 4
internal const val SEARCH_ORIGIN = "search"

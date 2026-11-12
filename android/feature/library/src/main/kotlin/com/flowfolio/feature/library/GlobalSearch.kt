package com.flowfolio.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.BookCover
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.rememberSearchKeyboard

/** The library's search bar (`.searchbar`, ADR-0052): a pill that opens the global search. */
@Composable
internal fun LibrarySearchBar(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(p.surface, shape)
            .border(1.dp, p.line, shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp)
            .testTag(LibraryTestTags.SEARCH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowFolioGlyph(Glyph.Search, null, color = p.ink3, size = 18.dp, glow = false)
        Text(
            stringResource(R.string.library_search_bar),
            style = FlowFolioType.body.copy(color = p.ink3),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The global search (`.globsearch`, ADR-0052), over the whole library: Back and a field in the accent, then the
 * recent books until two letters are typed, and from then on everything that matches, grouped (books, shelves,
 * highlights, ribbons, words). Back closes it.
 */
@Suppress("LongMethod") // one layout, read top to bottom
@Composable
internal fun GlobalSearch(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    results: SearchResults,
    recent: List<LibraryItem>,
    actions: LibraryActions
) {
    val p = FlowFolio.palette
    BackHandler(onBack = onClose)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val gutter = libraryGutter()
    val expanded = remember { mutableStateOf(emptySet<SearchGroup>()) }
    val typed = query.trim().length >= SEARCH_MINIMUM
    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .chromeStart()
            .testTag(LibraryTestTags.GLOBAL_SEARCH)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 12.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "field") {
                Row(
                    Modifier.padding(horizontal = gutter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FlowFolioIconButton(onClick = onClose, tooltip = stringResource(R.string.action_close)) {
                        FlowFolioGlyph(Glyph.Back, null, size = 20.dp)
                    }
                    SearchInput(query, onQuery, Modifier.weight(1f))
                }
            }
            when {
                !typed -> {
                    item(key = "recent-head") { GroupHead(stringResource(R.string.library_search_recent), gutter) }
                    items(recent, key = { "recent-${it.book.id}" }) { item ->
                        RecentRow(item, { actions.onOpen(item, SEARCH_ORIGIN) }, Modifier.padding(horizontal = gutter))
                    }
                    item(key = "hint") {
                        Text(
                            stringResource(R.string.library_search_hint),
                            style = FlowFolioType.body.copy(fontSize = 12.5.sp, color = p.ink2, lineHeight = 18.sp),
                            modifier = Modifier
                                .padding(horizontal = gutter, vertical = 16.dp)
                                .background(p.surface2, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }

                results.empty -> item(key = "nothing") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.library_search_nothing, query.trim()),
                            style = FlowFolioType.body.copy(color = p.ink3),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            stringResource(R.string.library_search_in_reader),
                            style = FlowFolioType.body.copy(fontSize = 12.sp, color = p.ink3),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 18.dp)
                        )
                    }
                }

                else -> searchResults(results, actions, expanded)
            }
        }
    }
}

/** The search field (`.field`): an accent outline, the glyph, and the cursor in it as soon as it opens. */
@Composable
private fun SearchInput(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .heightIn(min = 46.dp)
            .background(p.surface, shape)
            .border(1.5.dp, p.accent, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowFolioGlyph(Glyph.Search, null, color = p.ink3, size = 18.dp, glow = false)
        val searchKeyboard = rememberSearchKeyboard()
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            keyboardOptions = searchKeyboard.options,
            keyboardActions = searchKeyboard.actions,
            textStyle = FlowFolioType.body.copy(fontSize = 15.sp, color = p.ink),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.weight(1f).focusRequester(focus).testTag(LibraryTestTags.SEARCH_FIELD),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.library_search_field),
                            style = FlowFolioType.body.copy(fontSize = 15.sp, color = p.ink3),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            }
        )
    }
}

/** A group's head (`.rgrp`): small capitals in the third ink. */
@Composable
private fun GroupHead(text: String, gutter: androidx.compose.ui.unit.Dp) {
    Text(
        text.uppercase(),
        style = FlowFolioType.body.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.05.sp,
            color = FlowFolio.palette.ink3
        ),
        modifier = Modifier.padding(start = gutter + 4.dp, end = gutter, top = 20.dp, bottom = 4.dp)
    )
}

/** A recent book (`.res`): its small cover, title and author. */
@Composable
private fun RecentRow(item: LibraryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val cover by rememberCover(item.book)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(item.book.face(cover), Modifier.width(34.dp))
        Column {
            Text(
                item.book.title,
                style = FlowFolioType.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.book.author?.let {
                Text(it, style = FlowFolioType.body.copy(fontSize = 13.sp, color = p.ink2), maxLines = 1)
            }
        }
    }
}

/** The fewest letters a search runs on (ADR-0052): one letter matched too much to be useful. */
internal const val SEARCH_MINIMUM = 2

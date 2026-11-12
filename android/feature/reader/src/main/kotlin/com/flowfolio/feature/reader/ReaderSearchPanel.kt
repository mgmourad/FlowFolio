package com.flowfolio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.rememberSearchKeyboard

/**
 * In-book search (`.rpanel` search, ADR-0052): "Search in this book" and Esc to close; the field with the number of
 * matches inside it; Whole book or This chapter, and Match case; then the matches by chapter, each with its page and
 * a line around it, the match on the accent. Tapping one jumps there and highlights it.
 */
@Suppress("LongMethod", "LongParameterList") // the field, the scope chips and the grouped results
@Composable
internal fun ReaderSearchPanel(
    query: String,
    hits: List<SearchHit>,
    onQuery: (String) -> Unit,
    onJump: (SearchHit) -> Unit,
    navigation: BookNavigation,
    page: Int,
    matchCase: Boolean,
    onMatchCase: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val p = FlowFolio.palette
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var chapterOnly by rememberSaveable { mutableStateOf(false) }
    val here = PageLookup.chapterTitleAt(navigation.outline, page)
    val shown = if (chapterOnly) {
        hits.filter {
            PageLookup.chapterTitleAt(navigation.outline, it.page) == here
        }
    } else {
        hits
    }
    var current by remember(hits) { mutableIntStateOf(-1) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reader_search_title),
                style = FlowFolioType.dialogTitle.copy(fontSize = 20.sp, color = p.ink),
                modifier = Modifier.weight(1f)
            )
            Text(stringResource(R.string.reader_search_esc), style = FlowFolioType.rowDetail.copy(color = p.ink3))
        }
        SearchField(
            query,
            onQuery,
            count = if (query.trim().length >= MIN_CHARS) shown.size else null,
            onClose = onClose,
            modifier = Modifier.focusRequester(focus)
        )
        Row(
            Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowFolioChip(stringResource(R.string.reader_search_whole), !chapterOnly, { chapterOnly = false })
            FlowFolioChip(stringResource(R.string.reader_search_chapter), chapterOnly, { chapterOnly = true })
            FlowFolioChip(stringResource(R.string.reader_search_case), matchCase, { onMatchCase(!matchCase) })
        }
        when {
            query.isBlank() -> Unit

            query.trim().length < MIN_CHARS -> Note(R.string.reader_search_min)

            shown.isEmpty() -> Note(R.string.reader_search_empty)

            else -> {
                val groups = shown.withIndex().groupBy { it.value.chapter.orEmpty() }
                LazyColumn(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    groups.forEach { (chapter, entries) ->
                        item(key = "chapter-$chapter-${entries.first().index}") {
                            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)) {
                                Text(
                                    chapter.ifEmpty { stringResource(R.string.reader_search_no_chapter) }.uppercase(),
                                    style = FlowFolioType.eyebrow.copy(color = p.ink3),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${entries.size}", style = FlowFolioType.eyebrow.copy(color = p.ink3))
                            }
                        }
                        items(entries.size, key = { "hit-${entries[it].index}" }) { i ->
                            val (index, hit) = entries[i]
                            SearchResultRow(
                                hit,
                                PageLookup.labelOf(hit.page, navigation.labels),
                                index == current,
                                matchCase
                            ) {
                                current = index
                                onJump(hit)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** `.field`: the accent outline, the search glyph, the query and, once there is one, the number of matches. */
@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    count: Int?,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            // The gap above the field belongs out here: given to the text inside, it pushed the query, its hint and
            // the cursor below the field's middle (owner, 2026-11-12).
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(FIELD_HEIGHT)
            .border(1.5.dp, p.accent, shape)
            .background(p.surface, shape)
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
            textStyle = FieldText.copy(color = p.ink),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = searchKeyboard.options,
            keyboardActions = searchKeyboard.actions,
            modifier = modifier
                .weight(1f)
                .onPreviewKeyEvent { e ->
                    (e.type == KeyEventType.KeyDown && e.key == Key.Escape).also { if (it) onClose() }
                }
                .testTag(ReaderTestTags.SEARCH_FIELD),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.reader_search_hint),
                            style = FieldText.copy(color = p.ink3),
                            maxLines = 1
                        )
                    }
                    inner()
                }
            }
        )
        count?.let {
            Text(
                pluralStringResource(R.plurals.reader_search_count, it, it),
                style = FlowFolioType.rowDetail.copy(fontSize = 11.5.sp, color = p.ink3),
                maxLines = 1
            )
        }
    }
}

/** The query and its hint: one line of the font's own height, centred in the field. */
private val FieldText = FlowFolioType.body.copy(
    fontSize = 15.sp,
    lineHeight = 20.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
)

@Composable
private fun Note(text: Int) {
    Text(
        stringResource(text),
        style = FlowFolioType.body.copy(color = FlowFolio.palette.ink2),
        modifier = Modifier.padding(top = 14.dp, start = 4.dp)
    )
}

/** `.res`: the page on the left, the line around the match on the right; the one last opened on the second surface. */
@Composable
private fun SearchResultRow(hit: SearchHit, label: String, current: Boolean, matchCase: Boolean, onClick: () -> Unit) {
    val p = FlowFolio.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) p.surface2 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.reader_search_page_label, label),
            style = FlowFolioType.rowDetail.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink3),
            modifier = Modifier.widthIn(min = 38.dp).padding(top = 2.dp)
        )
        Text(
            highlightMatch(hit.snippet, hit.text, matchCase, p.accent, p.accentInk),
            style = FlowFolioType.body.copy(fontSize = 13.5.sp, lineHeight = 19.5.sp, color = p.ink2),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The matched phrase in a snippet on the accent, in the accent's ink (the mock's `mark`). */
private fun highlightMatch(
    snippet: String,
    match: String,
    matchCase: Boolean,
    fill: Color,
    ink: Color
): AnnotatedString {
    val index = snippet.indexOf(match, ignoreCase = !matchCase)
    if (index < 0) return AnnotatedString(snippet)
    return buildAnnotatedString {
        append(snippet.substring(0, index))
        withStyle(SpanStyle(color = ink, background = fill, fontWeight = FontWeight.SemiBold)) {
            append(snippet.substring(index, index + match.length))
        }
        append(snippet.substring(index + match.length))
    }
}

private val FIELD_HEIGHT = 46.dp
private const val MIN_CHARS = 2

package com.flowfolio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph

/** In-book search (Phase 6 M1): a field and the matches, each jumping to its page and highlighting it. */
@Suppress("LongMethod") // the field, its states and the grouped results
@Composable
internal fun ReaderSearchPanel(
    query: String,
    hits: List<SearchHit>,
    onQuery: (String) -> Unit,
    onJump: (SearchHit) -> Unit
) {
    // ADR-0052: two letters before searching, results by chapter with their pages, and ⌃ / ⌄ through them.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            label = { Text(stringResource(R.string.reader_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp)
                .focusRequester(focus)
                .testTag(ReaderTestTags.SEARCH_FIELD)
        )
        var current by remember(hits) { mutableIntStateOf(-1) }
        when {
            query.isBlank() -> Unit

            query.trim().length < MIN_CHARS -> Text(
                stringResource(R.string.reader_search_min),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 16.dp, start = 4.dp)
            )

            hits.isEmpty() -> Text(
                stringResource(R.string.reader_search_empty),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 16.dp, start = 4.dp)
            )

            else -> {
                // ADR-0052: how many, and ⌃ / ⌄ to step through them in order.
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.reader_search_count, hits.size, hits.size) +
                            if (current >= 0) "  ·  ${current + 1}/${hits.size}" else "",
                        style = FlowFolioType.sub.merge(FlowFolio.hint),
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    StepButton(Glyph.Back, R.string.reader_search_previous, rotation = 90f) {
                        current = if (current <= 0) hits.lastIndex else current - 1
                        onJump(hits[current])
                    }
                    StepButton(Glyph.Back, R.string.reader_search_next, rotation = -90f) {
                        current = if (current >= hits.lastIndex) 0 else current + 1
                        onJump(hits[current])
                    }
                }
                // Grouped by chapter, each group with its count; every match shows its page.
                val groups = hits.withIndex().groupBy { it.value.chapter.orEmpty() }
                LazyColumn(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    groups.forEach { (chapter, entries) ->
                        item(key = "chapter-$chapter-${entries.first().index}") {
                            Text(
                                (chapter.ifEmpty { stringResource(R.string.reader_search_no_chapter) }) +
                                    " · ${entries.size}",
                                style = FlowFolioType.rowDetail.merge(FlowFolio.title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
                            )
                        }
                        items(entries.size, key = { "hit-${entries[it].index}" }) { i ->
                            val (index, hit) = entries[i]
                            SearchResultRow(hit, index == current) {
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

@Composable
private fun StepButton(glyph: Glyph, label: Int, rotation: Float, onClick: () -> Unit) {
    FlowFolioIconButton(onClick = onClick, size = 36.dp, ghost = true, tooltip = stringResource(label)) {
        FlowFolioGlyph(
            glyph,
            null,
            size = 18.dp,
            glow = false,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, current: Boolean, onClick: () -> Unit) {
    val p = FlowFolio.palette
    Column(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(if (current) p.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            stringResource(R.string.reader_search_page, hit.page + 1),
            style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
        )
        Text(
            hit.snippet.run { highlightMatch(this, hit.text) },
            style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.item),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The matched phrase in a snippet, bold with a soft marker behind it. */
private fun highlightMatch(snippet: String, match: String): AnnotatedString {
    val index = snippet.indexOf(match, ignoreCase = true)
    if (index < 0) return AnnotatedString(snippet)
    return buildAnnotatedString {
        append(snippet.substring(0, index))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = MATCH_MARK)) {
            append(snippet.substring(index, index + match.length))
        }
        append(snippet.substring(index + match.length))
    }
}

private val MATCH_MARK = Color(0x4DFFD54F)

private const val MIN_CHARS = 2

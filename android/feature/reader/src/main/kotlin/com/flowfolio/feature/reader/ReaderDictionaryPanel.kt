package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.DictionaryRepository
import com.flowfolio.core.data.TranslationRepository
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioPill
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.model.Translation
import com.flowfolio.core.model.WordDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class ReaderDictionaryState(
    val query: String = "",
    val definitions: List<WordDefinition> = emptyList(),
    val translations: List<Translation> = emptyList()
)

@HiltViewModel
internal class ReaderDictionaryViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val dictionary: DictionaryRepository,
    private val translations: TranslationRepository
) : ViewModel() {
    private val bookId: String? = saved[ReaderViewModel.ARG_BOOK_ID]
    private val query = MutableStateFlow("")

    /** Forgets one definition (owner, M5 round). The word can always be looked up again. */
    fun forgetDefinition(id: Long) = viewModelScope.launch { dictionary.removeDefinition(id) }

    /** Forgets one translation. */
    fun forgetTranslation(id: Long) = viewModelScope.launch { translations.remove(id) }

    val state: StateFlow<ReaderDictionaryState> = combine(
        dictionary.observeDefinitions(),
        translations.history(),
        query
    ) { defs, trans, q ->
        val bookDefs = defs.filter { bookId == null || it.bookId == bookId || it.bookId == null }
        val bookTrans = trans.filter { bookId == null || it.bookId == bookId || it.bookId == null }
        val filteredDefs = if (q.isBlank()) {
            bookDefs
        } else {
            bookDefs.mapNotNull { d ->
                val score = maxOf(
                    fuzzyScore(q, d.word),
                    fuzzyScore(q, d.definition),
                    fuzzyScore(q, d.partOfSpeech)
                )
                if (score > 0) d to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }
        val filteredTrans = if (q.isBlank()) {
            bookTrans
        } else {
            bookTrans.mapNotNull { t ->
                val score = maxOf(
                    fuzzyScore(q, t.source),
                    fuzzyScore(q, t.translated)
                )
                if (score > 0) t to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }
        ReaderDictionaryState(
            query = q,
            definitions = filteredDefs,
            translations = filteredTrans
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), ReaderDictionaryState())

    fun onQuery(text: String) {
        query.value = text
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}

/**
 * Centered popup window in the reader for Dictionary items: left tab "Definitions", right tab "Translations", and a
 * search glyph in the top right corner for fast fuzzy search across input words and output definitions/translations.
 */
@Composable
internal fun ReaderDictionaryPanel(
    navigation: BookNavigation,
    onGo: (Int) -> Unit,
    viewModel: ReaderDictionaryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val tabs = listOf(
        SheetTab(stringResource(R.string.reader_tab_definitions), Glyph.Book, "reader_tab_definitions"),
        SheetTab(stringResource(R.string.reader_tab_translations), Glyph.Translate, "reader_tab_translations")
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(ReaderTestTags.DICTIONARY_PANEL)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.reader_dictionary),
                style = MaterialTheme.typography.titleLarge.merge(FlowFolio.title)
            )
            FlowFolioIconButton(
                onClick = {
                    searching = !searching
                    if (!searching) viewModel.onQuery("")
                },
                glass = true,
                size = 38.dp
            ) {
                FlowFolioGlyph(
                    Glyph.Search,
                    contentDescription = stringResource(R.string.reader_dictionary_search_hint),
                    color = if (searching) FlowFolio.palette.title else FlowFolio.palette.glyph,
                    size = 20.dp
                )
            }
        }
        AnimatedVisibility(searching) {
            DictionarySearchField(state.query, viewModel::onQuery)
        }
        Column(Modifier.padding(top = 10.dp)) {
            SheetTabs(tabs, tab) { tab = it }
        }
        DictionaryBody(state, tab, navigation, onGo, viewModel)
    }
}

/** Fast fuzzy match score against [text] for [query]; 0 when no match. */
internal fun fuzzyScore(query: String, text: String?): Int {
    val q = query.trim().lowercase()
    val t = text?.lowercase().orEmpty()
    val exactIdx = if (q.isNotEmpty() && t.isNotEmpty()) t.indexOf(q) else -1
    return when {
        t.isBlank() -> 0
        q.isEmpty() -> 1
        exactIdx >= 0 -> EXACT_BASE_SCORE - exactIdx.coerceAtMost(MAX_INDEX_PENALTY)
        else -> subsequenceScore(q, t)
    }
}

private fun subsequenceScore(q: String, t: String): Int {
    var qi = 0
    var score = 0
    var streak = 0
    for (i in t.indices) {
        if (qi < q.length && t[i] == q[qi]) {
            qi++
            streak++
            score += streak * STREAK_BONUS
        } else {
            streak = 0
        }
    }
    return if (qi == q.length) score.coerceAtLeast(1) else 0
}

internal val LIST_MAX_HEIGHT = 340.dp
private const val EXACT_BASE_SCORE = 1_000
private const val MAX_INDEX_PENALTY = 500
private const val STREAK_BONUS = 5

/** What the panel shows under its tabs: a search across everything, or the tab that is open. */
@Composable
private fun DictionaryBody(
    state: ReaderDictionaryState,
    tab: Int,
    navigation: BookNavigation,
    onGo: (Int) -> Unit,
    viewModel: ReaderDictionaryViewModel
) {
    if (state.query.isNotBlank()) {
        // A search is not a tab: it looks across every word and every result at once (owner, M4b).
        LookupResults(
            state.definitions,
            state.translations,
            navigation,
            onGo,
            viewModel::forgetDefinition,
            viewModel::forgetTranslation
        )
    } else if (tab == 0) {
        DefinitionsTabList(state.definitions, false, navigation, onGo, viewModel::forgetDefinition)
    } else {
        TranslationsTabList(state.translations, false, navigation, onGo, viewModel::forgetTranslation)
    }
}

@Composable
private fun DictionarySearchField(query: String, onQuery: (String) -> Unit) {
    val p = FlowFolio.palette
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.reader_dictionary_search_hint), style = FlowFolio.hint) },
        leadingIcon = { FlowFolioGlyph(Glyph.Search, contentDescription = null, size = 18.dp, glow = false) },
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.title,
            unfocusedBorderColor = p.edge,
            cursorColor = p.title,
            focusedTextColor = p.text,
            unfocusedTextColor = p.text,
            focusedContainerColor = p.surface.copy(alpha = 0.55f),
            unfocusedContainerColor = p.surface.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .focusRequester(focus)
    )
}

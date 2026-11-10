package com.flowfolio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.data.DefaultDictionaryRepository
import com.flowfolio.core.designsystem.ButtonStyle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioButton
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioTabs
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.glass

/** The Words panel's tabs (ADR-0052, the mock's `rpanel` for words). */
internal enum class WordsTab { DEFINE, TRANSLATE, HISTORY }

/** What the reader chose in the book for Words: a word to define or a passage to translate, and where it was. */
internal data class WordsTarget(val text: String, val page: Int?, val locator: String?)

/**
 * Words (ADR-0052; owner, 2026-11-10: "the way it is in the mock"): one panel with Define, Translate and History.
 * The dock's Words tool opens it on History; a selection's Define or Translate open it on that tab with what was
 * chosen. Everything is looked up on the phone: Wiktionary, WordNet and Wikipedia's summary for a word, ML Kit for
 * a passage, and History keeps every word and translation from this book.
 */
@Composable
internal fun WordsPanel(
    target: WordsTarget?,
    initial: WordsTab,
    navigation: BookNavigation,
    onGo: (Int) -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable(target, initial) { mutableIntStateOf(initial.ordinal) }
    val p = FlowFolio.palette
    Column(modifier.fillMaxWidth().testTag(ReaderTestTags.WORDS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reader_words),
                style = FlowFolioType.dialogTitle.copy(fontSize = 20.sp, color = p.ink)
            )
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.reader_words_offline), style = FlowFolioType.rowDetail.copy(color = p.ink3))
            onClose?.let { close ->
                FlowFolioIconButton(
                    onClick = close,
                    size = 34.dp,
                    ghost = true,
                    tooltip = stringResource(R.string.reader_close),
                    modifier = Modifier.padding(start = 6.dp)
                ) { FlowFolioGlyph(Glyph.Close, stringResource(R.string.reader_close), color = p.ink2, size = 18.dp) }
            }
        }
        FlowFolioTabs(
            listOf(
                stringResource(R.string.reader_define),
                stringResource(R.string.reader_translate),
                stringResource(R.string.reader_words_history)
            ),
            tab,
            { tab = it },
            Modifier.padding(top = 10.dp, bottom = 12.dp)
        )
        when (WordsTab.entries[tab]) {
            WordsTab.DEFINE -> DefineTab(target, onTranslate = { tab = WordsTab.TRANSLATE.ordinal })
            WordsTab.TRANSLATE -> TranslateTab(target)
            WordsTab.HISTORY -> ReaderDictionaryPanel(navigation, onGo, header = false)
        }
    }
}

/** Words over the page for a selection: a glass card at the foot of the screen, as the dock's panels sit. */
@Composable
internal fun BoxScope.WordsSheet(
    target: WordsTarget,
    initial: WordsTab,
    navigation: BookNavigation,
    onGo: (Int) -> Unit,
    onClose: () -> Unit
) {
    WordsPanel(
        target,
        initial,
        navigation,
        onGo,
        onClose,
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(12.dp)
            .widthIn(max = SHEET_WIDTH)
            .heightIn(max = SHEET_HEIGHT)
            .glass(RoundedCornerShape(24.dp))
            .padding(18.dp)
    )
}

/** Define (`.dict`): the word in Fraunces italic, its sources as chips, the meanings numbered, then the actions. */
@Composable
private fun DefineTab(target: WordsTarget?, onTranslate: () -> Unit, viewModel: DefineViewModel = hiltViewModel()) {
    val word = target?.text?.trim()?.takeIf { DefaultDictionaryRepository.isSingleWord(it) }
    if (word == null) {
        Hint(R.string.reader_words_define_hint)
        return
    }
    LaunchedEffect(target) { viewModel.define(DefineRequest(word, target.page ?: 0, target.locator)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val p = FlowFolio.palette
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            word,
            style = FlowFolioType.heroTitle.copy(fontSize = 28.sp, fontStyle = FontStyle.Italic, color = p.ink)
        )
        when (state.status) {
            DefineStatus.WORKING -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.ink)
                Text(
                    stringResource(R.string.reader_define_working),
                    style = FlowFolioType.body.copy(color = p.ink2),
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            DefineStatus.NOT_FOUND -> Text(
                stringResource(R.string.reader_define_not_found),
                style = FlowFolioType.body.copy(color = p.ink2),
                modifier = Modifier.padding(top = 12.dp)
            )

            DefineStatus.DONE -> Definitions(state, word, onTranslate)
        }
    }
}

/** A found word: its sources as chips (one at a time), the meanings numbered, then Saved, Translate and Copy. */
@Composable
private fun Definitions(state: DefineUiState, word: String, onTranslate: () -> Unit) {
    val p = FlowFolio.palette
    val context = LocalContext.current
    var source by rememberSaveable(word) { mutableStateOf<String?>(null) }
    val sources = state.entries.map { it.source }.distinct()
    val shown = source?.takeIf { it in sources } ?: sources.first()
    Row(
        Modifier.padding(vertical = 10.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        sources.forEach { s -> FlowFolioChip(s, s == shown, { source = s }) }
    }
    val entries = state.entries.filter { it.source == shown }
    entries.firstOrNull()?.partOfSpeech?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = FlowFolioType.rowDetail.copy(color = p.ink3, fontStyle = FontStyle.Italic))
    }
    entries.forEachIndexed { i, e ->
        Row(Modifier.padding(top = 6.dp)) {
            Text(
                "${i + 1}.",
                style = FlowFolioType.body.copy(color = p.ink3),
                modifier = Modifier.widthIn(min = 22.dp)
            )
            Text(
                e.definition,
                style = FlowFolioType.body.copy(fontSize = 14.sp, lineHeight = 21.sp, color = p.ink2),
                modifier = Modifier.testTag(ReaderTestTags.DEFINITION)
            )
        }
    }
    val combined = state.entries.joinToString("\n\n") { "${it.source}: ${it.definition}" }
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // A definition is kept in History the moment it is found, so the first action says so.
        FlowFolioButton(
            stringResource(R.string.reader_words_saved),
            {},
            small = true,
            style = ButtonStyle.SOFT,
            icon = Glyph.Check
        )
        FlowFolioButton(
            stringResource(R.string.reader_translate),
            onTranslate,
            small = true,
            style = ButtonStyle.GHOST,
            icon = Glyph.Translate
        )
        FlowFolioButton(
            stringResource(R.string.reader_copy),
            { copy(context, "$word: $combined") },
            small = true,
            style = ButtonStyle.GHOST,
            icon = Glyph.Copy
        )
    }
}

/** Translate: from and to, the passage, the target languages as chips, then the result on the second surface. */
@Composable
private fun TranslateTab(target: WordsTarget?, viewModel: TranslateViewModel = hiltViewModel()) {
    if (target == null) {
        Hint(R.string.reader_words_translate_hint)
        return
    }
    LaunchedEffect(target) { viewModel.start(target.text, target.page, target.locator) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        LanguageRow(state, viewModel)
        Passage(state)
        Spacer(Modifier.size(10.dp))
        TargetChips(state, viewModel)
        Column(
            Modifier
                .fillMaxWidth()
                .background(FlowFolio.palette.surface2, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp)
                .padding(bottom = 12.dp)
        ) { Result(state) }
    }
}

@Composable
private fun Hint(text: Int) {
    Text(
        stringResource(text),
        style = FlowFolioType.body.copy(color = FlowFolio.palette.ink2),
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

private val SHEET_WIDTH = 560.dp
private val SHEET_HEIGHT = 560.dp

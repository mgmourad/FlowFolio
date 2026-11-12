package com.flowfolio.feature.reader

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.magnifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.data.DefaultDictionaryRepository
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioPalette
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.PageRect
import com.flowfolio.core.model.Passage
import com.flowfolio.core.model.shareQuote
import com.flowfolio.core.pdf.PageText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Highlights by page, for the page panes (they turn with the page). */
internal val LocalHighlights = staticCompositionLocalOf<Map<Int, List<Annotation>>> { emptyMap() }

/**
 * The bar's last action: Copy for a new selection, Delete (a [warn]ing) for an existing highlight.
 *
 * [glyph] is what the bar draws; the label stays for the screen reader and the long-press tooltip, so the bar reads
 * as pictures without becoming a guessing game (owner, M5 round).
 */
internal class BarExtra(val label: Int, val glyph: Glyph, val warn: Boolean, val onClick: () -> Unit)

/** What the highlight bar can do. */
@Suppress("LongParameterList")
internal class HighlightActions(
    val current: HighlightColor?,
    val onColour: (HighlightColor) -> Unit,
    val onNote: () -> Unit,
    val onShare: () -> Unit,
    val onTranslate: () -> Unit,
    val onDefine: (() -> Unit)? = null,
    /** Speaks a note against the passage instead of typing one (owner, M5). */
    val onVoiceMark: () -> Unit = {},
    val extra: BarExtra
)

/** A note being written: for a new highlight (saved with it) or for an existing one. */
internal sealed interface NoteTarget {
    data class New(val highlight: Passage, val color: HighlightColor) : NoteTarget

    data class Existing(val annotation: Annotation) : NoteTarget
}

/**
 * Highlighting in the open book: turning presses and drags into a selection, taps on highlights into their menu, and
 * the bar's choices into saved highlights and notes. [frames] gives where the pages are on screen.
 */
@Stable
internal class Annotating(
    /** Also the way to the recorder behind voice notes (owner, M5). */
    internal val annotations: ReaderAnnotationsViewModel,
    private val texts: PageTexts,
    private val scope: CoroutineScope,
    private val labelOf: (Int) -> String?,
    /** An EPUB selection as its locator and chapter offsets (ADR-0040); null for a PDF's. */
    private val bookRange: (PageText, IntRange) -> Pair<String, IntRange>? = { _, _ -> null }
) {
    val pick = TextPick()
    var size by mutableStateOf(IntSize.Zero)
    var frames: (IntSize) -> List<PageFrames.Frame> = { emptyList() }

    /**
     * The book's highlights, each on its first page; observable, so the page and the zoomed view redraw as soon as one
     * is saved. An EPUB's are placed in the current layout (ADR-0040).
     */
    var all by mutableStateOf<List<Annotation>>(emptyList())

    /** Copies of EPUB highlights on the further pages they run onto, for drawing and tapping only. */
    var extra by mutableStateOf<List<Annotation>>(emptyList())

    /** The transient highlight marking the search hit the reader last jumped to (Phase 6 M1); null when idle. */
    var search by mutableStateOf<Annotation?>(null)

    /** Highlights by page, recomputed only when [all], [extra] or [search] change. */
    val byPage: Map<Int, List<Annotation>> by derivedStateOf {
        (all + extra + listOfNotNull(search)).groupBy { it.page }
    }

    /** Where the magnifier looks (the finger while selecting or moving a handle); unspecified hides it. */
    var lens by mutableStateOf(Offset.Unspecified)
    var note by mutableStateOf<NoteTarget?>(null)

    /** The highlight a VoiceMark is being recorded against (owner, M5); null when none is. */
    var voiceMarking by mutableStateOf<Long?>(null)

    /** A passage being translated (the translate panel is open). */
    var translating by mutableStateOf<TranslateRequest?>(null)

    /** A single word being defined (the define panel is open; M3T3). */
    var defining by mutableStateOf<DefineRequest?>(null)

    /** The book, for the reference under a shared quote. */
    var title: String = ""
    var author: String? = null
    var dragging by mutableStateOf(false)
    private var lastColour = HighlightColor.YELLOW

    /** Reads [page]'s text ahead of a selection. */
    suspend fun prefetch(page: Int) {
        texts.get(page)
    }

    fun startAt(point: Offset) {
        lens = point
        val (frame, at) = frameAt(point, frames(size)) ?: return
        dragging = true
        scope.launch {
            val text = texts.get(frame.page) ?: return@launch
            TextSelection.charAt(text, at.x, at.y)?.let { pick.start(frame.page, text, it) }
        }
    }

    fun dragTo(point: Offset) {
        lens = point
        val text = pick.text
        val hit = frameAt(point, frames(size))?.takeIf { it.first.page == pick.page }
        if (text != null && hit != null) TextSelection.charAt(text, hit.second.x, hit.second.y)?.let(pick::extendTo)
    }

    /** A tap on a highlight opens its menu (and is not a page turn). */
    fun tap(point: Offset, area: IntSize): Boolean {
        val hit = frames(area).firstNotNullOfOrNull { f ->
            byPage[f.page]?.firstOrNull { a -> a.rects.any { it.on(f).contains(point) } }
        } ?: return false
        pick.clear()
        pick.editing = hit
        return true
    }

    fun frameOfPick(): PageFrames.Frame? = frames(size).firstOrNull { it.page == pick.page }

    /** Where [rects] on [page] are on screen, as one box (for placing the bar), or null when the page is not shown. */
    fun around(page: Int, rects: List<PageRect>): Rect? {
        val frame = frames(size).firstOrNull { it.page == page } ?: return null
        return rects.map { it.on(frame) }.reduceOrNull { a, b -> a.union(b) }
    }

    /** The passage as it is shared or copied: quoted, with the book, author and page. */
    fun quote(text: String, page: Int, label: String?): String =
        shareQuote(text, title, author, label ?: (page + 1).toString())

    /** The new highlight the selection describes. */
    fun selection(): Passage? {
        val text = pick.text
        val range = pick.range
        val rects = if (text != null && range != null) TextSelection.rects(text, range) else emptyList()
        return if (text == null || range == null || rects.isEmpty()) {
            null
        } else {
            val book = bookRange(text, range)
            Passage(
                pick.page,
                labelOf(pick.page),
                book?.second ?: range,
                TextSelection.quote(text, range),
                rects,
                book?.first
            )
        }
    }

    fun save(colour: HighlightColor) {
        val new = selection() ?: return
        lastColour = colour
        annotations.highlight(new, colour)
        pick.clear()
    }

    fun noteForSelection() {
        val new = selection() ?: return
        note = NoteTarget.New(new, lastColour)
    }

    /** Keeps the selection as a highlight and opens a VoiceMark on it (owner, M5). */
    fun voiceForSelection() {
        val passage = selection() ?: return
        annotations.highlightThen(passage, lastColour) { id ->
            voiceMarking = id
            pick.clear()
        }
    }

    fun saveNote(text: String) {
        when (val target = note) {
            is NoteTarget.New -> annotations.highlight(target.highlight, target.color, text)
            is NoteTarget.Existing -> annotations.setNote(target.annotation.id, text)
            null -> Unit
        }
        note = null
        pick.clear()
    }

    fun recolour(a: Annotation, colour: HighlightColor) {
        lastColour = colour
        annotations.recolour(a.id, colour)
        pick.clear()
    }

    fun delete(a: Annotation) {
        annotations.remove(a.id)
        pick.clear()
    }
}

/** The open book's annotating state, tied to the reader's page layout. */
@Composable
internal fun rememberAnnotating(
    s: ReaderState.Ready,
    viewModel: ReaderViewModel,
    navigation: BookNavigation,
    frames: (IntSize) -> List<PageFrames.Frame>
): Annotating {
    val vm: ReaderAnnotationsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val labels by rememberUpdatedState(navigation.labels)
    val annotating = remember(vm) {
        val texts = PageTexts { page -> viewModel.pageText(page) }
        Annotating(vm, texts, scope, { page -> labels.getOrNull(page) }, viewModel::bookRange)
    }
    val all by vm.all.collectAsStateWithLifecycle()
    val book by vm.book.collectAsStateWithLifecycle()
    val search by viewModel.searchHighlight.collectAsStateWithLifecycle()
    annotating.frames = frames
    annotating.search = search
    if (s.reflowable) PlaceHighlights(annotating, all, s, viewModel, vm) else annotating.all = all
    annotating.title = book?.title ?: s.title
    annotating.author = book?.author
    // A turn, a jump or a new layout ends a selection on the old page. Once the turn has settled, the text of the pages
    // in view is read ahead, so a press and hold selects at once instead of waiting for PDFium.
    LaunchedEffect(s.unit, s.layout) {
        annotating.pick.clear()
        delay(TEXT_PREFETCH_DELAY_MS)
        pagesInView(s).forEach { annotating.prefetch(it) }
    }
    return annotating
}

/** The page box's part: presses that select, and the box's size (for page frames). */
internal fun Modifier.annotate(annotating: Annotating, enabled: Boolean): Modifier =
    onSizeChanged { annotating.size = it }
        .selectText(enabled, annotating::startAt, annotating::dragTo) {
            annotating.dragging = false
            annotating.lens = Offset.Unspecified
        }

/** Highlights by page, for [LocalHighlights]. */
internal fun Annotating.byPage(): Map<Int, List<Annotation>> = byPage

/**
 * What floats over the pages while annotating: the selection with its handles, the highlight bar, and the note
 * editor. Drawn in the palette of the page colour, like the reader's bars.
 */
@Composable
internal fun BoxScope.AnnotatingLayer(
    annotating: Annotating,
    s: ReaderState.Ready,
    navigation: BookNavigation,
    onGo: (Int) -> Unit
) {
    val pick = annotating.pick
    Lens(annotating)
    val hasOverlay = annotating.note != null || annotating.translating != null ||
        annotating.defining != null || annotating.voiceMarking != null
    if (!pick.active && !hasOverlay) return
    // The selection's handles are drawn on the page, so they follow its paper; the menus and panels around them are
    // the reader's dark chrome (ADR-0056).
    FlowFolioPalette(dark = s.tone.dark) {
        if (pick.selecting) {
            SelectionMarks(pick, annotating::frameOfPick, FlowFolio.palette.ink, onLens = { annotating.lens = it }) {
                pick.clear()
            }
        }
    }
    ReaderChromePalette {
        androidx.compose.runtime.CompositionLocalProvider(
            com.flowfolio.core.designsystem.LocalGlassStyle provides readerChromeGlass()
        ) {
            val note = annotating.note
            val voiceMark = annotating.voiceMarking
            val editing = pick.editing
            val translating = annotating.translating
            val defining = annotating.defining
            when {
                // ADR-0052: Define and Translate open the one Words panel, on their own tab.
                defining != null -> {
                    SelectionCatcher { annotating.defining = null }
                    WordsSheet(
                        WordsTarget(defining.word, defining.page, defining.locator),
                        WordsTab.DEFINE,
                        navigation,
                        { page ->
                            annotating.defining = null
                            onGo(page)
                        }
                    ) { annotating.defining = null }
                }

                translating != null -> {
                    SelectionCatcher { annotating.translating = null }
                    WordsSheet(
                        WordsTarget(translating.text, translating.page, translating.locator),
                        WordsTab.TRANSLATE,
                        navigation,
                        { page ->
                            annotating.translating = null
                            onGo(page)
                        }
                    ) { annotating.translating = null }
                }

                voiceMark != null -> VoiceMarkLayer(annotating, voiceMark)

                note != null -> NoteLayer(annotating, note)

                editing != null -> EditingBar(annotating, editing)

                !annotating.dragging -> SelectionBar(annotating)
            }
        }
    }
}

/** The bar over a tapped highlight: recolour, edit its note, share it or delete it. */
@Composable
private fun BoxScope.EditingBar(annotating: Annotating, editing: Annotation) {
    val context = LocalContext.current
    val around = annotating.around(editing.page, editing.rects) ?: return
    val singleWord = DefaultDictionaryRepository.isSingleWord(editing.text)
    Box(Modifier.matchParentSize()) {
        SelectionCatcher { annotating.pick.clear() }
        HighlightBar(
            around,
            HighlightActions(
                current = editing.color,
                onColour = { annotating.recolour(editing, it) },
                onNote = { annotating.note = NoteTarget.Existing(editing) },
                onShare = { share(context, annotating.quote(editing.text, editing.page, editing.label)) },
                onTranslate = {
                    annotating.translating = TranslateRequest(editing.text, editing.page, editing.locator)
                },
                onDefine = if (singleWord) {
                    { annotating.defining = DefineRequest(editing.text, editing.page, editing.locator) }
                } else {
                    null
                },
                onVoiceMark = { annotating.voiceMarking = editing.id },
                extra = BarExtra(R.string.reader_delete, Glyph.Trash, warn = true) { annotating.delete(editing) }
            )
        )
    }
}

/** The bar over a new selection: a colour saves it as a highlight; or add a note, share or copy it. */
@Composable
private fun BoxScope.SelectionBar(annotating: Annotating) {
    val context = LocalContext.current
    val selection = annotating.selection() ?: return
    val around = annotating.around(selection.page, selection.rects) ?: return
    val quote = annotating.quote(selection.text, selection.page, selection.label)
    val singleWord = DefaultDictionaryRepository.isSingleWord(selection.text)
    HighlightBar(
        around,
        HighlightActions(
            current = null,
            onColour = annotating::save,
            onNote = annotating::noteForSelection,
            onVoiceMark = annotating::voiceForSelection,
            onShare = { share(context, quote) },
            onTranslate = {
                annotating.translating =
                    TranslateRequest(selection.text, selection.page, selection.locator)
            },
            onDefine = if (singleWord) {
                { annotating.defining = DefineRequest(selection.text, selection.page, selection.locator) }
            } else {
                null
            },
            extra = BarExtra(R.string.reader_copy, Glyph.Copy, warn = false) {
                copy(context, quote)
                annotating.pick.clear()
            }
        )
    )
}

/** Writing a note: the editor over a catcher that cancels it. */
@Composable
private fun BoxScope.NoteLayer(annotating: Annotating, target: NoteTarget) {
    val (quote, initial) = when (target) {
        is NoteTarget.New -> target.highlight.text to null
        is NoteTarget.Existing -> target.annotation.text to target.annotation.note
    }
    SelectionCatcher { annotating.note = null }
    NoteEditor(quote, initial, annotating::saveNote) { annotating.note = null }
}

/**
 * The magnifier over the finger while selecting (owner, 2026-10-18: precision), like the system's text magnifier:
 * the text under the finger, enlarged, just above it, so the finger never hides what is being selected.
 */
@Composable
private fun BoxScope.Lens(annotating: Annotating) {
    val lift = with(LocalDensity.current) { LENS_LIFT.toPx() }
    Spacer(
        Modifier.matchParentSize().magnifier(
            sourceCenter = { annotating.lens },
            magnifierCenter = {
                annotating.lens.takeIf { it.isSpecified }?.let { it - Offset(0f, lift) }
                    ?: Offset.Unspecified
            },
            zoom = LENS_ZOOM,
            size = DpSize(LENS_WIDTH, LENS_HEIGHT),
            cornerRadius = LENS_HEIGHT / 2
        )
    )
}

private const val LENS_ZOOM = 1.6f

/** After a turn: long enough for the pages themselves to render first (PDFium does one thing at a time). */
private const val TEXT_PREFETCH_DELAY_MS = 700L
private val LENS_WIDTH = 140.dp
private val LENS_HEIGHT = 56.dp
private val LENS_LIFT = 72.dp

/** A tap anywhere outside the bar or editor closes it. */
@Composable
internal fun BoxScope.SelectionCatcher(onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { tap() } })
}

/** Unions of rectangles for the bar's placement. */
private fun Rect.union(other: Rect) =
    Rect(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

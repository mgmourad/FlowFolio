package com.flowfolio.feature.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioPalette
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.PageRect
import com.flowfolio.core.model.Passage
import com.flowfolio.core.model.shareQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Highlights by page, for the page panes (they turn with the page). */
internal val LocalHighlights = staticCompositionLocalOf<Map<Int, List<Annotation>>> { emptyMap() }

/** The bar's last action: Copy for a new selection, Delete (a [warn]ing) for an existing highlight. */
internal class BarExtra(val label: Int, val warn: Boolean, val onClick: () -> Unit)

/** What the highlight bar can do. */
internal class HighlightActions(
    val current: HighlightColor?,
    val onColour: (HighlightColor) -> Unit,
    val onNote: () -> Unit,
    val onShare: () -> Unit,
    val onTranslate: () -> Unit,
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
    private val vm: ReaderAnnotationsViewModel,
    private val texts: PageTexts,
    private val scope: CoroutineScope,
    private val labelOf: (Int) -> String?
) {
    val pick = TextPick()
    var size by mutableStateOf(IntSize.Zero)
    var frames: (IntSize) -> List<PageFrames.Frame> = { emptyList() }
    var all: List<Annotation> = emptyList()
    var note by mutableStateOf<NoteTarget?>(null)

    /** A passage being translated (the translate panel is open). */
    var translating by mutableStateOf<String?>(null)

    /** The book, for the reference under a shared quote. */
    var title: String = ""
    var author: String? = null
    var dragging by mutableStateOf(false)
    private var lastColour = HighlightColor.YELLOW

    fun startAt(point: Offset) {
        val (frame, at) = frameAt(point, frames(size)) ?: return
        dragging = true
        scope.launch {
            val text = texts.get(frame.page) ?: return@launch
            TextSelection.charAt(text, at.x, at.y)?.let { pick.start(frame.page, text, it) }
        }
    }

    fun dragTo(point: Offset) {
        val text = pick.text
        val hit = frameAt(point, frames(size))?.takeIf { it.first.page == pick.page }
        if (text != null && hit != null) TextSelection.charAt(text, hit.second.x, hit.second.y)?.let(pick::extendTo)
    }

    /** A tap on a highlight opens its menu (and is not a page turn). */
    fun tap(point: Offset, area: IntSize): Boolean {
        val hit = frames(area).firstNotNullOfOrNull { f ->
            all.firstOrNull { a -> a.page == f.page && a.rects.any { it.on(f).contains(point) } }
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
            Passage(pick.page, labelOf(pick.page), range, TextSelection.quote(text, range), rects)
        }
    }

    fun save(colour: HighlightColor) {
        val new = selection() ?: return
        lastColour = colour
        vm.highlight(new, colour)
        pick.clear()
    }

    fun noteForSelection() {
        val new = selection() ?: return
        note = NoteTarget.New(new, lastColour)
    }

    fun saveNote(text: String) {
        when (val target = note) {
            is NoteTarget.New -> vm.highlight(target.highlight, target.color, text)
            is NoteTarget.Existing -> vm.setNote(target.annotation.id, text)
            null -> Unit
        }
        note = null
        pick.clear()
    }

    fun recolour(a: Annotation, colour: HighlightColor) {
        lastColour = colour
        vm.recolour(a.id, colour)
        pick.clear()
    }

    fun delete(a: Annotation) {
        vm.remove(a.id)
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
        val texts = PageTexts { page -> runCatching { viewModel.document?.pageText(page) }.getOrNull() }
        Annotating(vm, texts, scope) { page -> labels.getOrNull(page) }
    }
    val all by vm.all.collectAsStateWithLifecycle()
    val book by vm.book.collectAsStateWithLifecycle()
    annotating.frames = frames
    annotating.all = all
    annotating.title = book?.title ?: s.title
    annotating.author = book?.author
    // A turn, a jump or a new layout ends a selection on the old page.
    LaunchedEffect(s.unit, s.layout) { annotating.pick.clear() }
    return annotating
}

/** The page box's part: presses that select, and the box's size (for page frames). */
internal fun Modifier.annotate(annotating: Annotating, enabled: Boolean): Modifier =
    onSizeChanged { annotating.size = it }
        .selectText(enabled, annotating::startAt, annotating::dragTo) { annotating.dragging = false }

/** Highlights by page, for [LocalHighlights]. */
internal fun Annotating.byPage(): Map<Int, List<Annotation>> = all.groupBy { it.page }

/**
 * What floats over the pages while annotating: the selection with its handles, the highlight bar, and the note
 * editor. Drawn in the palette of the page colour, like the reader's bars.
 */
@Composable
internal fun BoxScope.AnnotatingLayer(annotating: Annotating, s: ReaderState.Ready) {
    val pick = annotating.pick
    if (!pick.active && annotating.note == null && annotating.translating == null) return
    FlowFolioPalette(dark = s.tone.dark) {
        if (pick.range != null) {
            SelectionMarks(pick, annotating.frameOfPick(), FlowFolio.palette.item) { pick.clear() }
        }
        val note = annotating.note
        val editing = pick.editing
        val translating = annotating.translating
        when {
            translating != null -> {
                SelectionCatcher { annotating.translating = null }
                TranslateOverlay(translating) { annotating.translating = null }
            }

            note != null -> NoteLayer(annotating, note)

            editing != null -> EditingBar(annotating, editing)

            !annotating.dragging -> SelectionBar(annotating)
        }
    }
}

/** The bar over a tapped highlight: recolour, edit its note, share it or delete it. */
@Composable
private fun BoxScope.EditingBar(annotating: Annotating, editing: Annotation) {
    val context = LocalContext.current
    val around = annotating.around(editing.page, editing.rects) ?: return
    Box(Modifier.matchParentSize()) {
        SelectionCatcher { annotating.pick.clear() }
        HighlightBar(
            around,
            HighlightActions(
                current = editing.color,
                onColour = { annotating.recolour(editing, it) },
                onNote = { annotating.note = NoteTarget.Existing(editing) },
                onShare = { share(context, annotating.quote(editing.text, editing.page, editing.label)) },
                onTranslate = { annotating.translating = editing.text },
                extra = BarExtra(R.string.reader_delete, warn = true) { annotating.delete(editing) }
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
    HighlightBar(
        around,
        HighlightActions(
            current = null,
            onColour = annotating::save,
            onNote = annotating::noteForSelection,
            onShare = { share(context, quote) },
            onTranslate = { annotating.translating = selection.text },
            extra = BarExtra(R.string.reader_copy, warn = false) {
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

/** A tap anywhere outside the bar or editor closes it. */
@Composable
private fun BoxScope.SelectionCatcher(onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { tap() } })
}

/** Unions of rectangles for the bar's placement. */
private fun Rect.union(other: Rect) =
    Rect(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

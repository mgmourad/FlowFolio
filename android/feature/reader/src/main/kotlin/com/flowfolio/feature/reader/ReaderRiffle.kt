package com.flowfolio.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.designsystem.coverColors
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.BookmarkColor
import com.flowfolio.core.model.PixelSize
import com.flowfolio.core.model.UserSettings
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import com.flowfolio.feature.reader.riffle.BoardColors
import com.flowfolio.feature.reader.riffle.BookGeometry
import com.flowfolio.feature.reader.riffle.ChromePause
import com.flowfolio.feature.reader.riffle.RiffleDock
import com.flowfolio.feature.reader.riffle.RiffleEvents
import com.flowfolio.feature.reader.riffle.RiffleFeedback
import com.flowfolio.feature.reader.riffle.RiffleFlag
import com.flowfolio.feature.reader.riffle.RiffleHaptic
import com.flowfolio.feature.reader.riffle.RiffleHaptics
import com.flowfolio.feature.reader.riffle.RiffleScrubberState
import com.flowfolio.feature.reader.riffle.RiffleSounds
import com.flowfolio.feature.reader.riffle.ScrubSpeed
import com.flowfolio.feature.reader.riffle.Section
import com.flowfolio.feature.reader.riffle.SectionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The open book as the riffle scrubber sees it (ADR-0022): the reader's units, its contents and printed numbers. */
internal class ReaderBook(
    private val s: ReaderState.Ready,
    navigation: BookNavigation,
    override val rightToLeft: Boolean
) : BookGeometry {
    private val labels = navigation.labels
    private val spread = s.layout == ReaderLayout.SPREAD

    override val pageCount = s.pageCount
    override val unitCount = s.unitCount

    override fun pagesOf(unit: Int): IntRange = if (spread) {
        val pages = Spreads.pagesOf(unit, s.pageCount, s.coverAlone).ifEmpty { listOf(unit) }
        pages.first()..pages.last()
    } else {
        unit..unit
    }

    override fun unitOf(page: Int): Int = if (spread) Spreads.unitOf(page, s.coverAlone) else page

    /**
     * The contents' top level: entries the book numbers ("Chapter 9 Linear Momentum", "9 Linear Momentum") are chapters
     * with that number; glossaries and indexes go by their titles; anything else (a preface) is shown by its title.
     */
    override val sections: List<Section> = navigation.outline.filter { it.depth == 0 }.sortedBy { it.pageIndex }
        .map { entry ->
            val kind = kindOf(entry.title)
            val numbered = CHAPTER_NUMBER.find(entry.title.trim())
            if (kind == SectionKind.CHAPTER && numbered != null) {
                val title = numbered.groupValues[2].ifBlank { entry.title }
                Section(entry.pageIndex, title, kind, numbered.groupValues[1].toInt())
            } else {
                Section(entry.pageIndex, entry.title, kind)
            }
        }

    override fun label(page: Int): String = PageLookup.labelOf(page, labels)

    override fun pageOfLabel(label: String): Int? = PageLookup.resolve(label, labels, pageCount)

    /** The page the reader goes to for [unit]: its first page, or the spread's anchor. */
    fun anchorOf(unit: Int): Int = if (spread) Spreads.anchorOf(unit, s.pageCount, s.coverAlone) else unit

    private fun kindOf(title: String): SectionKind {
        val t = title.lowercase()
        return when {
            "glossary" in t -> SectionKind.GLOSSARY
            t.startsWith("index") -> SectionKind.INDEX
            else -> SectionKind.CHAPTER
        }
    }

    private companion object {
        /** A leading chapter number, with or without the word: "Chapter 9 Title", "9. Title", "9 Title". */
        val CHAPTER_NUMBER = Regex("""^(?:chapter\s+)?(\d{1,3})\b[\s.:·–-]*(.*)$""", RegexOption.IGNORE_CASE)
    }
}

/**
 * The reader's bottom bar: the riffle scrubber on glass (ADR-0022). It follows the reader's unit, shows the book's
 * bookmarks as flags in their own colours (the default red, the ribbon's colour, is drawn lighter on the strip, owner
 * decision 3), and turns releases, flag taps, nudges and Go to page into landings ([Riffling.land]).
 */
@Composable
internal fun RiffleBar(
    s: ReaderState.Ready,
    riffling: Riffling,
    marks: List<Bookmark>,
    viewModel: ReaderViewModel,
    chrome: ChromeClock
) {
    val book = riffling.book
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val state = remember(book) { RiffleScrubberState(book, s.unit) }
    LaunchedEffect(state, s.unit) { state.currentUnit = s.unit }
    val flagColors = BookmarkColor.entries.associateWith { flagColor(it) }
    LaunchedEffect(state, marks, flagColors) {
        state.flags = marks.map { RiffleFlag(it.id, it.page, flagColors.getValue(it.color).toArgb()) }
    }
    val thumbnails = viewModel.thumbnails
    val density = LocalDensity.current
    LaunchedEffect(thumbnails, s.tone, s.pageAspect) {
        val height = with(density) { LOUPE_PAGE_HEIGHT.dp.roundToPx() }
        thumbnails?.use(PixelSize((height * s.pageAspect).toInt().coerceAtLeast(1), height), s.tone)
    }
    val cover by viewModel.cover.collectAsStateWithLifecycle()
    val boards = boardColors(cover)
    val events = rememberReaderEvents(state, settings.scrubHaptics, chrome, riffling.sounds, riffling.land)
    // The glass is drawn behind the dock rather than on it: glass clips, and the loupe rises above the dock.
    Box(Modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 8.dp).fillMaxWidth()) {
        Box(Modifier.matchParentSize().glass(RoundedCornerShape(22.dp)))
        RiffleDock(state, boards, Modifier.fillMaxWidth(), events, thumbnails, chrome::remaining)
    }
}

/** The reader's book for the scrubber, rebuilt when the layout, pairing, contents or direction change. */
@Composable
internal fun rememberReaderBook(s: ReaderState.Ready, navigation: BookNavigation): ReaderBook {
    val rtl = LocalRightToLeft.current
    return remember(s.pageCount, s.unitCount, s.layout, s.coverAlone, navigation, rtl) {
        ReaderBook(s, navigation, rtl)
    }
}

/** What the reader's chrome clock takes from the bar: interactions restart it, holds pause it (spec 3.4). */
internal interface ChromeControl {
    fun interacted()

    fun pause(reason: ChromePause, held: Boolean)
}

@Composable
private fun rememberReaderEvents(
    state: RiffleScrubberState,
    haptics: Boolean,
    chrome: ChromeControl,
    sounds: ReaderSounds,
    onLand: (Int) -> Unit
): RiffleEvents {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The reader's state moves on; the events are kept, so they call the latest landing.
    val landing by rememberUpdatedState(onLand)
    return remember(state, haptics, chrome, sounds) {
        object : RiffleEvents {
            override fun onRelease(unit: Int) {
                sounds.stopRiffle()
                land(unit)
            }

            override fun onGoTo(unit: Int) = land(unit)

            override fun onFlag(flag: RiffleFlag) {
                val unit = state.book.unitOf(flag.page)
                if (unit == state.currentUnit) return
                sounds.turn(forward = unit > state.currentUnit)
                state.travelTo(unit)
                landing(unit)
            }

            override fun onFeedback(feedback: RiffleFeedback) {
                if (haptics) feedback.haptic?.let { RiffleHaptics.play(view, it, scope) }
                sounds.riffle(feedback)
            }

            override fun onSpeed(speed: ScrubSpeed) {
                if (haptics) RiffleHaptics.play(view, RiffleHaptic.SPEED, scope)
            }

            override fun onInteraction() = chrome.interacted()

            override fun onPause(reason: ChromePause, held: Boolean) = chrome.pause(reason, held)

            private fun land(unit: Int) {
                if (unit == state.currentUnit) return
                sounds.turn(forward = unit > state.currentUnit)
                landing(unit)
            }
        }
    }
}

/** The boards in the cover's colours (spec 4.1), else the theme's accent. */
@Composable
private fun boardColors(cover: Bitmap?): BoardColors {
    val accent = FlowFolio.palette.glyph
    val fromCover by produceState<BoardColors?>(null, cover) {
        value = cover?.let { bmp -> withContext(Dispatchers.Default) { coverColors(bmp) } }
            ?.let { BoardColors(it.start, it.end) }
    }
    return fromCover ?: BoardColors(accent, lerp(accent, Color.Black, DARK_SHADE))
}

/** A bookmark's flag colour; the default red is the ribbon's, so the strip draws it lighter (owner decision 3). */
@Composable
private fun flagColor(color: BookmarkColor): Color =
    if (color == BookmarkColor.SUB_ITEM) lerp(color.color, Color.White, LIGHTER) else color.color

private const val LOUPE_PAGE_HEIGHT = 162
private const val DARK_SHADE = 0.45f
private const val LIGHTER = 0.45f

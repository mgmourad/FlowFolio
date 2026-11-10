package com.flowfolio.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion

/**
 * The Notebook's sections (ADR-0052): everything marked in every book, one tab instead of three. Highlights holds the
 * notes and VoiceMarks with them, as the Annotations page did.
 */
enum class NotebookSection(val glyph: Glyph) {
    HIGHLIGHTS(Glyph.Highlight),
    BOOKMARKS(Glyph.Bookmark),
    WORDS(Glyph.Translate)
}

/**
 * Inside the Notebook, a page's [ScreenHeader] is the Notebook's: its title and line, then the section chips. The
 * pages that used to be tabs of their own (Bookmarks, Annotations, Dictionary) therefore keep their search, select
 * mode and lists unchanged while wearing one header.
 */
internal class NotebookHeader(val section: NotebookSection, val onSection: (NotebookSection) -> Unit)

internal val LocalNotebookHeader = staticCompositionLocalOf<NotebookHeader?> { null }

/**
 * The Notebook tab: the chosen [section]'s page, swapped with a quick cross-fade when another chip is picked.
 * [content] draws a section's page (the app supplies them, with their navigation).
 */
@Composable
fun NotebookScreen(
    section: NotebookSection,
    onSection: (NotebookSection) -> Unit,
    content: @Composable (NotebookSection) -> Unit
) {
    AnimatedContent(
        targetState = section,
        transitionSpec = {
            fadeIn(tween(Motion.MEDIUM_MS, delayMillis = FADE_OUT_MS, easing = Motion.Emphasized)) togetherWith
                fadeOut(tween(FADE_OUT_MS, easing = Motion.EmphasizedExit))
        },
        label = "notebookSection"
    ) { shown ->
        CompositionLocalProvider(LocalNotebookHeader provides NotebookHeader(shown, onSection)) { content(shown) }
    }
}

/** The section chips under the Notebook's title. */
@Composable
internal fun NotebookChips(header: NotebookHeader, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NotebookSection.entries.forEach { s ->
            FlowFolioChip(
                text = stringResource(s.label),
                selected = s == header.section,
                onClick = { header.onSection(s) },
                glyph = s.glyph,
                modifier = Modifier.testTag(LibraryTestTags.notebookSection(s))
            )
        }
    }
}

private val NotebookSection.label: Int
    get() = when (this) {
        NotebookSection.HIGHLIGHTS -> R.string.notebook_highlights
        NotebookSection.BOOKMARKS -> R.string.notebook_bookmarks
        NotebookSection.WORDS -> R.string.notebook_words
    }

private const val FADE_OUT_MS = 90

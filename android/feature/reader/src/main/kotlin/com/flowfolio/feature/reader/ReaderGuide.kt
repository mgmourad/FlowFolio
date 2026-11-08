package com.flowfolio.feature.reader

import androidx.annotation.StringRes
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.model.UserSettings

/**
 * A first-use guide on one of the reader's controls (owner, 2026-11-07). Shown once as a centred window the first time
 * the control is used, then never again. [id] is what is remembered, so it must not change once a build has shipped
 * with it.
 */
internal enum class ReaderGuide(val id: String, val glyph: Glyph, @StringRes val title: Int, @StringRes val body: Int) {
    Contents(
        "reader_guide_contents",
        Glyph.Contents,
        R.string.reader_guide_contents_title,
        R.string.reader_guide_contents_body
    ),
    GoTo("reader_guide_go_to", Glyph.GoTo, R.string.reader_guide_go_to_title, R.string.reader_guide_go_to_body),
    Settings(
        "reader_guide_settings",
        Glyph.Settings,
        R.string.reader_guide_settings_title,
        R.string.reader_guide_settings_body
    ),
    Mark("reader_guide_mark", Glyph.Bookmark, R.string.reader_guide_mark_title, R.string.reader_guide_mark_body),
    Annotations(
        "reader_guide_annotations",
        Glyph.Highlight,
        R.string.reader_guide_annotations_title,
        R.string.reader_guide_annotations_body
    ),
    Dictionary(
        "reader_guide_dictionary",
        Glyph.Translate,
        R.string.reader_guide_dictionary_title,
        R.string.reader_guide_dictionary_body
    );

    /** Whether this one still has something to say: tips are on and it has not been seen. */
    fun unseen(settings: UserSettings): Boolean = settings.tipsEnabled && id !in settings.seenGuides

    companion object {
        /** The guide a panel's button explains, or null for panels without one. */
        fun of(panel: Panel): ReaderGuide? = when (panel) {
            Panel.CONTENTS -> Contents
            Panel.GO_TO -> GoTo
            Panel.SETTINGS -> Settings
            Panel.MARK -> Mark
            Panel.ANNOTATIONS -> Annotations
            Panel.DICTIONARY -> Dictionary
            Panel.NONE -> null
        }
    }
}

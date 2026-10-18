package com.flowfolio.feature.reader

import com.flowfolio.core.model.BookmarkColor

/** What is open on top of the reader. */
internal enum class Panel { NONE, CONTENTS, GO_TO, SETTINGS, MARK }

/** What the reader's controls can ask for. */
internal class ChromeActions(
    val onBack: () -> Unit,
    /** Contents, Go to page, Aa and the ribbon button: show or hide their panel. */
    val onPanel: (Panel) -> Unit,
    /** The scrubber was released on this page. */
    val onScrub: (Int) -> Unit,
    /** Any use of the controls; keeps them on screen a little longer. */
    val onInteract: () -> Unit
)

/**
 * The ribbon on the page in view ([color], null when there is none) and what the bookmark button can do with it:
 * put one on (or recolour it) and take it off.
 */
internal class ChromeMark(
    val color: BookmarkColor?,
    val onMark: (BookmarkColor) -> Unit = {},
    val onUnmark: () -> Unit = {}
)

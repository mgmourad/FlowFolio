package com.flowfolio.feature.reader

import com.flowfolio.core.model.BookmarkColor

/** What the reader's controls can ask for. */
internal class ChromeActions(
    val onBack: () -> Unit,
    val onContents: () -> Unit,
    val onGoTo: () -> Unit,
    val onSettings: () -> Unit,
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

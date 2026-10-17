package com.flowfolio.feature.reader

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

package com.flowfolio.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Space the app shell's floating chrome takes over a screen: the glass tab bar at the bottom (folded) or the rail on
 * the left (unfolded). Screens pad their scrolling content and floating buttons by it so nothing ends up under the
 * chrome, while content can still scroll beneath the glass and show through its blur.
 */
val LocalChromePadding = staticCompositionLocalOf { PaddingValues() }

/** Keeps a screen clear of the rail when the shell shows one (unfolded); no-op on the bottom-bar layout. */
@Composable
fun Modifier.chromeStart(): Modifier =
    padding(start = LocalChromePadding.current.calculateStartPadding(LocalLayoutDirection.current))

/** Keeps the end of a scrolling screen clear of the floating tab bar (folded); no-op on the rail layout. */
@Composable
fun Modifier.chromeBottom(): Modifier = padding(bottom = LocalChromePadding.current.calculateBottomPadding())

/**
 * A page under a tab (a book's details, a shelf) fills the screen clear of the bar or rail, which stays on it (owner,
 * M3T1). The tab screens keep clear of it themselves, their lists scrolling under the glass.
 */
@Composable
fun Modifier.clearOfChrome(): Modifier = fillMaxSize().chromeStart().chromeBottom()

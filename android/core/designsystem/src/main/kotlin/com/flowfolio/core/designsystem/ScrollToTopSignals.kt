package com.flowfolio.core.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Taps on the tab bar and the rail, counted per destination (owner, M4c). Tapping a destination shows its page from
 * the top, whether the reader was already on it or is arriving from somewhere else, so a tab is always a way back to
 * the start of a page rather than wherever it was left.
 *
 * The count is what screens watch: it changes on every tap, including a tap on the page already showing, which a
 * plain "is this route selected" flag could not tell apart.
 */
@Stable
class ScrollToTopSignals {
    private val taps = mutableStateMapOf<String, Int>()

    /** Records a tap on [route]'s tab. */
    fun tapped(route: String) {
        taps[route] = (taps[route] ?: 0) + 1
    }

    /** How many times [route]'s tab has been tapped this run; read from a composable to follow it. */
    fun taps(route: String): Int = taps[route] ?: 0
}

val LocalScrollToTop = staticCompositionLocalOf { ScrollToTopSignals() }

/**
 * Sends [state] back to the top whenever [route]'s tab is tapped (owner, M4c). A null [route] is a page that
 * is not a tab of its own, which never scrolls itself.
 */
@Composable
fun ScrollToTopOnTab(route: String?, state: LazyListState) {
    val taps = if (route == null) 0 else LocalScrollToTop.current.taps(route)
    LaunchedEffect(taps) {
        if (taps > 0) state.animateScrollToItem(0)
    }
}

/** The grid's version of [ScrollToTopOnTab]. */
@Composable
fun ScrollToTopOnTab(route: String?, state: LazyGridState) {
    val taps = if (route == null) 0 else LocalScrollToTop.current.taps(route)
    LaunchedEffect(taps) {
        if (taps > 0) state.animateScrollToItem(0)
    }
}

/** The plain scrolling column's version of [ScrollToTopOnTab] (Settings). */
@Composable
fun ScrollToTopOnTab(route: String?, state: ScrollState) {
    val taps = if (route == null) 0 else LocalScrollToTop.current.taps(route)
    LaunchedEffect(taps) {
        if (taps > 0) state.animateScrollTo(0)
    }
}

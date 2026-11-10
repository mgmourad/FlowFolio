package com.flowfolio.feature.reader

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much of the screen's foot the dock takes, navigation bar included (ADR-0052): the chrome measures it, the panels
 * sit [PANEL_GAP][DockPanel] above it, so every panel keeps the same distance from the bottom bar.
 */
@Stable
internal class DockSpace {
    var height by mutableStateOf(0.dp)
}

internal val LocalDockSpace = staticCompositionLocalOf { DockSpace() }

/** The dock's width unfolded, and every panel's: the mock's 560 dp. */
internal val READER_DOCK_WIDTH: Dp = 560.dp

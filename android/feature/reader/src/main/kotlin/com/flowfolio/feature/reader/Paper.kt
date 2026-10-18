package com.flowfolio.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
/** The page colour: white as printed, or the night paper when Night pages is showing. */
internal val LocalPaper = staticCompositionLocalOf { Color.White }

internal val PAPER: Color
    @Composable @ReadOnlyComposable
    get() = LocalPaper.current

/**
 * Night paper: the app's dark background (#1C0A4D, ADR-0014) moved to the nearest RGB_565 colour, because pages are
 * 16-bit images and the page must match the screen around it exactly. Same value as the native night pass.
 */
internal val NIGHT_PAPER = Color(0xFF18084A)

internal val isNightPaper: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalPaper.current == NIGHT_PAPER

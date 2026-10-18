// Paper colours are the mock's values snapped to RGB_565, each documented beside it (ADR-0015).
@file:Suppress("MagicNumber")

package com.flowfolio.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.flowfolio.core.model.PaperStyle
import com.flowfolio.core.pdf.PageTint

/**
 * How the pages look: the [paper] colour (the page and the screen around it) and how text and line art are
 * recoloured ([tint]; images never are). Paper colours are exact in a 16-bit (RGB_565) page image, so the page and
 * the screen around it match: each channel is a value RGB_565 expands to (for example 0x18084A, 0xF7E3C6).
 */
enum class PageTone(val paper: Color, val ink: Color?, val dark: Boolean) {
    /** As printed: white paper, no recolouring. */
    PRINTED(Color.White, null, false),

    /** The mock's warm paper (#FFFAF0 -> #FFFBEF) with brown-black ink. */
    PAPER(Color(0xFFFFFBEF), Color(0xFF2C241C), false),

    /** The mock's sepia (#F4E4C4 -> #F7E3C6) with dark brown ink. */
    SEPIA(Color(0xFFF7E3C6), Color(0xFF4A3520), false),

    /** The app's dark background (#1C0A4D -> #18084A) with soft lavender ink. */
    NIGHT(Color(0xFF18084A), Color(0xFFE9E4F5), true),

    /** Near black (#050307 -> #080408) with grey-lavender ink. */
    BLACK(Color(0xFF080408), Color(0xFFC9C4D6), true);

    /** The recolouring for the renderer, or null for pages as printed. */
    val tint: PageTint? get() = ink?.let { PageTint(paper.toArgb(), it.toArgb(), invert = dark) }
}

/** The tone a paper style gives: Auto follows the app (as printed in light mode, Night in dark mode). */
fun PaperStyle.tone(appDark: Boolean): PageTone = when (this) {
    PaperStyle.AUTO -> if (appDark) PageTone.NIGHT else PageTone.PRINTED
    PaperStyle.PAPER -> PageTone.PAPER
    PaperStyle.SEPIA -> PageTone.SEPIA
    PaperStyle.NIGHT -> PageTone.NIGHT
    PaperStyle.BLACK -> PageTone.BLACK
}

/** The tone of the book being read. */
internal val LocalTone = staticCompositionLocalOf { PageTone.PRINTED }

internal val PAPER: Color
    @Composable @ReadOnlyComposable
    get() = LocalTone.current.paper

/** Dark paper (Night, Black): shading and highlights adapt. */
internal val isDarkPaper: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalTone.current.dark

/**
 * Status bar icons readable on the paper: dark icons on light papers (as printed, Paper, Sepia), light on dark ones.
 * The app's own setting comes back when the reader closes.
 */
@Composable
internal fun StatusBarForPaper(tone: PageTone) {
    val view = LocalView.current
    DisposableEffect(tone) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !tone.dark
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
}

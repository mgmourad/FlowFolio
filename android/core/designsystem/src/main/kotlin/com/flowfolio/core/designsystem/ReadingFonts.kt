package com.flowfolio.core.designsystem

import androidx.annotation.FontRes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.flowfolio.core.model.ReadingFont

/**
 * The font family resource for this reading font (ADR-0027): regular and bold, upright and italic. The book's own font
 * falls back to Literata.
 */
@get:FontRes
val ReadingFont.fontRes: Int
    get() = when (this) {
        ReadingFont.BOOK, ReadingFont.LITERATA -> R.font.font_literata
        ReadingFont.CHARTER -> R.font.font_charter
        ReadingFont.ATKINSON_HYPERLEGIBLE -> R.font.font_atkinson
        ReadingFont.EB_GARAMOND -> R.font.font_ebgaramond
        ReadingFont.SOURCE_SANS -> R.font.font_sourcesans
        ReadingFont.LEXEND -> R.font.font_lexend
        ReadingFont.ANDIKA -> R.font.font_andika
    }

/** The font's own name, shown in the font picker; the book's own font is labelled by the picker's caller. */
val ReadingFont.displayName: String
    get() = when (this) {
        ReadingFont.BOOK -> ""
        ReadingFont.LITERATA -> "Literata"
        ReadingFont.CHARTER -> "Charter"
        ReadingFont.ATKINSON_HYPERLEGIBLE -> "Atkinson Hyperlegible"
        ReadingFont.EB_GARAMOND -> "EB Garamond"
        ReadingFont.SOURCE_SANS -> "Source Sans"
        ReadingFont.LEXEND -> "Lexend"
        ReadingFont.ANDIKA -> "Andika"
    }

/** The font for Compose text (the picker's samples). */
val ReadingFont.fontFamily: FontFamily
    get() = FontFamily(
        androidx.compose.ui.text.font.Font(fontRes, FontWeight.Normal, FontStyle.Normal)
    )

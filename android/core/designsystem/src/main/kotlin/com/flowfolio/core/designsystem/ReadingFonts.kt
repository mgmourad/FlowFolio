package com.flowfolio.core.designsystem

import androidx.annotation.FontRes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.flowfolio.core.model.ReadingFont

/** The font family resource for this reading font (ADR-0027): regular and bold, upright and italic. */
@get:FontRes
val ReadingFont.fontRes: Int
    get() = when (this) {
        ReadingFont.LITERATA -> R.font.font_literata
        ReadingFont.CHARTER -> R.font.font_charter
        ReadingFont.ATKINSON_HYPERLEGIBLE -> R.font.font_atkinson
        ReadingFont.EB_GARAMOND -> R.font.font_ebgaramond
        ReadingFont.SOURCE_SANS -> R.font.font_sourcesans
        ReadingFont.LEXEND -> R.font.font_lexend
        ReadingFont.ANDIKA -> R.font.font_andika
    }

/** The font's own name, shown in its own face in the font picker. */
val ReadingFont.displayName: String
    get() = when (this) {
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

package com.flowfolio.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * The mock's type (ADR-0015): Fraunces for display (titles, book titles, reading text) with its soft, slightly wonky
 * axes, and Instrument Sans for UI. Both are variable fonts (SIL OFL); each weight below is an instance of one file.
 */

private const val DISPLAY_OPSZ = 96f
private const val TEXT_OPSZ = 16f
private const val SOFT_DISPLAY = 100f
private const val SOFT_TEXT = 50f
private const val TITLE_WEIGHT = 650

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun fraunces(weight: Int, italic: Boolean, opsz: Float, soft: Float, wonk: Float) = Font(
    resId = if (italic) R.font.fraunces_italic else R.font.fraunces,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz),
        FontVariation.Setting("SOFT", soft),
        FontVariation.Setting("WONK", wonk)
    )
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun instrument(weight: Int, italic: Boolean = false) = Font(
    resId = if (italic) R.font.instrument_sans_italic else R.font.instrument_sans,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Fraunces for display: `"SOFT" 100, "WONK" 1, "opsz" 96` as in the mock's `.display`. */
val DisplayFamily = FontFamily(
    listOf(400, 600, 650, 680, 700, 720).map { fraunces(it, italic = false, DISPLAY_OPSZ, SOFT_DISPLAY, 1f) } +
        listOf(380, 400).map { fraunces(it, italic = true, DISPLAY_OPSZ, SOFT_DISPLAY, 1f) }
)

/** Fraunces for long reading text: `"opsz" 16, "SOFT" 50` as in the mock's `.prose`. */
val ProseFamily = FontFamily(
    fraunces(400, italic = false, TEXT_OPSZ, SOFT_TEXT, 0f),
    fraunces(700, italic = false, TEXT_OPSZ, SOFT_TEXT, 0f),
    fraunces(400, italic = true, TEXT_OPSZ, SOFT_TEXT, 0f)
)

/** Instrument Sans for UI labels, buttons, metadata, the tab bar. */
val UiFamily = FontFamily(listOf(400, 600, 650, 700, 800).map { instrument(it) } + instrument(400, italic = true))

/**
 * Named text styles from the Quiet Library mock's CSS (ADR-0052), in sp. Colours and glows come from [FlowFolio]'s role
 * styles.
 */
object FlowFolioType {
    /** `.ttl`: screen titles ("Library"); 44 sp on the unfolded screen ([screenTitleWide]). */
    val screenTitle =
        TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(600), fontSize = 38.sp, lineHeight = 40.sp)

    val screenTitleWide = screenTitle.copy(fontSize = 44.sp, lineHeight = 46.sp)

    /** `.det-body h1`: the book title on its details page. */
    val bookTitle =
        TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(700), fontSize = 30.sp, lineHeight = 31.sp)

    /** `.hero-card h2`. */
    val heroTitle =
        TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(600), fontSize = 22.sp, lineHeight = 25.sp)

    /** `.sheet h3`, `.dlg h3`: sheet and dialog titles. */
    val dialogTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(600), fontSize = 21.sp)

    /** `.shelf-hd h3`. */
    val sectionTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(600), fontSize = 21.sp)

    /** Plain interface text: menu entries, dialog bodies, list rows. */
    val body = TextStyle(fontFamily = UiFamily, fontSize = 14.sp, lineHeight = 21.sp)

    /** `.det-body .subt`: italic subtitle. */
    val subtitle = TextStyle(fontFamily = DisplayFamily, fontStyle = FontStyle.Italic, fontSize = 17.sp)

    /** `.prose`: descriptions and bios. */
    val prose = TextStyle(fontFamily = ProseFamily, fontSize = 16.sp, lineHeight = 25.6.sp)

    /** `.hdr .sub` and other secondary lines. */
    val sub = TextStyle(fontFamily = UiFamily, fontSize = 13.5.sp)

    /** Row labels (`.set-row .lbl b`). */
    val rowLabel = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(600), fontSize = 15.sp)

    /** Row details (`.set-row .lbl span`). */
    val rowDetail = TextStyle(fontFamily = UiFamily, fontSize = 12.sp)

    /** `.set-h`, `.eyebrow`: small caps-style section labels (write the text in upper case). */
    val eyebrow =
        TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(700), fontSize = 11.sp, letterSpacing = 0.14.em)

    /** `.btn`. */
    val button = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(600), fontSize = 14.5.sp)

    /** `.chip`. */
    val chip = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(500), fontSize = 13.sp)

    /** `.pill`, `.seg button`. */
    val pill = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(600), fontSize = 11.5.sp)

    /** `.tab`: tab bar labels. */
    val tab = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(500), fontSize = 11.sp)

    /** `.stat b`: big numbers. */
    val stat = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(600), fontSize = 21.sp)
}

/** Material's type scale on the mock's fonts: display, headline and large titles in Fraunces, the rest UI. */
internal fun flowFolioTypography(base: Typography = Typography()): Typography {
    fun display(s: TextStyle) = s.copy(fontFamily = DisplayFamily)
    fun ui(s: TextStyle) = s.copy(fontFamily = UiFamily)
    return base.copy(
        displayLarge = display(base.displayLarge),
        displayMedium = display(base.displayMedium),
        displaySmall = display(base.displaySmall),
        headlineLarge = display(base.headlineLarge),
        headlineMedium = display(base.headlineMedium),
        headlineSmall = display(base.headlineSmall),
        titleLarge = display(base.titleLarge).copy(fontWeight = FontWeight(TITLE_WEIGHT)),
        titleMedium = ui(base.titleMedium),
        titleSmall = ui(base.titleSmall),
        bodyLarge = ui(base.bodyLarge),
        bodyMedium = ui(base.bodyMedium),
        bodySmall = ui(base.bodySmall),
        labelLarge = ui(base.labelLarge).copy(fontWeight = FontWeight.Bold),
        labelMedium = ui(base.labelMedium),
        labelSmall = ui(base.labelSmall)
    )
}

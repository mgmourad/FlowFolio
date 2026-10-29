package com.flowfolio.core.reflow

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.graphics.fonts.SystemFonts
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * The face code is set in: the app's own code font once the reader has [use]d it (JetBrains Mono, the same on every
 * phone), else a system font file that really is monospaced. `Typeface.MONOSPACE` comes last: it is whatever the phone
 * maps it to, and a phone's font style (Samsung's, for one) can swap it for a proportional face, which breaks code's
 * columns and charts drawn with characters.
 */
object CodeFace {
    @Volatile
    private var bundled: Typeface? = null

    private val system: Typeface by lazy { fromSystemFiles() ?: Typeface.MONOSPACE }

    val typeface: Typeface get() = bundled ?: system

    /** Sets code in [typeface] from now on (the app's bundled code font). */
    fun use(typeface: Typeface) {
        bundled = typeface
    }

    private fun fromSystemFiles(): Typeface? = runCatching {
        val upright = SystemFonts.getAvailableFonts().filter { f ->
            f.file?.name?.contains("Mono", ignoreCase = true) == true &&
                f.style.weight == FontStyle.FONT_WEIGHT_NORMAL && f.style.slant == FontStyle.FONT_SLANT_UPRIGHT
        }
        upright.sortedBy(::rank).firstNotNullOfOrNull { font -> typefaceOf(font)?.takeIf(::isMonospaced) }
    }.getOrNull()

    /** Where [font]'s file comes in [PREFERRED]; others after. */
    private fun rank(font: Font): Int {
        val name = font.file?.name.orEmpty()
        val at = PREFERRED.indexOfFirst { name.startsWith(it) }
        return if (at < 0) PREFERRED.size else at
    }

    private fun typefaceOf(font: Font): Typeface? = runCatching {
        Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build()).build()
    }.getOrNull()

    private fun isMonospaced(typeface: Typeface): Boolean {
        val paint = Paint().apply {
            this.typeface = typeface
            textSize = PROBE_SIZE
        }
        val narrow = paint.measureText("i")
        return narrow > 0f && narrow == paint.measureText("W")
    }

    /** Sans faces first: they suit the reader's panels better than a typewriter's slab serif. */
    private val PREFERRED = listOf("RobotoMono", "DroidSansMono", "NotoSansMono", "SourceCodePro")
    private const val PROBE_SIZE = 40f
}

/**
 * Code within running text (`<code>`), in [CodeFace]'s face as it is when drawn, so a chapter parsed before the reader
 * set its code font still shows it. The text's own weight and slant are kept.
 */
internal class CodeSpan : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) = apply(paint)

    override fun updateMeasureState(paint: TextPaint) = apply(paint)

    private fun apply(paint: TextPaint) {
        val style = paint.typeface?.style ?: Typeface.NORMAL
        paint.typeface = if (style == Typeface.NORMAL) CodeFace.typeface else Typeface.create(CodeFace.typeface, style)
    }
}

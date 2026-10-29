package com.flowfolio.core.reflow

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Code is laid out as written: monospaced, its lines kept, scaled down to fit the column rather than wrapped. */
@RunWith(AndroidJUnit4::class)
class CodeLayoutTest {
    private val paginator = Paginator(PageSpec(600, 900, 30f, Locale.ENGLISH)) { null }

    @Test
    fun aLineWiderThanTheColumnScalesTheListingDownInsteadOfWrapping() {
        // A line a third wider than the column at the code size (measured, as fonts differ between test and phone).
        val paint = TextPaint().apply {
            typeface = Typeface.MONOSPACE
            textSize = 30f * Paginator.CODE_SCALE
        }
        val column = 600 - 2 * paginator.codePadPx()
        val wide = "-".repeat(ceil(column * WIDER / paint.measureText("-")).toInt())
        val layout = paginator.layout(code("$wide\n| a |"))
        assertEquals(2, layout.lineCount)
        // Scaled down, not wrapped. (Robolectric measures text the same at any size, so how well it then fits the
        // column is checked on the device.)
        assertTrue(layout.paint.textSize < 30f * Paginator.CODE_SCALE)
    }

    @Test
    fun aListingThatFitsKeepsItsSizeAndBlankLines() {
        val layout = paginator.layout(code("a = 1\n\nb = 2"))
        assertEquals(3, layout.lineCount)
        assertEquals(30f * Paginator.CODE_SCALE, layout.paint.textSize, 0.01f)
    }

    @Test
    fun aCodePanelPadsItsText() {
        val block = code("x")
        assertEquals(paginator.codePadPx(), paginator.indentPx(block))
        assertTrue(paginator.layout(block).width <= 600 - 2 * paginator.codePadPx())
    }

    private fun code(text: String) = Block.Text(0, SpannableStringBuilder(text), TextStyle(preformatted = true))

    private companion object {
        const val WIDER = 1.3f
    }
}

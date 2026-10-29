package com.flowfolio.core.reflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterParserTest {
    @Test
    fun listsAndHandBrokenLinesAreSetRaggedAndTheRestJustified() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body>
            <p>Before.</p>
            <ul><li><p>Item.</p><ol><li><p>Nested.</p></li></ol></li></ul>
            <dl><dt>Term</dt><dd>Definition.</dd></dl>
            <p>One<br/>Two<br/>Three</p>
            <p>Line<br/>break</p>
            <p>After.</p></body></html>"""
        var island = 0
        val chapter = ChapterParser("c.xhtml") { island++ }.parse(xhtml.toByteArray())
        val justified = chapter.blocks.filterIsInstance<Block.Text>().associate {
            it.text.toString() to it.style.justify
        }
        assertEquals(
            mapOf(
                "Before." to true,
                "Item." to false,
                "Nested." to false,
                "Term" to false,
                "Definition." to false,
                "One\nTwo\nThree" to false,
                "Line\nbreak" to true,
                "After." to true
            ),
            justified
        )
    }

    @Test
    fun listItemsCarryTheirMarkersOutsideTheText() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body>
            <ul><li>Apple</li><li><p>Pear</p><ul><li>Nested</li></ul></li></ul>
            <ol start="3"><li>Third</li><li>Fourth</li></ol>
            <ol type="a"><li>Letter</li><li>Next</li></ol>
            <ol type="i"><li>Roman</li><li>Two</li></ol>
            <dl><dt>Term</dt></dl>
            <p>After.</p></body></html>"""
        val chapter = ChapterParser("c.xhtml") { 0 }.parse(xhtml.toByteArray())
        val markers = chapter.blocks.filterIsInstance<Block.Text>().associate { it.text.toString() to it.style.marker }
        assertEquals(
            mapOf(
                "Apple" to "•",
                "Pear" to "•",
                "Nested" to "◦",
                "Third" to "3.",
                "Fourth" to "4.",
                "Letter" to "a.",
                "Next" to "b.",
                "Roman" to "i.",
                "Two" to "ii.",
                "Term" to null,
                "After." to null
            ),
            markers
        )
        // The marker is drawn beside the text, not part of it: offsets (and highlights placed by them) don't move.
        assertEquals(
            "ApplePearNestedThirdFourthLetterNextRomanTwoTermAfter.".length,
            chapter.blocks.sumOf {
                it.length
            }
        )
    }

    @Test
    fun inlineCodeTakesTheCodeFaceSetAfterParsing() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body>
            <p>Run <code>helm install</code> now.</p></body></html>"""
        val text = ChapterParser("c.xhtml") { 0 }.parse(xhtml.toByteArray()).blocks
            .filterIsInstance<Block.Text>().single().text as android.text.Spanned
        val span = text.getSpans(0, text.length, CodeSpan::class.java).single()
        assertEquals("helm install", text.substring(text.getSpanStart(span), text.getSpanEnd(span)))
        // The reader sets its code font once it has loaded it, maybe after the chapter was parsed.
        CodeFace.use(android.graphics.Typeface.DEFAULT_BOLD)
        val paint = android.text.TextPaint()
        span.updateDrawState(paint)
        assertEquals(android.graphics.Typeface.DEFAULT_BOLD, paint.typeface)
    }

    @Test
    fun anItemWithItsOwnGlyphKeepsIt() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body>
            <ul><li style="list-style-type: '✅ '">Git</li><li>Plain</li><li style="list-style-type: '🔹 '">Docker</li></ul>
            </body></html>"""
        val chapter = ChapterParser("c.xhtml") { 0 }.parse(xhtml.toByteArray())
        val markers = chapter.blocks.filterIsInstance<Block.Text>().associate { it.text.toString() to it.style.marker }
        assertEquals(mapOf("Git" to "✅", "Plain" to "•", "Docker" to "🔹"), markers)
    }

    @Test
    fun codeKeepsItsLinesAndSpaces() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body>
            <pre class="ff-code"><code>def f():
    return  1</code></pre></body></html>"""
        val block = ChapterParser("c.xhtml") { 0 }.parse(xhtml.toByteArray()).blocks.single() as Block.Text
        assertEquals("def f():\n    return  1", block.text.toString())
        assertEquals(true, block.style.preformatted)
        assertEquals(false, block.style.justify)
    }
}

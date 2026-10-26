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
}

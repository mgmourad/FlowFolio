package com.flowfolio.tools.convert

import java.io.File
import java.util.zip.ZipFile
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The structure of a technical book survives conversion (`technical.pdf`, made by `tools/testpdfs`): code keeps its
 * lines, indentation and blank lines, and a diagram drawn with characters its shape; lists keep their items, depth,
 * kind and numbering; inline code is marked; headings are the book's (its chapter labels on their titles, no running
 * header); a table without rules is a table, not a heading and a paragraph per cell.
 */
class TechnicalBookTest {
    @Test
    fun codeKeepsItsLinesIndentationAndBlankLines() {
        assertTrue(
            chapter1,
            chapter1.contains(
                "<code>def greet(name):\n    if name:\n        print(&quot;Hello, &quot; + name)\n" +
                    "\n    return name</code>"
            )
        )
    }

    @Test
    fun aDiagramDrawnWithCharactersKeepsItsShape() {
        val diagram = listOf(
            "+--------+      +--------+",
            "| client | ---&gt; | server |",
            "+--------+      +--------+",
            "     |               |",
            "     v               v",
            "  [ cache ]      [ store ]"
        ).joinToString("\n")
        assertTrue(chapter1, chapter1.contains("<code>$diagram</code>"))
    }

    @Test
    fun aShadedBoxOfCommandsIsAListingOfItsOwn() {
        assertTrue(chapter1.contains("<code>\$ git add *.c\n\$ git commit -m 'Initial version'</code>"))
    }

    @Test
    fun aListingOverAPageTurnStaysOne() {
        val listing = Regex(
            "<code>for i in range\\(3\\):\n    total = total \\+ i\n" +
                "<span [^>]*id=\"page-3\"[^>]*/>    print\\(total\\)\nprint\\('done'\\)</code>"
        )
        assertTrue(chapter1, listing.containsMatchIn(chapter1))
    }

    @Test
    fun inlineCodeIsMarked() {
        assertTrue(chapter1.contains("Call <code>print()</code> to show text, and <code>len(s)</code> to count it."))
    }

    @Test
    fun listsKeepTheirItemsDepthKindAndNumbering() {
        assertTrue(
            chapter1,
            chapter1.contains(
                "<li>Lists keep each item whole, even when an item runs over more than one line of the page.<ul>" +
                    "<li>A nested item sits further in.</li>" +
                    "<li>So does this one, whose text also runs on to a second " +
                    "line that hangs under its first word.</li></ul></li><li>Back at the first depth again.</li></ul>"
            )
        )
        assertTrue(chapter1.contains("""data-ff-page="1"><li>Install Python from its website.</li>"""))
        assertTrue(Regex("<ol [^>]*><li>Install Python").containsMatchIn(chapter1))
        assertTrue(chapter1.contains("<li>Symbols set in a dingbat font are bullets too.</li><li>This one is a second"))
        assertEquals(3, Regex("<[uo]l id=").findAll(chapter1).count())
    }

    @Test
    fun headingsAreTheBooksOwn() {
        assertTrue(chapter1.contains(">Chapter 1<br/>Getting Started</h1>"))
        assertTrue(chapter2.contains(">Chapter 2<br/>Going Further</h1>"))
        assertTrue(chapter1.contains(">1.1 Lists and bullets</h2>"))
        // The book's title is the page's <title>; in the body it was only the running header.
        val body = chapter1.substringAfter("<body")
        assertFalse("the running header is not text", body.contains("Learning Python by Example"))
    }

    @Test
    fun aTableWithoutRulesIsATable() {
        assertTrue(chapter1.contains("""<figure class="ff-table ff-fallback""""))
        assertFalse(chapter1.contains(">Escape Meaning Example</h"))
        assertFalse(chapter1.contains("<p id=\"p0003-b004\""))
    }

    companion object {
        private lateinit var out: File
        private lateinit var chapter1: String
        private lateinit var chapter2: String

        @BeforeClass
        @JvmStatic
        fun convertOnce() {
            out = kotlin.io.path.createTempDirectory("ff-technical").toFile()
            convert(File("../../core/pdf/src/androidTest/assets/pdfs/technical.pdf"), out)
            ZipFile(File(out, "book.epub")).use { zip ->
                chapter1 = zip.getInputStream(zip.getEntry("OEBPS/ch001.xhtml")).reader().readText()
                chapter2 = zip.getInputStream(zip.getEntry("OEBPS/ch002.xhtml")).reader().readText()
            }
        }

        @AfterClass
        @JvmStatic
        fun cleanUp() {
            out.deleteRecursively()
        }
    }
}

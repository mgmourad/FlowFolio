package com.flowfolio.feature.reader

import androidx.compose.ui.graphics.luminance
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.riffle.Section
import com.flowfolio.feature.reader.riffle.SectionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reader's book as the riffle scrubber sees it (ADR-0022). */
class ReaderBookTest {
    private val navigation = BookNavigation(
        outline = listOf(
            OutlineEntry(0, 0, "Preface"),
            OutlineEntry(0, 1, "1 Units"),
            OutlineEntry(1, 2, "1.1 Standards"),
            OutlineEntry(0, 5, "Chapter 2 Motion"),
            OutlineEntry(0, 8, "Glossary"),
            OutlineEntry(0, 9, "Index")
        ),
        labels = listOf(null, "i", "1", "2", "3", "4", "5", "6", "7", "8")
    )

    private fun book(layout: ReaderLayout, coverAlone: Boolean = true, rtl: Boolean = false): ReaderBook {
        val units = if (layout == ReaderLayout.SPREAD) (if (coverAlone) 6 else 5) else 10
        val s = ReaderState.Ready("t", 10, layout, unit = 0, unitCount = units, anchorPage = 0, coverAlone = coverAlone)
        return ReaderBook(s, navigation, rtl)
    }

    @Test
    fun spreadsFollowTheReadersPairing() {
        val book = book(ReaderLayout.SPREAD)
        assertEquals(0..0, book.pagesOf(0))
        assertEquals(3..4, book.pagesOf(2))
        assertEquals(9..9, book.pagesOf(5))
        assertEquals(2, book.unitOf(4))
        assertEquals(3, book.anchorOf(2))
        val paired = book(ReaderLayout.SPREAD, coverAlone = false)
        assertEquals(2..3, paired.pagesOf(1))
        assertEquals(1, paired.unitOf(3))
    }

    @Test
    fun singlePagesAreTheirOwnUnits() {
        val book = book(ReaderLayout.SINGLE)
        assertEquals(10, book.unitCount)
        assertEquals(7..7, book.pagesOf(7))
        assertEquals(7, book.unitOf(7))
        assertEquals(7, book.anchorOf(7))
    }

    @Test
    fun topLevelContentsKeepTheBooksOwnChapterNumbers() {
        assertEquals(
            listOf(
                Section(0, "Preface", SectionKind.CHAPTER),
                Section(1, "Units", SectionKind.CHAPTER, 1),
                Section(5, "Motion", SectionKind.CHAPTER, 2),
                Section(8, "Glossary", SectionKind.GLOSSARY),
                Section(9, "Index", SectionKind.INDEX)
            ),
            book(ReaderLayout.SINGLE).sections
        )
    }

    @Test
    fun printedNumbersLabelAndFindPages() {
        val book = book(ReaderLayout.SINGLE, rtl = true)
        assertEquals("i", book.label(1))
        assertEquals("1", book.label(0)) // no printed number: counted from the cover
        assertEquals(4, book.pageOfLabel("3"))
        assertNull(book.pageOfLabel("xx"))
        assertEquals(true, book.rightToLeft)
    }

    @Test
    fun riffleBoardAndPageColorsFollowReadingToneLuminanceRules() {
        for (tone in listOf(PageTone.RUSTIC, PageTone.NIGHT, PageTone.BLACK)) {
            val colors = boardColors(tone)
            val pages = colors.pageColor!!
            assertTrue(
                "Expected riffled pages for $tone to be lighter than its covers",
                pages.luminance() > colors.main.luminance()
            )
        }
        for (tone in listOf(PageTone.PRINTED, PageTone.PAPER, PageTone.SEPIA)) {
            val colors = boardColors(tone)
            val pages = colors.pageColor!!
            assertTrue(
                "Expected riffled pages for $tone to be darker than its covers",
                pages.luminance() < colors.main.luminance()
            )
        }
    }

    @Test
    fun dictionaryFuzzySearchMatchesPrefixSubstringAndSubsequence() {
        assertTrue(fuzzyScore("ephem", "ephemeral") > fuzzyScore("eph", "the ephemeral moment"))
        assertTrue(fuzzyScore("ephmrl", "ephemeral") > 0)
        assertEquals(0, fuzzyScore("xyz", "ephemeral"))
    }
}

package com.flowfolio.core.data

import com.flowfolio.core.epub.EpubMetadata
import com.flowfolio.core.epub.EpubPackage
import com.flowfolio.core.epub.NavPoint
import com.flowfolio.core.epub.SpineItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** Naming an EPUB highlight's or ribbon's chapter from its locator (ADR-0040). */
class ChapterTitlesTest {
    @Test
    fun eachChapterTakesTheContentsEntryItFallsUnder() {
        val pkg = pkg(
            spine = listOf("cover.xhtml", "c1.xhtml", "c1b.xhtml", "c2.xhtml", "notes.xhtml"),
            toc = listOf(
                NavPoint("One", "c1.xhtml", listOf(NavPoint("One, part two", "c1b.xhtml#start"))),
                NavPoint("Two", "c2.xhtml")
            )
        )
        // The cover comes before any entry; a chapter without its own entry keeps the one before it.
        assertEquals(listOf(null, "One", "One, part two", "Two", "Two"), chapterTitles(pkg))
    }

    @Test
    fun nonLinearItemsAreLeftOutAsTheReaderLeavesThemOut() {
        val pkg = pkg(
            spine = listOf("c1.xhtml", "aside.xhtml", "c2.xhtml"),
            toc = listOf(NavPoint("One", "c1.xhtml"), NavPoint("Two", "c2.xhtml")),
            nonLinear = setOf("aside.xhtml")
        )
        assertEquals(listOf("One", "Two"), chapterTitles(pkg))
    }

    private fun pkg(spine: List<String>, toc: List<NavPoint>, nonLinear: Set<String> = emptySet()) = EpubPackage(
        packagePath = "content.opf",
        metadata = EpubMetadata(null, emptyList(), null, emptyList(), null, null, null),
        manifest = emptyMap(),
        spine = spine.map { SpineItem(it, "application/xhtml+xml", it !in nonLinear) },
        toc = toc,
        pageList = emptyList(),
        landmarks = emptyList(),
        rightToLeft = false,
        fixedLayout = false,
        coverPath = null
    )
}

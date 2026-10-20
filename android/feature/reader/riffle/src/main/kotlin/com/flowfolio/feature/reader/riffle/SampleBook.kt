package com.flowfolio.feature.reader.riffle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The mock's sample book (spec 9): *Cellular Biology*, 842 pages, 17 chapters plus a glossary and an index, five
 * bookmarks. Shown as spreads with the cover on its own, like a printed book; [singlePages] shows one page per unit, as
 * on the cover screen. For the demo screen and tests.
 */
class SampleBook(private val singlePages: Boolean = false, override val rightToLeft: Boolean = false) : BookGeometry {
    override val pageCount = PAGES

    override val unitCount = if (singlePages) PAGES else PAGES / 2 + 1

    override fun pagesOf(unit: Int): IntRange = when {
        singlePages -> unit..unit
        unit == 0 -> 0..0
        else -> (2 * unit - 1)..minOf(2 * unit, PAGES - 1)
    }

    override fun unitOf(page: Int): Int = if (singlePages) page else (page + 1) / 2

    // The mock's chapter list, its printed (1-based) first pages turned into 0-based pages.
    override val sections: List<Section> = CHAPTERS.mapIndexed { i, (first, title) ->
        Section(first - 1, title, SectionKind.CHAPTER, i + 1)
    } + listOf(
        Section(GLOSSARY - 1, "Glossary", SectionKind.GLOSSARY),
        Section(INDEX - 1, "Index", SectionKind.INDEX)
    )

    override fun label(page: Int): String = (page + 1).toString()

    override fun pageOfLabel(label: String): Int? = label.trim().toIntOrNull()?.minus(1)?.takeIf { it in 0 until PAGES }

    /** The mock's five bookmarks, in its dark-theme flag colours. */
    val flags: RiffleFlags = RiffleFlags { flagState }

    private val flagState: Flow<List<RiffleFlag>> = MutableStateFlow(
        FLAGS.mapIndexed { i, page -> RiffleFlag(i.toLong(), page - 1, FLAG_COLORS[i % FLAG_COLORS.size]) }
    )

    companion object {
        const val PAGES = 842
        private const val GLOSSARY = 803
        private const val INDEX = 817
        private val FLAGS = listOf(88, 233, 433, 590, 702)
        private val FLAG_COLORS = listOf(0xFF00F0FF, 0xFFFF8A00, 0xFFC02F9A, 0xFF2ECC8A, 0xFFFFD166).map { it.toInt() }
        private val CHAPTERS = listOf(
            1 to "The Chemistry of Life",
            37 to "Cell Architecture",
            71 to "Proteins at Work",
            109 to "Enzymes and Catalysis",
            143 to "Energy and Metabolism",
            181 to "Membrane Structure",
            213 to "Membrane Transport",
            259 to "Cell Signaling",
            301 to "The Cytoskeleton",
            355 to "The Nucleus and DNA",
            409 to "Gene Expression",
            467 to "Protein Sorting",
            527 to "The Cell Cycle",
            589 to "Cell Death",
            647 to "Stem Cells and Development",
            703 to "Cancer Biology",
            761 to "Cells in Tissues"
        )
    }
}

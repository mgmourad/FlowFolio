package com.flowfolio.tools.convert

import com.adobe.epubcheck.api.EpubCheck
import com.adobe.epubcheck.util.DefaultReportImpl
import java.io.File
import kotlin.math.max
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The corpus harness (Phase 3 plan, M2 item 6): every corpus PDF converts to an EPUB with no EPUBCheck errors and a
 * sidecar that follows `docs/schemas/flowfolio.schema.json`, and its metrics hold against
 * `samples/golden/baselines/<id>.json` within the tolerances below. With
 * `-Pflowfolio.recordBaselines` the run writes the baselines instead (after a deliberate change). Skips when the
 * corpus isn't downloaded (`python -I tools/corpus/fetch_corpus.py`).
 */
class CorpusTest {
    private val corpus = File(System.getProperty("flowfolio.corpus") ?: "../../../samples/corpus/cache")
    private val golden = File(System.getProperty("flowfolio.golden") ?: "../../../samples/golden")
    private val record = System.getProperty("flowfolio.recordBaselines") == "true"
    private val schema = SchemaCheck(File(golden, "../../docs/schemas/flowfolio.schema.json"))
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun corpusBooksMeetTheirBaselines() {
        val books = corpus.listFiles { f -> f.extension == "pdf" }.orEmpty().sortedBy { it.name }
        assumeTrue("no corpus PDFs at $corpus", books.isNotEmpty())
        val problems = ArrayList<String>()
        val all = books.map { pdf ->
            val id = pdf.nameWithoutExtension
            val metrics = measure(pdf, id)
            println(summary(metrics))
            if (metrics.epubcheckErrors > 0) problems += "$id: ${metrics.epubcheckErrors} EPUBCheck errors"
            val file = File(golden, "baselines/$id.json")
            when {
                record -> file.also { it.parentFile.mkdirs() }.writeText(json.encodeToString(metrics) + "\n")
                file.exists() -> problems += regressions(json.decodeFromString<Metrics>(file.readText()), metrics)
                else -> problems += "$id: no baseline (run with -Pflowfolio.recordBaselines)"
            }
            metrics
        }
        val text = all.sumOf { it.textPages }
        val covered = all.sumOf { (it.coveredShare * it.textPages).toDouble() } / max(text, 1)
        val geometric = all.sumOf { (it.meanGeometricCoverage * it.textPages).toDouble() } / max(text, 1)
        println("corpus: ${all.size} books, $text text pages, %.4f covered, %.4f geometric".format(covered, geometric))
        assertTrue("corpus geometric coverage %.4f fell below 0.9".format(geometric), geometric >= GEOMETRIC_MIN)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun measure(pdf: File, id: String): Metrics {
        val converted = convertBook(pdf, File(golden, "$id/output"))
        val invalid = schema.problems(converted.json)
        val shown = invalid.take(MAX_SHOWN).joinToString("\n")
        assertTrue("$id: flowfolio.json against the schema:\n$shown", invalid.isEmpty())
        val report = DefaultReportImpl(id)
        EpubCheck(converted.epub, report).doValidate()
        val labels = File(golden, "labels/$id.json").takeIf { it.exists() }
            ?.let { json.decodeFromString<List<PageLabel>>(it.readText()) }.orEmpty()
        val doc = converted.result.document
        return Metrics.of(
            id,
            doc,
            converted.result.sidecar.health.summary.chaptersFrom,
            converted.seconds,
            converted.epub.length(),
            report.errorCount + report.fatalErrorCount,
            report.warningCount
        ).copy(labelledHeadings = Labels.headings(doc, labels), labelledOrder = Labels.order(doc, labels))
    }

    /** What got worse than [was] beyond tolerance. Machine-dependent numbers (time, EPUB size) are not compared. */
    private fun regressions(was: Metrics, now: Metrics): List<String> = buildList {
        val id = now.id
        fun less(name: String, before: Float, after: Float, slack: Float) {
            if (after < before - slack) add("$id: $name fell from %.4f to %.4f".format(before, after))
        }
        less("coveredShare", was.coveredShare, now.coveredShare, COVERAGE_SLACK)
        less("meanCoverage", was.meanCoverage, now.meanCoverage, COVERAGE_SLACK)
        less("outlineMatched", was.outlineMatched, now.outlineMatched, OUTLINE_SLACK)
        if (was.labelledHeadings >= 0) less("labelledHeadings", was.labelledHeadings, now.labelledHeadings, 0f)
        if (was.labelledOrder >= 0) less("labelledOrder", was.labelledOrder, now.labelledOrder, 0f)
        val fallbackSlack = max(FALLBACK_SLACK_PAGES, (now.pages * FALLBACK_SLACK_SHARE).toInt())
        if (now.fallbackPages.size > was.fallbackPages.size + fallbackSlack) {
            add("$id: fallback pages rose from ${was.fallbackPages.size} to ${now.fallbackPages.size}")
        }
        if (now.chapters != was.chapters) add("$id: chapters changed from ${was.chapters} to ${now.chapters}")
        if (now.epubcheckWarnings > was.epubcheckWarnings) {
            add("$id: EPUBCheck warnings rose from ${was.epubcheckWarnings} to ${now.epubcheckWarnings}")
        }
    }

    private fun summary(m: Metrics) = (
        "%s: %d pages, covered %.4f (mean %.4f, geometric %.4f), %d fallback, %d chapters (%s), outline %.3f, " +
            "%d headings, %d figures, %d tables, %d equations, %d inline, EPUBCheck %d/%d, labels %.2f/%.2f, " +
            "%.3f s/page, %.1f MB"
        ).format(
        m.id, m.pages, m.coveredShare, m.meanCoverage, m.meanGeometricCoverage, m.fallbackPages.size, m.chapters,
        m.chaptersFrom, m.outlineMatched, m.headings, m.figures, m.tables, m.equations, m.inlineDrawings,
        m.epubcheckErrors, m.epubcheckWarnings, m.labelledHeadings, m.labelledOrder, m.secondsPerPage, m.epubMegabytes
    )

    private companion object {
        const val COVERAGE_SLACK = 0.005f
        const val OUTLINE_SLACK = 0.01f
        const val FALLBACK_SLACK_PAGES = 2
        const val FALLBACK_SLACK_SHARE = 0.005f
        const val GEOMETRIC_MIN = 0.9f
        const val MAX_SHOWN = 20
    }
}

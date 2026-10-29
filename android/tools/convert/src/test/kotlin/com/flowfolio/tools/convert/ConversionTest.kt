package com.flowfolio.tools.convert

import com.adobe.epubcheck.api.EpubCheck
import com.adobe.epubcheck.util.DefaultReportImpl
import com.flowfolio.core.conversion.ConversionException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The converter on the desktop JVM, with the same pipeline the phone runs: the robustness PDFs convert without
 * failing (or are refused with a reason), into EPUBs EPUBCheck finds no error in. The corpus books are [CorpusTest].
 */
class ConversionTest {
    private val robustness = File("../../core/pdf/src/androidTest/assets/pdfs")
    private val schema = SchemaCheck(File("../../../docs/schemas/flowfolio.schema.json"))

    @Test
    fun robustnessPdfsConvertOrAreRefused() {
        val files = robustness.listFiles { f -> f.extension == "pdf" }.orEmpty()
        assumeTrue("no robustness PDFs at $robustness", files.isNotEmpty())
        for (pdf in files) {
            val out = kotlin.io.path.createTempDirectory("ff-robust").toFile()
            val result = runCatching { convert(pdf, out) }
            if (result.isSuccess) {
                assertNoErrors(File(out, "book.epub"))
                val invalid = schema.problems(result.getOrThrow())
                assertTrue("${pdf.name}: $invalid", invalid.isEmpty())
            } else {
                // Only files PDFium can't open (not a PDF, encrypted, broken beyond repair) or that the converter
                // refuses with a reason may fail; anything else is a crash.
                val e = result.exceptionOrNull()
                assertTrue(
                    "${pdf.name}: $e",
                    e is ConversionException || e?.message?.contains("could not open") == true
                )
            }
            out.deleteRecursively()
        }
    }

    /** EPUBs the phone made (WebP; `ConversionOnDeviceTest`, pulled into the corpus' out/ folder) are valid too. */
    @Test
    fun deviceMadeEpubsAreValid() {
        val corpus = File(System.getProperty("flowfolio.corpus") ?: "../../../samples/corpus/cache")
        val epubs = File(corpus, "out").listFiles { f -> f.name.endsWith("-device.epub") }.orEmpty()
        assumeTrue("no device-made EPUBs in $corpus/out", epubs.isNotEmpty())
        epubs.forEach { assertNoErrors(it) }
    }

    private fun assertNoErrors(epub: File) {
        val report = DefaultReportImpl(epub.name)
        EpubCheck(epub, report).doValidate()
        println(
            "${epub.parentFile.name}/${epub.name}: EPUBCheck ${report.errorCount} errors, " +
                "${report.fatalErrorCount} fatal, ${report.warningCount} warnings"
        )
        assertEquals("${epub.parentFile.name}: EPUBCheck errors", 0, report.errorCount + report.fatalErrorCount)
    }
}

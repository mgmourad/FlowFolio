package com.flowfolio.core.conversion.ocr

import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The M3 quality gate for scanned books (ADR-0054), measured against the M1 spike's proofread pages. It runs when
 * pointed at the pages, the models and tessdata:
 *
 *     FLOWFOLIO_OCR_PAGES=<dir of en-NN.jpg/.txt, ar-NN.jpg/.txt> \
 *     FLOWFOLIO_OCR_MODELS=<dir of PP-OCRv6_det_small.onnx, PP-OCRv6_rec_small.onnx, arabic_PP-OCRv5_rec_mobile.onnx> \
 *     FLOWFOLIO_TESSDATA=<dir of ara.traineddata> \
 *       ./gradlew :tools:convert:test --tests '*OcrCorpusTest*'
 *
 * English pages are read by PaddleOCR alone (the 0.85 confidence gate). Arabic pages are read by Tesseract and checked
 * by PaddleOCR (the 40% agreement gate); the spike's manuscripts stay pictures, and a page blurred past reading does
 * too.
 */
class OcrCorpusTest {
    private val pages = System.getenv("FLOWFOLIO_OCR_PAGES")?.let(::File)
    private val models = System.getenv("FLOWFOLIO_OCR_MODELS")?.let(::File)
    private val tessdata = System.getenv("FLOWFOLIO_TESSDATA")?.let(::File)

    @Test
    fun englishScansReadToTextAtTheBaseline() {
        assumeTrue(pages?.isDirectory == true && models?.isDirectory == true)
        val reader = ScannedPageReader(latin(), arabic(), null)
        val (recall, cer, kept) = measure(reader, "en", excluded = emptySet())
        println("EN recall %.1f%% CER %.1f%% kept %d/%d".format(recall * 100, cer * 100, kept.second, kept.first))
        assertTrue("median recall $recall", recall >= EN_RECALL)
        assertTrue("CER $cer", cer <= EN_CER)
        assertTrue("kept ${kept.second}/${kept.first}", kept.second.toFloat() / kept.first >= EN_KEPT)
    }

    @Test
    fun arabicPrintedPagesBecomeTextOnlyWhenTheEnginesAgree() {
        assumeTrue(pages?.isDirectory == true && models?.isDirectory == true && tessdata?.isDirectory == true)
        assumeTrue("no Arabic pages fetched", hasAny("ar"))
        val reader = ScannedPageReader(latin(), arabic(), JvmTesseract(tessdata!!, "ara"))
        val (recall, cer, kept) = measure(reader, "ar", excluded = MANUSCRIPTS + ENGLISH_ABSTRACT)
        println("AR recall %.1f%% CER %.1f%% kept %d/%d".format(recall * 100, cer * 100, kept.second, kept.first))
        // The modern printed pages read well; the older metal type stays pictures (ADR-0054).
        assertTrue("median recall $recall", recall >= AR_RECALL)
        assertTrue("kept ${kept.second}/${kept.first}", kept.second.toFloat() / kept.first >= AR_KEPT)
    }

    @Test
    fun manuscriptsAndBlurredPagesStayPictures() {
        assumeTrue(pages?.isDirectory == true && models?.isDirectory == true && tessdata?.isDirectory == true)
        assumeTrue("no Arabic pages fetched", hasAny("ar"))
        assumeTrue("no English pages fetched", hasAny("en"))
        val reader = ScannedPageReader(latin(), arabic(), JvmTesseract(tessdata!!, "ara"))
        for (id in MANUSCRIPTS) {
            val image = load(File(pages, "$id.jpg"))
            assertNull("$id is a manuscript and must stay a picture", reader.read(image))
        }
        // A clean page blurred past reading also stays a picture.
        val clean = pages!!.listFiles { f -> f.name.matches(Regex("en-\\d+\\.jpg")) }!!.minByOrNull { it.length() }!!
        assertNull("a blurred page must stay a picture", reader.read(blur(load(clean))))
    }

    private fun hasAny(lang: String) =
        pages!!.listFiles { f -> f.name.matches(Regex("$lang-\\d+\\.jpg")) }!!.isNotEmpty()

    /** Median recall and CER over the pages read as text, and (pages read, kept as text) over [lang]-NN pages. */
    private fun measure(reader: OcrReader, lang: String, excluded: Set<String>): Triple<Float, Float, Pair<Int, Int>> {
        val recalls = ArrayList<Float>()
        val cers = ArrayList<Float>()
        var total = 0
        var kept = 0
        for (image in pages!!.listFiles { f -> f.name.matches(Regex("$lang-\\d+\\.jpg")) }!!.sorted()) {
            val id = image.nameWithoutExtension
            if (id in excluded) continue
            total++
            val truth = File(pages, "$id.txt").readText()
            val read = reader.read(load(image))
            if (read != null) {
                kept++
                val text = read.lines.joinToString("\n") { it.text }
                recalls += Recall.of(truth, text)
                cers += Cer.of(truth, text)
            }
        }
        fun median(v: List<Float>) = if (v.isEmpty()) 0f else v.sorted()[v.size / 2]
        return Triple(median(recalls), median(cers), total to kept)
    }

    private fun latin() = PaddleOcr(
        JvmOnnxModel(File(models, "PP-OCRv6_det_small.onnx")),
        JvmOnnxModel(File(models, "PP-OCRv6_rec_small.onnx")),
        OnnxMetadata.read(File(models, "PP-OCRv6_rec_small.onnx"))["character"].orEmpty(),
        "paddle-v6s"
    )

    private fun arabic() = PaddleOcr(
        JvmOnnxModel(File(models, "PP-OCRv6_det_small.onnx")),
        JvmOnnxModel(File(models, "arabic_PP-OCRv5_rec_mobile.onnx")),
        OnnxMetadata.read(File(models, "arabic_PP-OCRv5_rec_mobile.onnx"))["character"].orEmpty(),
        "paddle-ar-v5",
        rightToLeft = true
    )

    /** The scan's pixels as the phone would hand them over; a grey raster is used as-is so ImageIO's gamma is kept. */
    private fun load(file: File): OcrImage {
        val img = ImageIO.read(file)
        val pixels = if (img.type == BufferedImage.TYPE_BYTE_GRAY) {
            val raster = img.raster
            IntArray(img.width * img.height) { i ->
                val g = raster.getSample(i % img.width, i / img.width, 0)
                (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        } else {
            img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
        }
        return OcrImage(img.width, img.height, pixels, 1f)
    }

    /** A heavy Gaussian blur, standing in for a photographed or out-of-focus page. */
    private fun blur(image: OcrImage): OcrImage {
        val src = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        src.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        val k = Kernel(9, 9, FloatArray(81) { 1f / 81f })
        val dst = ConvolveOp(k, ConvolveOp.EDGE_ZERO_FILL, null).filter(src, null)
        return OcrImage(
            image.width,
            image.height,
            dst.getRGB(0, 0, image.width, image.height, null, 0, image.width),
            1f
        )
    }

    private companion object {
        // The spike's 9 Arabic manuscripts (handwriting) and its one English abstract; excluded from the print scores.
        val MANUSCRIPTS = setOf("ar-00", "ar-06", "ar-09", "ar-10", "ar-11", "ar-12", "ar-13", "ar-17", "ar-18")
        val ENGLISH_ABSTRACT = setOf("ar-19")

        // Baselines from the M3 run on the spike's corpus (English 98.9% recall / 3.0% CER / 20 of 21 kept;
        // Arabic printed 93.0% recall / 16.0% CER / 4 of 10 kept — the pre-1950 pages stay pictures, ADR-0054).
        const val EN_RECALL = 0.95f
        const val EN_CER = 0.08f
        const val EN_KEPT = 0.90f
        const val AR_RECALL = 0.80f
        const val AR_KEPT = 0.20f
    }
}

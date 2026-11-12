package com.flowfolio.core.conversion.ocr

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The Kotlin PaddleOCR against the M1 spike's proofread scans (docs/spikes/2026-11-ocr): it must read as well as
 * RapidOCR did there. Skipped unless pointed at the pages and models, so it never runs in CI without them:
 *
 *     FLOWFOLIO_OCR_PAGES=<dir of en-NN.jpg + en-NN.txt> FLOWFOLIO_OCR_MODELS=<dir of .onnx> \
 *       ./gradlew :core:conversion:test --tests '*PaddleOcrScansTest*' -i
 */
class PaddleOcrScansTest {
    private val pages = System.getenv("FLOWFOLIO_OCR_PAGES")?.let(::File)
    private val models = System.getenv("FLOWFOLIO_OCR_MODELS")?.let(::File)

    @Test
    fun englishReadsAsWellAsInTheSpike() {
        assumeTrue(pages?.isDirectory == true && models?.isDirectory == true)
        val ocr = latin()
        val excluded = File(pages, "handwritten.txt").takeIf { it.isFile }?.readText()?.split(Regex("\\s+")).orEmpty()
        val recalls = ArrayList<Float>()
        val kept = ArrayList<String>()
        for (image in pages!!.listFiles { f -> f.name.matches(Regex("en-\\d+\\.jpg")) }!!.sorted()) {
            val name = image.nameWithoutExtension
            if (name in excluded) continue
            val started = System.nanoTime()
            val page = ocr.read(load(image))
            val seconds = (System.nanoTime() - started) / 1e9
            val recall = Recall.of(truthOf(name).readText(), page.lines.joinToString("\n") { it.text })
            recalls += recall
            if (page.confidence >= OcrGate.MIN_CONFIDENCE) kept += name
            println(
                "OCR %s recall %.1f%% conf %.3f %.2fs %d lines".format(
                    name,
                    recall * 100,
                    page.confidence,
                    seconds,
                    page.lines.size
                )
            )
        }
        val median = recalls.sorted()[recalls.size / 2]
        println("OCR median recall %.1f%%, kept %d of %d".format(median * 100, kept.size, recalls.size))
        // The spike's RapidOCR median was 98.7%.
        assertTrue("median recall $median", median >= SPIKE_MEDIAN - TOLERANCE)
    }

    /**
     * Arabic pages never pass as Latin text. The Latin model can be 99.8% sure of nonsense on clean Arabic print
     * (ar-15), so the script check, not the confidence gate, is what keeps them out.
     */
    @Test
    fun arabicPagesDoNotPassForLatin() {
        assumeTrue(pages?.isDirectory == true && models?.isDirectory == true)
        val latin = latin()
        val arabic = arabic()
        for (image in pages!!.listFiles { f -> f.name.matches(Regex("ar-\\d+\\.jpg")) }!!.sorted()) {
            val img = load(image)
            val asLatin = latin.read(img)
            val script = Script.of(img, latin, arabic)
            println(
                "SCRIPT %s latin-conf %.3f script %s".format(image.nameWithoutExtension, asLatin.confidence, script)
            )
            // ar-19 is an English abstract on Arabic Wikisource: Latin is right for it.
            if (image.nameWithoutExtension != "ar-19") {
                assertTrue(image.name, script != Script.LATIN || asLatin.confidence < OcrGate.MIN_CONFIDENCE)
            }
        }
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

    /**
     * The scan's pixels as the phone would hand them over. ImageIO's getRGB gamma-converts grayscale images (most
     * scans), washing them out; a grey sample is used as it is instead.
     */
    private fun load(file: File): OcrImage {
        val img = ImageIO.read(file)
        val pixels = if (img.type == java.awt.image.BufferedImage.TYPE_BYTE_GRAY) {
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

    private companion object {
        const val SPIKE_MEDIAN = 0.987f
        const val TOLERANCE = 0.01f
    }
}

/** A page's proofread text: beside its scan (the spike's layout) or committed under `samples/golden/ocr/`. */
private fun truthOf(name: String): File {
    val pages = System.getenv("FLOWFOLIO_OCR_PAGES")?.let(::File)
    val beside = pages?.let { File(it, "$name.txt") }
    return beside?.takeIf { it.isFile }
        ?: File(File(System.getProperty("flowfolio.golden") ?: "../../samples/golden", "ocr"), "$name.txt")
}

/** The share of a reference's words an OCR reading has (as multisets), line-end hyphens joined, as `rescore.py`. */
object Recall {
    /** Letters and numbers, Arabic and Latin alike (Java's `\w` is ASCII and would miss Arabic). */
    private val WORD = Regex("[\\p{L}\\p{N}]+")

    fun of(truth: String, read: String): Float {
        val want = words(truth)
        val have = words(read).groupingBy { it }.eachCount().toMutableMap()
        var hit = 0
        for (w in want) {
            val n = have[w] ?: 0
            if (n > 0) {
                hit++
                have[w] = n - 1
            }
        }
        return if (want.isEmpty()) 0f else hit.toFloat() / want.size
    }

    private fun words(t: String): List<String> = WORD.findAll(
        java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFKC)
            .replace(Regex("([\\p{L}\\p{N}])-\\s*\\n\\s*([\\p{L}\\p{N}])"), "$1$2")
            .replace(Regex("([\\p{L}\\p{N}])- ([\\p{L}\\p{N}])"), "$1$2")
            .lowercase()
    ).map { it.value }.toList()
}

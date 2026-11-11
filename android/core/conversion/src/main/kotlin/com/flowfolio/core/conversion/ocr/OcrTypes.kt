package com.flowfolio.core.conversion.ocr

/**
 * A page picture for OCR (ADR-0054): [pixels] are ARGB, row by row, [width] x [height]. [scale] is pixels per PDF
 * point, so text found at pixel x lies at x / [scale] points.
 */
class OcrImage(val width: Int, val height: Int, val pixels: IntArray, val scale: Float)

/** A rectangle in pixels of the [OcrImage], top-left origin. */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** One line of text read from a page, in reading order, with the reader's [confidence] (0 to 1). */
data class OcrLine(val text: String, val box: PixelBox, val confidence: Float)

/** What the reader found on a page; [confidence] is the mean of its lines' (0 when nothing was read). */
class OcrPage(val lines: List<OcrLine>, val engine: String) {
    val confidence: Float = if (lines.isEmpty()) 0f else lines.map { it.confidence }.average().toFloat()
}

/**
 * Runs one ONNX model. The phone gives it ONNX Runtime for Android, the desktop and CI the JVM build (ADR-0054); both
 * are the same Java API over the same runtime, so the converter's code around it is shared.
 */
interface OnnxModel : AutoCloseable {
    /** The model's custom metadata (PaddleOCR keeps its recognition alphabet under `character`). */
    val metadata: Map<String, String>

    /** Runs the model on one float tensor of [shape] and returns its first output. */
    fun run(input: FloatArray, shape: LongArray): Tensor
}

class Tensor(val data: FloatArray, val shape: LongArray)

/** Reads the text on a scanned page; null when it reads nothing it trusts. */
fun interface OcrReader {
    fun read(image: OcrImage): OcrPage?

    /** A reader for one book's pages, in order, which may learn from the pages before (the book's script). */
    fun forBook(): OcrReader = this
}

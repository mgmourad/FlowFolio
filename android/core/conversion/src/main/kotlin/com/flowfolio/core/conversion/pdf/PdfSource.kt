package com.flowfolio.core.conversion.pdf

/**
 * What the converter reads from a PDF (Stage B's input). PDFium supplies it: through our JNI bridge on the phone
 * (`:core:pdf`) and through JNA on the desktop JVM for tests and the corpus gate (`:tools:convert`). Both must return
 * the same data for the same file; the conversion itself only ever runs on the phone in the product (ADR-0009).
 * Coordinates are PDF points with the origin at the top left of the page as displayed (its rotation applied).
 */
interface PdfSource : AutoCloseable {
    val pageCount: Int

    /** Document information (`Title`, `Author`); null when absent or blank. */
    fun metadata(key: String): String?

    /** The outline (bookmarks) in document order. */
    fun outline(): List<OutlineItem>

    /** The printed page label of page [index] (0-based) from `/PageLabels`, or null. */
    fun pageLabel(index: Int): String?

    /** Page [index]'s size, characters, and the graphics on it. */
    fun page(index: Int): RawPage

    /**
     * [crops] of page [index] drawn at [scale] pixels per point and encoded: figures, tables, equations and whole-page
     * fallbacks. The page is drawn once for all of them (a textbook page can hold dozens of equations). The phone
     * writes WebP (lossy for photos, lossless for line art), the desktop PNG; an empty image when one can't be drawn.
     */
    fun renderImages(index: Int, crops: List<Crop>, scale: Float): List<EncodedImage>

    /**
     * Page [index] drawn whole at [scale] pixels per point, as ARGB pixels, for OCR (ADR-0054); null when this source
     * cannot draw pages that way (then scanned pages stay pictures).
     */
    fun renderPixels(index: Int, scale: Float): com.flowfolio.core.conversion.ocr.OcrImage? = null
}

/** A region of a page to draw: [photo] allows lossy encoding (pictures, whole pages); line art stays lossless. */
data class Crop(val box: Box, val photo: Boolean)

/**
 * An encoded image and its file [extension] (`webp`, `png`); [coloured] when line art has colour of its own to keep
 * ([Ink.coloured]; not worked out for photos).
 */
class EncodedImage(val bytes: ByteArray, val extension: String, val coloured: Boolean = false) {
    val mediaType: String get() = "image/$extension"
}

/** An outline entry: [depth] from 0, its [title], and the 0-based [page] it points at (-1 when it points nowhere). */
data class OutlineItem(val depth: Int, val title: String, val page: Int)

/** A rectangle in points, top-left origin. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height

    fun union(o: Box) = Box(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))

    fun intersects(o: Box) = left < o.right && o.left < right && top < o.bottom && o.top < bottom

    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom

    companion object {
        /** Floats per packed box: left, top, right, bottom. */
        const val FIELDS = 4
        private const val LEFT = 0
        private const val TOP = 1
        private const val RIGHT = 2
        private const val BOTTOM = 3

        /** The box packed in [values] from [offset]. */
        fun of(values: FloatArray, offset: Int = 0) =
            Box(values[offset + LEFT], values[offset + TOP], values[offset + RIGHT], values[offset + BOTTOM])
    }
}

/**
 * One page as PDFium reads it. [text] holds every character in PDFium's text order (one UTF-16 unit each, as
 * `FPDFText_GetUnicode` gives them); the arrays hold one entry per character. Characters PDFium generates (line breaks,
 * spaces it infers) have [generated] set and an empty box.
 */
// Plain data from the PDF and the stages; grouping fields into more classes would add nothing.
@Suppress("LongParameterList")
class RawPage(
    val index: Int,
    val width: Float,
    val height: Float,
    val text: String,
    /** left, top, right, bottom per character (points); -1s when the character has no box. */
    val boxes: FloatArray,
    /** Font size in points per character. */
    val fontSizes: FloatArray,
    /** Font weight per character (400 regular, 700 bold; -1 unknown). */
    val fontWeights: IntArray,
    /** Index into [fonts] per character, or -1. */
    val fontIds: IntArray,
    /** Fill colour per character, ARGB. */
    val colors: IntArray,
    val generated: BooleanArray,
    val fonts: List<FontInfo>,
    /** Images placed on the page (their bounds). */
    val images: List<Box>,
    /** Vector paths on the page: rules, boxes and drawings (their bounds, and whether filled). */
    val paths: List<PathInfo>
) {
    val length: Int get() = text.length

    fun hasBox(i: Int) = boxes[i * FIELDS] >= 0f

    fun box(i: Int) = Box.of(boxes, i * FIELDS)

    companion object {
        const val FIELDS = Box.FIELDS
    }
}

/** A font as the PDF names it, and its flags (`FXFONT_*`: bit 7 italic, bit 1 fixed pitch, bit 2 serif...). */
data class FontInfo(val name: String, val flags: Int) {
    val italic: Boolean get() = flags and ITALIC != 0 || name.contains("Italic", true) || name.contains("Oblique", true)
    val monospace: Boolean get() = flags and FIXED_PITCH != 0 || MONO_NAMES.any { name.contains(it, true) }
    val math: Boolean get() = MATH_NAMES.any { name.contains(it, true) }

    private companion object {
        const val FIXED_PITCH = 1
        const val ITALIC = 64

        // Measuring the glyphs finds the rest (stage Monospace): these are only the common names.
        val MONO_NAMES = listOf(
            "Mono", "Courier", "Consolas", "Code", "Menlo", "Monaco", "Inconsolata", "CMTT", "SFTT", "LMMono", "txtt",
            "Lucida Console", "LucidaConsole", "Andale", "mplus1mn"
        )
        val MATH_NAMES = listOf("Math", "Symbol", "CMMI", "CMSY", "CMEX", "STIX", "Cambria Math", "MTExtra")
    }
}

/** A vector path's bounds, and whether it is filled (a box or band) rather than only stroked (a rule or outline). */
data class PathInfo(val bounds: Box, val filled: Boolean)

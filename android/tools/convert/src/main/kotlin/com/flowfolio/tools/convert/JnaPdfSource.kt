package com.flowfolio.tools.convert

import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.pdf.Crop
import com.flowfolio.core.conversion.pdf.EncodedImage
import com.flowfolio.core.conversion.pdf.FontInfo
import com.flowfolio.core.conversion.pdf.Ink
import com.flowfolio.core.conversion.pdf.OutlineItem
import com.flowfolio.core.conversion.pdf.PathInfo
import com.flowfolio.core.conversion.pdf.PdfSource
import com.flowfolio.core.conversion.pdf.RawPage
import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * [PdfSource] on the desktop JVM through [Pdfium] (JNA). It reads exactly what `PdfiumConversionSource` reads on the
 * phone, field for field, so the pipeline sees the same pages in tests as in the app.
 */
class JnaPdfSource private constructor(private val data: Memory, private val doc: Pointer) : PdfSource {
    override val pageCount: Int = Pdfium.FPDF_GetPageCount(doc)

    override fun metadata(key: String): String? =
        utf16(sized { buf, len -> Pdfium.FPDF_GetMetaText(doc, key, buf, len) })?.trim()?.takeIf { it.isNotEmpty() }

    override fun pageLabel(index: Int): String? =
        utf16(sized { buf, len -> Pdfium.FPDF_GetPageLabel(doc, index, buf, len) })?.takeIf { it.isNotEmpty() }

    override fun outline(): List<OutlineItem> {
        val out = ArrayList<OutlineItem>()
        var budget = OUTLINE_BUDGET
        fun walk(parent: Pointer?, depth: Int) {
            var bm = Pdfium.FPDFBookmark_GetFirstChild(doc, parent)
            while (bm != null && budget-- > 0) {
                val title = utf16(sized { buf, len -> Pdfium.FPDFBookmark_GetTitle(bm!!, buf, len) }).orEmpty()
                var dest = Pdfium.FPDFBookmark_GetDest(doc, bm)
                if (dest == null) {
                    val action = Pdfium.FPDFBookmark_GetAction(bm)
                    if (action != null && Pdfium.FPDFAction_GetType(action).toLong() == Pdfium.ACTION_GOTO) {
                        dest = Pdfium.FPDFAction_GetDest(doc, action)
                    }
                }
                out += OutlineItem(depth, title.trim(), dest?.let { Pdfium.FPDFDest_GetDestPageIndex(doc, it) } ?: -1)
                if (depth < MAX_OUTLINE_DEPTH) walk(bm, depth + 1)
                bm = Pdfium.FPDFBookmark_GetNextSibling(doc, bm)
            }
        }
        walk(null, 0)
        return out
    }

    // Pages PDFium can't read come back empty, case by case.
    @Suppress("ReturnCount")
    override fun page(index: Int): RawPage {
        val size = FloatArray(2)
        Pdfium.FPDF_GetPageSizeByIndexF(doc, index, size)
        val page = Pdfium.FPDF_LoadPage(doc, index) ?: return empty(index, size[0], size[1])
        try {
            val toTop = Mapper(page, size[0], size[1])
            val (images, paths) = graphics(page, toTop)
            val text = Pdfium.FPDFText_LoadPage(page) ?: return empty(index, size[0], size[1], images, paths)
            try {
                return characters(index, size, text, toTop, images, paths)
            } finally {
                Pdfium.FPDFText_ClosePage(text)
            }
        } finally {
            Pdfium.FPDF_ClosePage(page)
        }
    }

    @Suppress("LongParameterList") // the page's parts as read so far
    private fun characters(
        index: Int,
        size: FloatArray,
        text: Pointer,
        toTop: Mapper,
        images: List<Box>,
        paths: List<PathInfo>
    ): RawPage {
        val n = minOf(Pdfium.FPDFText_CountChars(text), MAX_CHARS).coerceAtLeast(0)
        val chars = StringBuilder(n)
        val boxes = FloatArray(n * RawPage.FIELDS) { -1f }
        val sizes = FloatArray(n)
        val weights = IntArray(n)
        val fontIds = IntArray(n) { -1 }
        val colors = IntArray(n)
        val generated = BooleanArray(n)
        val fonts = LinkedHashMap<FontInfo, Int>()
        val rect = FloatArray(Box.FIELDS)
        val flags = IntArray(1)
        val r = IntArray(1)
        val g = IntArray(1)
        val b = IntArray(1)
        val a = IntArray(1)
        val nameBuf = ByteArray(FONT_NAME_BYTES)
        for (i in 0 until n) {
            chars.append(Pdfium.FPDFText_GetUnicode(text, i).toChar())
            generated[i] = Pdfium.FPDFText_IsGenerated(text, i) == 1
            if (Pdfium.FPDFText_GetLooseCharBox(text, i, rect) != 0) {
                Box.of(rect).let { toTop.box(it.left, it.top, it.right, it.bottom) }.let { box ->
                    floatArrayOf(box.left, box.top, box.right, box.bottom).copyInto(boxes, i * Box.FIELDS)
                }
            }
            sizes[i] = Pdfium.FPDFText_GetFontSize(text, i).toFloat()
            weights[i] = Pdfium.FPDFText_GetFontWeight(text, i)
            val nameSize = NativeLong(nameBuf.size.toLong())
            val nameLen = Pdfium.FPDFText_GetFontInfo(text, i, nameBuf, nameSize, flags).toInt()
            if (nameLen > 1) {
                val name = String(nameBuf, 0, minOf(nameLen, nameBuf.size) - 1, Charsets.UTF_8)
                fontIds[i] = fonts.getOrPut(FontInfo(name, flags[0])) { fonts.size }
            }
            colors[i] = if (Pdfium.FPDFText_GetFillColor(text, i, r, g, b, a) != 0) {
                java.awt.Color(r[0], g[0], b[0], a[0]).rgb
            } else {
                BLACK
            }
        }
        return RawPage(
            index, size[0], size[1], chars.toString(), boxes, sizes, weights, fontIds, colors, generated,
            fonts.keys.toList(), images, paths
        )
    }

    /** Images and paths on the page; a form (an embedded drawing) counts as an image when it holds one. */
    // Each continue skips one kind of page object.
    @Suppress("LoopWithTooManyJumpStatements")
    private fun graphics(page: Pointer, toTop: Mapper): Pair<List<Box>, List<PathInfo>> {
        val images = ArrayList<Box>()
        val paths = ArrayList<PathInfo>()
        val l = FloatArray(1)
        val bt = FloatArray(1)
        val rt = FloatArray(1)
        val t = FloatArray(1)
        val fill = IntArray(1)
        val stroke = IntArray(1)
        val count = minOf(Pdfium.FPDFPage_CountObjects(page), MAX_OBJECTS)
        for (i in 0 until count) {
            val obj = Pdfium.FPDFPage_GetObject(page, i) ?: continue
            val type = Pdfium.FPDFPageObj_GetType(obj)
            if (type != Pdfium.PAGEOBJ_IMAGE && type != Pdfium.PAGEOBJ_PATH && type != Pdfium.PAGEOBJ_FORM) continue
            if (Pdfium.FPDFPageObj_GetBounds(obj, l, bt, rt, t) == 0) continue
            val box = toTop.box(l[0], t[0], rt[0], bt[0])
            when {
                type == Pdfium.PAGEOBJ_IMAGE || (type == Pdfium.PAGEOBJ_FORM && hasImage(obj, 0)) -> images += box

                type == Pdfium.PAGEOBJ_PATH -> {
                    Pdfium.FPDFPath_GetDrawMode(obj, fill, stroke)
                    paths += PathInfo(box, filled = fill[0] != 0)
                }
            }
        }
        return images to paths
    }

    // Each return is one case of PDFium's object tree.
    @Suppress("ReturnCount")
    private fun hasImage(form: Pointer, depth: Int): Boolean {
        if (depth > MAX_FORM_DEPTH) return false
        val n = Pdfium.FPDFFormObj_CountObjects(form)
        for (i in 0 until n) {
            val child = Pdfium.FPDFFormObj_GetObject(form, NativeLong(i.toLong())) ?: continue
            when (Pdfium.FPDFPageObj_GetType(child)) {
                Pdfium.PAGEOBJ_IMAGE -> return true
                Pdfium.PAGEOBJ_FORM -> if (hasImage(child, depth + 1)) return true
            }
        }
        return false
    }

    // Pages PDFium can't draw give empty images, case by case.
    @Suppress("ReturnCount")
    override fun renderImages(index: Int, crops: List<Crop>, scale: Float): List<EncodedImage> {
        if (crops.isEmpty()) return emptyList()
        val size = FloatArray(2)
        Pdfium.FPDF_GetPageSizeByIndexF(doc, index, size)
        val pageW = (size[0] * scale).toInt().coerceIn(1, MAX_RENDER_PX)
        val pageH = (size[1] * scale).toInt().coerceIn(1, MAX_RENDER_PX)
        val none = crops.map { EncodedImage(ByteArray(0), PNG) }
        val page = Pdfium.FPDF_LoadPage(doc, index) ?: return none
        val bitmap = Pdfium.FPDFBitmap_Create(pageW, pageH, 0)
        try {
            bitmap ?: return none
            Pdfium.FPDFBitmap_FillRect(bitmap, 0, 0, pageW, pageH, NativeLong(WHITE))
            Pdfium.FPDF_RenderPageBitmap(bitmap, page, 0, 0, pageW, pageH, 0, Pdfium.RENDER_ANNOT)
            val stride = Pdfium.FPDFBitmap_GetStride(bitmap)
            val pixels = Pdfium.FPDFBitmap_GetBuffer(bitmap).getByteArray(0, stride * pageH)
            return crops.map { c ->
                val image = crop(pixels, stride, pageW, pageH, c.box, scale)
                val coloured =
                    !c.photo &&
                        Ink.coloured(
                            image.getRGB(0, 0, image.width, image.height, null, 0, image.width),
                            image.width,
                            image.height
                        )
                EncodedImage(
                    ByteArrayOutputStream().also {
                        ImageIO.write(image, "png", it)
                    }.toByteArray(),
                    PNG,
                    coloured
                )
            }
        } finally {
            bitmap?.let(Pdfium::FPDFBitmap_Destroy)
            Pdfium.FPDF_ClosePage(page)
        }
    }

    /** [region] (points) of a rendered page (BGRx rows of [stride] bytes). */
    @Suppress("LongParameterList") // the rendered page and the crop
    private fun crop(pixels: ByteArray, stride: Int, pageW: Int, pageH: Int, region: Box, scale: Float): BufferedImage {
        val x0 = (region.left * scale).toInt().coerceIn(0, pageW - 1)
        val y0 = (region.top * scale).toInt().coerceIn(0, pageH - 1)
        val x1 = (region.right * scale).toInt().coerceIn(x0 + 1, pageW)
        val y1 = (region.bottom * scale).toInt().coerceIn(y0 + 1, pageH)
        val image = BufferedImage(x1 - x0, y1 - y0, BufferedImage.TYPE_INT_RGB)
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                // PDFium's bitmaps are BGRA.
                val o = y * stride + x * BYTES_PER_PIXEL
                fun channel(k: Int) = pixels[o + k].toInt() and BYTE
                val px = java.awt.Color(channel(RED), channel(GREEN), channel(BLUE)).rgb
                image.setRGB(x - x0, y - y0, px)
            }
        }
        return image
    }

    override fun close() {
        Pdfium.FPDF_CloseDocument(doc)
        data.close()
    }

    /** Page space (origin bottom left, unrotated) to points from the top left of the page as displayed. */
    private class Mapper(private val page: Pointer, width: Float, height: Float) {
        private val w = (width * SUBPOINTS).toInt()
        private val h = (height * SUBPOINTS).toInt()
        private val x = IntArray(1)
        private val y = IntArray(1)

        fun box(left: Float, top: Float, right: Float, bottom: Float): Box {
            Pdfium.FPDF_PageToDevice(page, 0, 0, w, h, 0, left.toDouble(), top.toDouble(), x, y)
            val x1 = x[0]
            val y1 = y[0]
            Pdfium.FPDF_PageToDevice(page, 0, 0, w, h, 0, right.toDouble(), bottom.toDouble(), x, y)
            return Box(
                minOf(x1, x[0]) / SUBPOINTS,
                minOf(y1, y[0]) / SUBPOINTS,
                maxOf(x1, x[0]) / SUBPOINTS,
                maxOf(y1, y[0]) / SUBPOINTS
            )
        }
    }

    companion object {
        private const val BYTES_PER_PIXEL = 4
        private const val BLUE = 0
        private const val GREEN = 1
        private const val RED = 2
        private const val BYTE = 0xFF

        /** Opens [file], reading it into native memory (PDFium keeps a pointer to it while the document is open). */
        fun open(file: File, password: String? = null): JnaPdfSource {
            val bytes = file.readBytes()
            val data = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
            val doc = Pdfium.FPDF_LoadMemDocument64(data, bytes.size.toLong(), password)
                ?: error("PDFium could not open ${file.name}: error ${Pdfium.FPDF_GetLastError()}")
            return JnaPdfSource(data, doc)
        }

        private fun empty(
            index: Int,
            w: Float,
            h: Float,
            images: List<Box> = emptyList(),
            paths: List<PathInfo> = emptyList()
        ) = RawPage(
            index, w, h, "", FloatArray(0), FloatArray(0), IntArray(0), IntArray(0), IntArray(0),
            BooleanArray(0), emptyList(), images, paths
        )

        /** A PDFium getter that fills a buffer: asked for its length first, then for the bytes. */
        private fun sized(read: (ByteArray?, NativeLong) -> NativeLong): ByteArray? {
            val n = read(null, NativeLong(0)).toInt()
            if (n <= 2 || n > MAX_STRING_BYTES) return null
            val buf = ByteArray(n)
            read(buf, NativeLong(n.toLong()))
            return buf
        }

        /** UTF-16LE with a two-byte terminator, as PDFium returns strings. */
        private fun utf16(bytes: ByteArray?): String? =
            bytes?.let { String(it, 0, (it.size - 2).coerceAtLeast(0), Charsets.UTF_16LE) }

        private const val SUBPOINTS = 100f
        private const val PNG = "png"
        private const val MAX_CHARS = 200_000
        private const val MAX_OBJECTS = 100_000
        private const val MAX_FORM_DEPTH = 4
        private const val MAX_OUTLINE_DEPTH = 16
        private const val OUTLINE_BUDGET = 20_000
        private const val MAX_STRING_BYTES = 1 shl 20
        private const val FONT_NAME_BYTES = 256
        private const val MAX_RENDER_PX = 8192
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFFL
    }
}

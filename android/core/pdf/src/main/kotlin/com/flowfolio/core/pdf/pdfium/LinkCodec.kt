package com.flowfolio.core.pdf.pdfium

import com.flowfolio.core.pdf.PdfLink

/** Decodes the bridge's "left|top|right|bottom|pageIndex|uri" lines; separate so it is unit-testable on the JVM. */
internal object LinkCodec {
    private const val BOX_FIELDS = 4
    private const val FIELDS = BOX_FIELDS + 2

    fun decode(lines: Array<String>): List<PdfLink> = lines.mapNotNull(::decodeLine)

    private fun decodeLine(line: String): PdfLink? {
        val parts = line.split('|', limit = FIELDS)
        val box = parts.take(BOX_FIELDS).mapNotNull { it.toFloatOrNull()?.coerceIn(0f, 1f) }
        val page = parts.getOrNull(BOX_FIELDS)?.toIntOrNull()
        val uri = parts.getOrNull(BOX_FIELDS + 1)?.takeIf { it.isNotBlank() }
        if (box.size != BOX_FIELDS || page == null) return null
        val (left, top, right) = box
        val bottom = box.last()
        val link = { target: Int?, address: String? -> PdfLink(left, top, right, bottom, target, address) }
        return when {
            page >= 0 -> link(page, null)
            uri != null -> link(null, uri)
            else -> null
        }
    }
}

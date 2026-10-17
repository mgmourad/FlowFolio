package com.flowfolio.core.pdf.pdfium

import com.flowfolio.core.model.OutlineEntry

/** Decodes the bridge's "depth|pageIndex|title" lines. Kept separate so it is unit-testable on the JVM. */
internal object OutlineCodec {
    private const val FIELDS = 3

    fun decode(lines: Array<String>): List<OutlineEntry> = lines.mapNotNull { line ->
        val parts = line.split('|', limit = FIELDS)
        if (parts.size != FIELDS) return@mapNotNull null
        val depth = parts[0].toIntOrNull() ?: return@mapNotNull null
        val page = parts[1].toIntOrNull() ?: return@mapNotNull null
        OutlineEntry(depth, page, parts[2].trim())
    }
}

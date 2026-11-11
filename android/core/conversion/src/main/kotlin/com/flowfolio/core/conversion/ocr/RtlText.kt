package com.flowfolio.core.conversion.ocr

import java.text.Bidi

/**
 * Arabic OCR models read a line as it looks, left to right ("visual order"); text is stored in the order it is read
 * ("logical order"). For a line of one paragraph, applying the Unicode bidi algorithm's reordering to the visual
 * string gives back the logical one: Arabic runs reverse, numbers and Latin words inside them keep their own order.
 *
 * `java.text.Bidi` is in the JDK and on Android, so the phone and CI agree; RapidOCR's `python-bidi` is LGPL and is
 * not used (ADR-0054).
 */
object RtlText {
    fun visualToLogical(line: String): String {
        if (line.isEmpty() || !Bidi.requiresBidi(line.toCharArray(), 0, line.length)) return line
        val bidi = Bidi(line, Bidi.DIRECTION_RIGHT_TO_LEFT)
        val count = bidi.runCount
        val levels = ByteArray(count) { bidi.getRunLevel(it).toByte() }
        val runs = Array<Any>(count) { i ->
            val run = line.substring(bidi.getRunStart(i), bidi.getRunLimit(i))
            if (bidi.getRunLevel(i) % 2 == 1) run.reversed() else run
        }
        Bidi.reorderVisually(levels, 0, runs, 0, count)
        return runs.joinToString("") { it as String }
    }
}

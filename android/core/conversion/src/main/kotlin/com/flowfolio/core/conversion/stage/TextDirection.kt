package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.pdf.RawPage

/** Which way a line of text reads, by Unicode's rule: its first strongly directional character decides. */
object TextDirection {
    /** The characters [chars] of [raw] read right to left: the first strong one is Arabic or Hebrew. */
    fun rightToLeft(raw: RawPage, chars: List<Int>): Boolean {
        for (i in chars) direction(raw.text[i])?.let { return it }
        return false
    }

    fun rightToLeft(text: CharSequence): Boolean {
        for (c in text) direction(c)?.let { return it }
        return false
    }

    /** True for a strong right-to-left character, false for a strong left-to-right one, null for neither. */
    fun direction(c: Char): Boolean? = when (Character.getDirectionality(c)) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
        Character.DIRECTIONALITY_LEFT_TO_RIGHT -> false
        else -> null
    }

    /** Whether most of [text]'s strongly directional characters read right to left (a whole book's script). */
    fun mostlyRightToLeft(text: CharSequence): Boolean {
        var rtl = 0
        var ltr = 0
        for (c in text) {
            when (direction(c)) {
                true -> rtl++
                false -> ltr++
                null -> Unit
            }
        }
        return rtl > ltr
    }
}

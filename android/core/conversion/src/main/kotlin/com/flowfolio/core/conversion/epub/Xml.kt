package com.flowfolio.core.conversion.epub

/**
 * XML text and attribute values: escaped, with characters XML can't hold (controls, lone surrogates) dropped. Line
 * breaks become spaces, except with [keepLines] (code, in a `<pre>`).
 */
// One branch per character XML escapes.
@Suppress("CyclomaticComplexMethod")
internal fun esc(s: String, keepLines: Boolean = false): String {
    val sb = StringBuilder(s.length + s.length / ESCAPE_ROOM)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '&' -> sb.append("&amp;")

            c == '<' -> sb.append("&lt;")

            c == '>' -> sb.append("&gt;")

            c == '"' -> sb.append("&quot;")

            c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> {
                sb.append(c).append(s[i + 1])
                i++
            }

            c.isSurrogate() -> Unit

            c == '\n' && keepLines -> sb.append(c)

            c == '\t' || c == '\n' || c == '\r' -> sb.append(' ')

            c < ' ' || c == '￾' || c == '￿' -> Unit

            else -> sb.append(c)
        }
        i++
    }
    return sb.toString()
}

/** Room for escapes: one character in eight. */
private const val ESCAPE_ROOM = 8

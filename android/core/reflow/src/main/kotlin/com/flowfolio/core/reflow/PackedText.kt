package com.flowfolio.core.reflow

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Markup read only now and then (an island's MathML, M3 EPUB memory), packed once the book is open ([pack], in the
 * background, so opening is not slowed): a textbook's equations are tens of megabytes as strings and a tenth of that
 * packed. Short text, where packing saves little, stays as it is. [text] unpacks it on each call. Thread-safe.
 */
class PackedText(text: String) {
    /** The text until [pack] has run; then null and [packed] holds it. Written last, so [packed] is seen first. */
    @Volatile private var plain: String? = text
    private var packed: ByteArray? = null
    private var size = 0

    /** Packs the text unless it is short or already packed. Call from a background thread. */
    fun pack() {
        val text = plain ?: return
        if (text.length < MIN_PACKED) return
        val raw = text.toByteArray(Charsets.UTF_8)
        val deflater = DEFLATER.get()!!
        val out = ByteArrayOutputStream(raw.size / RATIO_GUESS + BUFFER)
        deflater.reset()
        deflater.setInput(raw)
        deflater.finish()
        val buf = ByteArray(BUFFER)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        packed = out.toByteArray()
        size = raw.size
        plain = null
    }

    fun text(): String {
        plain?.let { return it }
        val inflater = Inflater()
        return try {
            inflater.setInput(packed!!)
            val raw = ByteArray(size)
            var n = 0
            while (n < size && !inflater.finished()) n += inflater.inflate(raw, n, size - n)
            String(raw, 0, n, Charsets.UTF_8)
        } finally {
            inflater.end()
        }
    }

    private companion object {
        const val MIN_PACKED = 512
        const val BUFFER = 8 * 1024
        const val RATIO_GUESS = 6

        /** One deflater per thread, reset for each text: making one costs more than packing a small text. */
        val DEFLATER: ThreadLocal<Deflater> = ThreadLocal.withInitial { Deflater(Deflater.BEST_SPEED) }
    }
}

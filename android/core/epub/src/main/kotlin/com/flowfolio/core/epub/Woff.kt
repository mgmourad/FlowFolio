// Offsets and sizes below are the published layout of WOFF 1 and of the sfnt table directory (ADR-0030).
@file:Suppress("MagicNumber")

package com.flowfolio.core.epub

import java.nio.ByteBuffer
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** WOFF 1 fonts (ADR-0030), unwrapped to the TrueType or OpenType font inside. */
internal object Woff {
    /**
     * A WOFF 1 font as the plain TrueType or OpenType font it wraps: each table inflated (zlib) where it was
     * compressed, then written out behind a fresh table directory. Null when anything is out of bounds.
     */
    fun toSfnt(woff: ByteArray): ByteArray? = try {
        val buf = ByteBuffer.wrap(woff)
        val count = (buf.getShort(12).toInt() and 0xFFFF).takeIf { it in 1..FontFiles.MAX_TABLES } ?: return null
        val tables = (0 until count).map { i ->
            val at = 44 + i * 20
            WoffTable(buf.getInt(at), buf.getInt(at + 4), buf.getInt(at + 8), buf.getInt(at + 12), buf.getInt(at + 16))
        }
        val data = tables.map { tableData(woff, it) }
        if (data.any { it == null }) null else writeSfnt(buf.getInt(4), tables, data.map { it!! })
    } catch (_: IndexOutOfBoundsException) {
        null
    } catch (_: DataFormatException) {
        null
    }

    private class WoffTable(val tag: Int, val offset: Int, val compLength: Int, val origLength: Int, val checksum: Int)

    /** A table's bytes, inflated when stored compressed; null when its numbers don't fit the file. */
    private fun tableData(woff: ByteArray, t: WoffTable): ByteArray? {
        val fits = t.offset >= 0 && t.compLength >= 0 && t.origLength in 0..FontFiles.MAX_FONT_BYTES &&
            t.offset.toLong() + t.compLength <= woff.size && t.compLength <= t.origLength
        return when {
            !fits -> null
            t.compLength == t.origLength -> woff.copyOfRange(t.offset, t.offset + t.compLength)
            else -> inflate(woff, t.offset, t.compLength, t.origLength)
        }
    }

    private fun inflate(bytes: ByteArray, offset: Int, length: Int, size: Int): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(bytes, offset, length)
            val out = ByteArray(size)
            val n = inflater.inflate(out)
            out.takeIf { n == size && inflater.finished() }
        } finally {
            inflater.end()
        }
    }

    private fun writeSfnt(flavor: Int, tables: List<WoffTable>, data: List<ByteArray>): ByteArray? {
        fun padded(n: Int) = (n + 3) and 3.inv() // tables start on 4-byte boundaries
        val header = FontFiles.SFNT_HEADER + tables.size * FontFiles.SFNT_ENTRY
        val total = header.toLong() + data.sumOf { padded(it.size).toLong() }
        if (total > FontFiles.MAX_FONT_BYTES) return null
        val out = ByteBuffer.allocate(total.toInt())
        val power = Integer.highestOneBit(tables.size)
        out.putInt(flavor).putShort(tables.size.toShort())
            .putShort((power * FontFiles.SFNT_ENTRY).toShort())
            .putShort(Integer.numberOfTrailingZeros(power).toShort())
            .putShort(((tables.size - power) * FontFiles.SFNT_ENTRY).toShort())
        var offset = header
        tables.forEachIndexed { i, t ->
            out.putInt(t.tag).putInt(t.checksum).putInt(offset).putInt(data[i].size)
            offset += padded(data[i].size)
        }
        data.forEach { out.put(it).put(ByteArray(padded(it.size) - it.size)) }
        return out.array()
    }
}

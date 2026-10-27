package com.flowfolio.core.epub

import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException

/**
 * A zip read straight from an open file: a descriptor a document provider handed over for a book read in place.
 * `java.util.zip.ZipFile` needs a path, and re-opening `/proc/self/fd/N` by path is refused for files in shared storage
 * under scoped storage, though the descriptor itself reads fine. Reads the central directory once, then each entry at
 * its offset; stored and deflated entries only (what EPUBs use), no ZIP64, no encrypted entries.
 */
internal class ChannelZip(private val channel: FileChannel, private val owner: Closeable) : Closeable {
    class Entry(val name: String, val method: Int, val compressedSize: Long, val size: Long, val headerOffset: Long)

    private val entries: Map<String, Entry> = readDirectory()

    fun entry(name: String): Entry? = entries[name]

    /** The entry's bytes as they inflate. */
    fun open(entry: Entry): InputStream {
        val header = bytes(entry.headerOffset, LOCAL_HEADER)
        if (header.int(0) != LOCAL_SIGNATURE) throw ZipException("bad local header")
        val dataStart = entry.headerOffset + LOCAL_HEADER + header.short(LOCAL_NAME_LENGTH) + header.short(LOCAL_EXTRA)
        val raw = RangeStream(dataStart, entry.compressedSize)
        return when (entry.method) {
            STORED -> raw

            // Raw deflate needs one byte past the data to finish (as ZipFile does).
            DEFLATED -> InflaterInputStream(raw.withDummyByte(), Inflater(true), BUFFER)

            else -> throw ZipException("method ${entry.method}")
        }
    }

    override fun close() = owner.close()

    private fun readDirectory(): Map<String, Entry> {
        val size = channel.size()
        val tailLength = minOf(size, (END_RECORD + MAX_COMMENT).toLong()).toInt()
        val tail = bytes(size - tailLength, tailLength)
        val end = (tailLength - END_RECORD downTo 0).firstOrNull { tail.int(it) == END_SIGNATURE } ?: fail("not a zip")
        val count = tail.short(end + END_COUNT)
        val dirSize = tail.uint(end + END_DIR_SIZE)
        val dirOffset = tail.uint(end + END_DIR_OFFSET)
        if (dirOffset == ZIP64_MARK || dirOffset + dirSize > size) fail("ZIP64 or damaged")
        val dir = bytes(dirOffset, dirSize.toInt())
        val out = HashMap<String, Entry>(count * 2)
        var at = 0
        repeat(count) {
            if (at + DIR_HEADER > dir.limit() || dir.int(at) != DIR_SIGNATURE) fail("bad directory")
            val nameLength = dir.short(at + DIR_NAME_LENGTH)
            val name = ByteArray(nameLength).also { (dir.duplicate().position(at + DIR_HEADER) as ByteBuffer).get(it) }
                .toString(Charsets.UTF_8)
            val encrypted = dir.short(at + DIR_FLAGS) and 1 != 0
            if (!encrypted) {
                out[name] = Entry(
                    name,
                    dir.short(at + DIR_METHOD),
                    dir.uint(at + DIR_COMPRESSED),
                    dir.uint(at + DIR_SIZE),
                    dir.uint(at + DIR_LOCAL_OFFSET)
                )
            }
            at += DIR_HEADER + nameLength + dir.short(at + DIR_EXTRA) + dir.short(at + DIR_COMMENT)
        }
        return out
    }

    /** [length] bytes from [position], read in full. */
    private fun bytes(position: Long, length: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        var at = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, at)
            if (n < 0) throw ZipException("truncated")
            at += n
        }
        return buffer.flip() as ByteBuffer
    }

    /** The bytes of one entry, read where they lie (positional reads, so entries don't disturb each other). */
    private inner class RangeStream(private var position: Long, private var left: Long) : InputStream() {
        private var dummy = false

        fun withDummyByte() = also { dummy = true }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and BYTE
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = when {
            left > 0 -> readData(b, off, len)

            dummy -> {
                dummy = false
                b[off] = 0
                1
            }

            else -> -1
        }

        private fun readData(b: ByteArray, off: Int, len: Int): Int {
            val n = channel.read(ByteBuffer.wrap(b, off, minOf(len.toLong(), left).toInt()), position)
            if (n < 0) fail("truncated")
            position += n
            left -= n
            return n
        }
    }

    private companion object {
        const val STORED = 0
        const val DEFLATED = 8
        const val BUFFER = 8192
        const val BYTE = 0xFF
        const val END_SIGNATURE = 0x06054b50
        const val END_RECORD = 22
        const val MAX_COMMENT = 0xFFFF
        const val END_COUNT = 10
        const val END_DIR_SIZE = 12
        const val END_DIR_OFFSET = 16
        const val ZIP64_MARK = 0xFFFFFFFFL
        const val DIR_SIGNATURE = 0x02014b50
        const val DIR_HEADER = 46
        const val DIR_FLAGS = 8
        const val DIR_METHOD = 10
        const val DIR_COMPRESSED = 20
        const val DIR_SIZE = 24
        const val DIR_NAME_LENGTH = 28
        const val DIR_EXTRA = 30
        const val DIR_COMMENT = 32
        const val DIR_LOCAL_OFFSET = 42
        const val LOCAL_SIGNATURE = 0x04034b50
        const val LOCAL_HEADER = 30
        const val LOCAL_NAME_LENGTH = 26
        const val LOCAL_EXTRA = 28

        fun ByteBuffer.int(at: Int) = getInt(at)

        const val USHORT = 0xFFFF

        fun fail(why: String): Nothing = throw ZipException(why)

        fun ByteBuffer.short(at: Int) = getShort(at).toInt() and USHORT

        fun ByteBuffer.uint(at: Int) = getInt(at).toLong() and ZIP64_MARK
    }
}

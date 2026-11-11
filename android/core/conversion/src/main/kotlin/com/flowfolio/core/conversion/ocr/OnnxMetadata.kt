package com.flowfolio.core.conversion.ocr

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream

/**
 * A model file's custom metadata (`ModelProto.metadata_props`, field 14), read straight from the protobuf as UTF-8.
 * ONNX Runtime's Java API returns the same map through JNI "modified UTF-8", which mangles 4-byte characters (see
 * [PaddleOcr]'s `symbols`); reading the bytes ourselves keeps PaddleOCR's alphabets whole. Other fields, the weights
 * included, are skipped without being read into memory.
 */
object OnnxMetadata {
    fun read(file: File): Map<String, String> = file.inputStream().buffered().use { read(it) }

    fun read(input: InputStream): Map<String, String> {
        val data = DataInputStream(input)
        val out = LinkedHashMap<String, String>()
        var tag = varint(data)
        while (tag != null && field(data, tag, out)) tag = varint(data)
        return out
    }

    /** Reads or skips one field of the model; false at a wire type this reader does not know (stop there). */
    private fun field(data: DataInputStream, tag: Long, out: MutableMap<String, String>): Boolean {
        val number = (tag ushr TYPE_BITS).toInt()
        when ((tag and TYPE_MASK).toInt()) {
            VARINT -> varint(data)

            FIXED64 -> data.skipFully(Long.SIZE_BYTES)

            FIXED32 -> data.skipFully(Int.SIZE_BYTES)

            LENGTH -> {
                val length = varint(data) ?: throw EOFException()
                if (number == METADATA_PROPS) {
                    entry(ByteArray(length.toInt()).also { data.readFully(it) })?.let { (k, v) -> out[k] = v }
                } else {
                    data.skipFully(length)
                }
            }

            else -> return false
        }
        return true
    }

    /** A `StringStringEntryProto`: key (1) and value (2); null when it is not one. */
    private fun entry(bytes: ByteArray): Pair<String, String>? {
        val data = DataInputStream(bytes.inputStream())
        val fields = HashMap<Int, String>()
        var tag = varint(data)
        while (tag != null && (tag and TYPE_MASK).toInt() == LENGTH) {
            val length = varint(data) ?: throw EOFException()
            fields[(tag ushr TYPE_BITS).toInt()] = ByteArray(length.toInt()).also { data.readFully(it) }
                .toString(Charsets.UTF_8)
            tag = varint(data)
        }
        val key = fields[1]
        val value = fields[2]
        return if (tag == null && key != null && value != null) key to value else null
    }

    private fun varint(data: DataInputStream): Long? {
        var result = 0L
        var shift = 0
        while (true) {
            val b = data.read()
            if (b < 0) return if (shift == 0) null else throw EOFException()
            result = result or ((b and SEVEN_BITS).toLong() shl shift)
            if (b and MORE == 0) return result
            shift += SEVEN
        }
    }

    private fun DataInputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() < 0) throw EOFException()
                left--
            } else {
                left -= skipped
            }
        }
    }

    private fun DataInputStream.skipFully(n: Int) = skipFully(n.toLong())

    private const val METADATA_PROPS = 14
    private const val TYPE_BITS = 3
    private const val TYPE_MASK = 7L
    private const val VARINT = 0
    private const val FIXED64 = 1
    private const val LENGTH = 2
    private const val FIXED32 = 5
    private const val SEVEN = 7
    private const val SEVEN_BITS = 0x7F
    private const val MORE = 0x80
}

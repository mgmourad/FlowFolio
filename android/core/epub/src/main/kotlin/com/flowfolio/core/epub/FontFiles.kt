// Offsets and sizes below are the published layouts of the sfnt and WOFF 1 formats and of the two obfuscation
// algorithms, read as the specifications give them (ADR-0030).
@file:Suppress("MagicNumber")

package com.flowfolio.core.epub

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * A book's font files, made safe to hand to the system (ADR-0030): de-obfuscated (IDPF and Adobe), WOFF 1 turned back
 * into a plain font, and checked to be a TrueType or OpenType font of a sane size.
 */
internal object FontFiles {
    const val MAX_FONT_BYTES = 16 shl 20

    /** [bytes] as a plain TrueType or OpenType font (unwrapping WOFF 1), or null when it is not one. */
    fun plainFont(bytes: ByteArray): ByteArray? =
        (if (bytes.startsWith(WOFF)) Woff.toSfnt(bytes) else bytes)?.takeIf(::isSfnt)

    /** IDPF font obfuscation: the first 1040 bytes XORed with the SHA-1 of the unique identifier, spaces removed. */
    fun deobfuscateIdpf(bytes: ByteArray, uniqueIdentifier: String): ByteArray {
        val id = uniqueIdentifier.filterNot { it == ' ' || it == '\t' || it == '\n' || it == '\r' }
        return xor(bytes, MessageDigest.getInstance("SHA-1").digest(id.toByteArray()), 1040)
    }

    /** Adobe font obfuscation: the first 1024 bytes XORed with the 16 bytes of the book's UUID. */
    fun deobfuscateAdobe(bytes: ByteArray, key: ByteArray): ByteArray = xor(bytes, key, 1024)

    /** Adobe's key: the 16 bytes of the book's `urn:uuid:` identifier, or null when it has none. */
    fun adobeKey(metadata: EpubMetadata): ByteArray? = (listOfNotNull(metadata.uniqueIdentifier) + metadata.identifiers)
        .firstOrNull { it.trim().startsWith("urn:uuid:", ignoreCase = true) }
        ?.trim()?.drop("urn:uuid:".length)?.filter { it != '-' }
        ?.takeIf { hex -> hex.length == 32 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } }
        ?.let { hex -> ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }

    private fun xor(bytes: ByteArray, key: ByteArray, count: Int): ByteArray {
        val out = bytes.copyOf()
        for (i in 0 until minOf(count, out.size)) out[i] = (out[i].toInt() xor key[i % key.size].toInt()).toByte()
        return out
    }

    /** A TrueType (`00 01 00 00`, `true`) or OpenType (`OTTO`) font, with a table directory that fits the file. */
    fun isSfnt(bytes: ByteArray): Boolean {
        val buf = ByteBuffer.wrap(bytes)
        val tag = if (bytes.size >= SFNT_HEADER) buf.getInt(0) else 0
        val tables = if (bytes.size >= SFNT_HEADER) buf.getShort(4).toInt() and 0xFFFF else 0
        return tag in SFNT_TAGS && tables in 1..MAX_TABLES && SFNT_HEADER + tables * SFNT_ENTRY <= bytes.size
    }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    const val MAX_TABLES = 200
    const val SFNT_HEADER = 12
    const val SFNT_ENTRY = 16
    private val SFNT_TAGS = setOf(0x00010000, 0x74727565, 0x4F54544F) // 1.0, 'true', 'OTTO'
    private val WOFF = "wOFF".toByteArray()
}

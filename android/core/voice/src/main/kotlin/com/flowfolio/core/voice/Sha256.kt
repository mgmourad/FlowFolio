package com.flowfolio.core.voice

import java.io.File
import java.security.MessageDigest

/** SHA-256 digests for verifying downloaded voice packs. */
object Sha256 {
    fun ofBytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Hashes [file] by streaming it in chunks, so a ~150 MB archive never sits in memory at once. */
    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(CHUNK_BYTES)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val CHUNK_BYTES = 64 * 1024
}

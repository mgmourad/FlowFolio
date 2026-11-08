package com.flowfolio.core.voice

import java.io.File
import java.security.MessageDigest

/** SHA-256 digests for verifying downloaded voice packs. */
object Sha256 {
    fun ofBytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun of(file: File): String = ofBytes(file.readBytes())
}

package com.flowfolio.core.voice

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoicePackTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val pack = VoicePack(
        archiveUrl = "https://example.invalid/kokoro.zip",
        archiveSha256 = "abc",
        requiredFiles = listOf("model.int8.onnx", "voices.bin", "tokens.txt")
    )

    @Test
    fun `isReady when every required file is present and non-empty`() {
        val dir = tmp.newFolder("pack")
        pack.requiredFiles.forEach { File(dir, it).writeText("x") }
        assertTrue(pack.isReady(dir))
    }

    @Test
    fun `not ready when a required file is missing`() {
        val dir = tmp.newFolder("pack")
        File(dir, "model.int8.onnx").writeText("x")
        File(dir, "voices.bin").writeText("x")
        // tokens.txt missing
        assertFalse(pack.isReady(dir))
    }

    @Test
    fun `not ready when a required file is empty`() {
        val dir = tmp.newFolder("pack")
        pack.requiredFiles.forEach { File(dir, it).writeText("x") }
        File(dir, "tokens.txt").writeText("")
        assertFalse(pack.isReady(dir))
    }

    @Test
    fun `checksum valid only for the exact digest`() {
        val archive = tmp.newFile("p.zip")
        archive.writeText("content")
        val digest = Sha256.of(archive)
        val pack = VoicePack("https://example.invalid/kokoro.zip", digest, emptyList())
        assertTrue(pack.hasValidChecksum(archive))
        val wrong = VoicePack("https://example.invalid/kokoro.zip", "0".repeat(64), emptyList())
        assertFalse(wrong.hasValidChecksum(archive))
    }
}

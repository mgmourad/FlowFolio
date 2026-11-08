package com.flowfolio.core.voice

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Where a voice pack is in its download lifecycle, for the reader's first-use prompt. */
sealed interface VoicePackState {
    data object Idle : VoicePackState
    data class Downloading(val progress: Float) : VoicePackState
    data object Ready : VoicePackState
    data class Failed(val message: String) : VoicePackState
}

/**
 * Downloads, checksum-verifies and extracts a [VoicePack] into [destDir] (app storage), reporting
 * progress through [state]. Call [ensureDownloaded] from a coroutine; it is idempotent (a ready pack
 * returns immediately).
 */
class VoicePackDownloader(private val pack: VoicePack, private val destDir: File) {
    private val _state = MutableStateFlow<VoicePackState>(VoicePackState.Idle)
    val state: StateFlow<VoicePackState> = _state.asStateFlow()

    suspend fun ensureDownloaded() = withContext(Dispatchers.IO) {
        if (pack.isReady(destDir)) {
            _state.value = VoicePackState.Ready
            return@withContext
        }
        val archive = File(destDir, "voice-pack.zip")
        try {
            _state.value = VoicePackState.Downloading(0f)
            download(pack.archiveUrl, archive) { p -> _state.value = VoicePackState.Downloading(p) }
            if (!pack.hasValidChecksum(archive)) {
                archive.delete()
                throw IOException("Voice pack checksum mismatch")
            }
            ZipExtract.extract(archive, destDir)
            archive.delete()
            _state.value = if (pack.isReady(destDir)) {
                VoicePackState.Ready
            } else {
                VoicePackState.Failed("Voice pack is incomplete")
            }
        } catch (e: Exception) {
            archive.delete()
            _state.value = VoicePackState.Failed(e.message ?: "Voice pack download failed")
        }
    }

    private fun download(url: String, dest: File, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

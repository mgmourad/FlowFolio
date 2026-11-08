package com.flowfolio.core.voice

import java.io.File
import java.io.IOException
import java.io.InputStream
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
        } catch (e: IOException) {
            archive.delete()
            _state.value = VoicePackState.Failed(e.message ?: "Voice pack download failed")
        }
    }

    private fun download(url: String, dest: File, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            copyWithProgress(connection.inputStream, dest, connection.contentLengthLong, onProgress)
        } finally {
            connection.disconnect()
        }
    }

    private fun copyWithProgress(input: InputStream, dest: File, total: Long, onProgress: (Float) -> Unit) {
        val buffer = ByteArray(BUFFER_BYTES)
        var read = 0L
        dest.outputStream().use { output ->
            input.use { source ->
                var n = source.read(buffer)
                while (n >= 0) {
                    output.write(buffer, 0, n)
                    read += n
                    reportProgress(read, total, onProgress)
                    n = source.read(buffer)
                }
            }
        }
    }

    private fun reportProgress(read: Long, total: Long, onProgress: (Float) -> Unit) {
        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}

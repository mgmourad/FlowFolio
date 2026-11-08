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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * returns immediately) and serialized, so a double-tap cannot start two concurrent downloads.
 */
class VoicePackDownloader(private val pack: VoicePack, private val destDir: File) {
    private val _state = MutableStateFlow<VoicePackState>(VoicePackState.Idle)
    val state: StateFlow<VoicePackState> = _state.asStateFlow()

    private val mutex = Mutex()

    suspend fun ensureDownloaded() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (pack.isReady(destDir) && pack.lexiconReady(destDir)) {
                _state.value = VoicePackState.Ready
                return@withContext
            }
            val archive = File(destDir, pack.archive.fileName)
            try {
                _state.value = VoicePackState.Downloading(0f)
                download(pack.archiveUrl, archive) { p -> _state.value = VoicePackState.Downloading(p) }
                if (!pack.hasValidChecksum(archive)) {
                    archive.delete()
                    throw IOException("Voice pack checksum mismatch")
                }
                extract(archive, destDir)
                archive.delete()
                val lexicon = pack.lexicon
                if (lexicon != null && !pack.lexiconReady(destDir)) {
                    val file = File(destDir, lexicon.fileName)
                    download(lexicon.url, file) {}
                    if (Sha256.of(file) != lexicon.sha256) {
                        file.delete()
                        throw IOException("Voice lexicon checksum mismatch")
                    }
                }
                _state.value = if (pack.isReady(destDir) && pack.lexiconReady(destDir)) {
                    VoicePackState.Ready
                } else {
                    VoicePackState.Failed("Voice pack is incomplete")
                }
            } catch (e: IOException) {
                archive.delete()
                _state.value = VoicePackState.Failed(e.message ?: "Voice pack download failed")
            }
        }
    }

    private fun extract(archive: File, dir: File) {
        when (pack.archive.format) {
            VoicePackFormat.ZIP -> ZipExtract.extract(archive, dir)
            VoicePackFormat.TAR_BZ2 -> TarBz2Extract.extract(archive, dir, pack.requiredFiles.toSet())
        }
    }

    private fun download(url: String, dest: File, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", AGENT)
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
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val AGENT = "FlowFolio (Android; https://github.com/mgmourad/FlowFolio)"
    }
}

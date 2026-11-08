package com.flowfolio.core.voice

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    data class Installing(val progress: Float = 0f) : VoicePackState
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
            val part = File(destDir, "${pack.archive.fileName}.part")
            try {
                if (!pack.isReady(destDir)) {
                    fetchAndExtractArchive(archive, part)
                }
                val lexicon = pack.lexicon
                if (lexicon != null && !pack.lexiconReady(destDir)) {
                    ensureLexicon(lexicon)
                }
                _state.value = if (pack.isReady(destDir) && pack.lexiconReady(destDir)) {
                    VoicePackState.Ready
                } else {
                    VoicePackState.Failed("Voice pack is incomplete")
                }
            } catch (e: CancellationException) {
                part.delete()
                if (_state.value !is VoicePackState.Ready) _state.value = VoicePackState.Idle
                throw e
            } catch (e: IOException) {
                part.delete()
                archive.delete()
                _state.value = VoicePackState.Failed(e.message ?: "Voice pack download failed")
            }
        }
    }

    private suspend fun fetchAndExtractArchive(archive: File, part: File) {
        if (!archive.isFile || !pack.hasValidChecksum(archive)) {
            _state.value = VoicePackState.Downloading(0f)
            downloadWithRetry(pack.archiveUrl, archive, part) { p ->
                _state.value = VoicePackState.Downloading(p)
            }
            if (!pack.hasValidChecksum(archive)) {
                archive.delete()
                throw IOException("Voice pack checksum mismatch")
            }
        }
        _state.value = VoicePackState.Installing(0f)
        extract(archive, destDir) { p -> _state.value = VoicePackState.Installing(p) }
        archive.delete()
    }

    private suspend fun ensureLexicon(lexicon: VoiceLexicon) {
        val file = File(destDir, lexicon.fileName)
        val part = File(destDir, "${lexicon.fileName}.part")
        val shared = destDir.parentFile?.let { File(it, lexicon.fileName) }?.takeIf { it != file }
        if (shared != null && shared.isFile && Sha256.of(shared) == lexicon.sha256) {
            shared.copyTo(file, overwrite = true)
            return
        }
        try {
            downloadWithRetry(lexicon.url, file, part) { p ->
                _state.value = VoicePackState.Downloading(p)
            }
        } finally {
            part.delete()
        }
        if (Sha256.of(file) != lexicon.sha256) {
            file.delete()
            throw IOException("Voice lexicon checksum mismatch")
        }
        if (shared != null) {
            runCatching { file.copyTo(shared, overwrite = true) }
        }
    }

    private fun extract(archive: File, dir: File, onProgress: (Float) -> Unit) {
        when (pack.archive.format) {
            VoicePackFormat.ZIP -> ZipExtract.extract(archive, dir, onProgress)
            VoicePackFormat.TAR_BZ2 -> TarBz2Extract.extract(archive, dir, pack.requiredFiles.toSet(), onProgress)
        }
    }

    private suspend fun downloadWithRetry(url: String, dest: File, part: File, onProgress: (Float) -> Unit) {
        var lastError: IOException? = null
        for (attempt in 1..MAX_DOWNLOAD_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            try {
                downloadOnce(url, part, onProgress)
                dest.delete()
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
                return
            } catch (e: CancellationException) {
                part.delete()
                throw e
            } catch (e: IOException) {
                part.delete()
                lastError = e
                if (attempt < MAX_DOWNLOAD_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
            }
        }
        throw lastError ?: IOException("Voice pack download failed")
    }

    private suspend fun downloadOnce(url: String, dest: File, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", AGENT)
        try {
            val code = connection.responseCode
            if (code !in HTTP_OK_START..HTTP_OK_END) {
                throw IOException("Server returned HTTP $code")
            }
            copyWithProgress(connection.inputStream, dest, connection.contentLengthLong, onProgress)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun copyWithProgress(input: InputStream, dest: File, total: Long, onProgress: (Float) -> Unit) {
        val ctx = currentCoroutineContext()
        val buffer = ByteArray(BUFFER_BYTES)
        var read = 0L
        dest.outputStream().use { output ->
            input.use { source ->
                var n = source.read(buffer)
                while (n >= 0) {
                    ctx.ensureActive()
                    output.write(buffer, 0, n)
                    read += n
                    reportProgress(read, total, onProgress)
                    n = source.read(buffer)
                }
            }
        }
        if (total > 0L && read != total) {
            throw IOException("Connection closed early ($read of $total bytes)")
        }
    }

    private fun reportProgress(read: Long, total: Long, onProgress: (Float) -> Unit) {
        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_DOWNLOAD_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 600L
        const val HTTP_OK_START = 200
        const val HTTP_OK_END = 299
        const val AGENT = "FlowFolio (Android; https://github.com/mgmourad/FlowFolio)"
    }
}

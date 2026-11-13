@file:Suppress("ThrowsCount", "NestedBlockDepth") // one retry loop and one copy loop, read top to bottom

package com.flowfolio.core.voice

import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLongArray
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * One file of a downloadable pack: where it is, the SHA-256 and size of what is served there, and the name it is
 * kept under. A [gzipped] file is served compressed and unpacked as it lands (native zlib, seconds even for 100 MB).
 */
class PackFile(val name: String, val url: String, val sha256: String, val size: Long, val gzipped: Boolean = false)

/**
 * The downloader every optional pack uses (2026-11-12, after the first-launch downloads failed and crawled on the
 * owner's phone):
 * - all of a pack's files at once, one connection each;
 * - a dropped connection resumes from the bytes already on disk (HTTP Range), up to [ATTEMPTS] times, instead of
 *   failing the whole pack;
 * - progress reported at most once per percent, not for every 64 KB read;
 * - nothing to unpack afterwards but a gzip stream; the voices used to come as a bzip2 archive that took minutes to
 *   unpack in Java while the screen said 100%;
 * - every failure logged under [TAG], so the next one can be diagnosed from logcat.
 *
 * A file already in place with the right size is kept. Served bytes are checked against their pinned SHA-256 before
 * anything is renamed into place.
 */
object PackFetch {
    const val TAG = "FlowFolioFetch"

    /** Fetches every file in [files] into [dir], reporting the share of all bytes received (0..1) to [onProgress]. */
    suspend fun fetchAll(files: List<PackFile>, dir: File, onProgress: (Float) -> Unit) = coroutineScope {
        dir.mkdirs()
        val total = files.sumOf { it.size }.coerceAtLeast(1L)
        val received = AtomicLongArray(files.size)
        var lastPercent = -1
        // The files arrive on several threads at once: the sum and the last percent are kept under one lock.
        val report = { i: Int, bytes: Long ->
            received.set(i, bytes)
            synchronized(received) {
                var sum = 0L
                for (k in files.indices) sum += received.get(k)
                val percent = (sum * PERCENT / total).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(sum.toFloat() / total)
                }
            }
        }
        files.mapIndexed { i, f ->
            async {
                if (isInPlace(f, dir)) report(i, f.size) else fetch(f, dir) { report(i, it) }
            }
        }.awaitAll()
        onProgress(1f)
    }

    /** Whether [file] is already in place: whole, since parts are renamed in only when whole. */
    fun isInPlace(file: PackFile, dir: File): Boolean = File(dir, file.name).let { it.isFile && it.length() > 0L } &&
        (file.gzipped || File(dir, file.name).length() == file.size)

    private suspend fun fetch(file: PackFile, dir: File, onBytes: (Long) -> Unit) {
        val served = File(dir, file.name + if (file.gzipped) ".gz.part" else ".part")
        var lastError: IOException? = null
        for (attempt in 1..ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            try {
                download(file.url, served, file.size, onBytes)
                check(served.length() == file.size) { "${file.name}: ${served.length()} of ${file.size} bytes" }
                if (sha256(served) != file.sha256) {
                    served.delete()
                    throw IOException("${file.name} did not arrive whole (checksum)")
                }
                place(file, served, dir)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                Log.w(TAG, "${file.name}: attempt $attempt failed: ${e.message}")
                if (attempt < ATTEMPTS) delay(RETRY_MS * attempt)
            } catch (e: IllegalStateException) {
                lastError = IOException(e.message, e)
                Log.w(TAG, "${file.name}: attempt $attempt failed: ${e.message}")
                if (attempt < ATTEMPTS) delay(RETRY_MS * attempt)
            }
        }
        throw lastError ?: IOException("${file.name} could not be downloaded")
    }

    /** Appends to [part] from where it stopped; starts over when the server ignores the range. */
    private suspend fun download(url: String, part: File, size: Long, onBytes: (Long) -> Unit) {
        var have = if (part.isFile) part.length() else 0L
        if (have >= size) {
            onBytes(have)
            return
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", AGENT)
            if (have > 0L) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = connection.responseCode
            if (code !in HTTP_OK..HTTP_PARTIAL) throw IOException("HTTP $code for $url")
            val append = code == HTTP_PARTIAL && have > 0L
            if (!append) have = 0L
            val ctx = currentCoroutineContext()
            connection.inputStream.use { input ->
                java.io.FileOutputStream(part, append).buffered(BUFFER).use { out ->
                    val buffer = ByteArray(BUFFER)
                    var n = input.read(buffer)
                    while (n >= 0) {
                        ctx.ensureActive()
                        out.write(buffer, 0, n)
                        have += n
                        onBytes(have)
                        n = input.read(buffer)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun place(file: PackFile, served: File, dir: File) {
        val target = File(dir, file.name)
        if (file.gzipped) {
            val unpacked = File(dir, file.name + ".part")
            GZIPInputStream(served.inputStream().buffered(BUFFER), BUFFER).use { input ->
                unpacked.outputStream().buffered(BUFFER).use { input.copyTo(it, BUFFER) }
            }
            served.delete()
            target.delete()
            if (!unpacked.renameTo(target)) throw IOException("could not place ${file.name}")
        } else {
            target.delete()
            if (!served.renameTo(target)) throw IOException("could not place ${file.name}")
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val ATTEMPTS = 5
    private const val RETRY_MS = 1_500L
    private const val BUFFER = 256 * 1024
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val HTTP_OK = 200
    private const val HTTP_PARTIAL = 206
    private const val PERCENT = 100
    private const val AGENT = "FlowFolio (Android; https://github.com/mgmourad/FlowFolio)"
}

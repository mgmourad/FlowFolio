package com.flowfolio.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Where a downloadable resource stands (TM2): not on the phone, coming in ([fraction]), ready, or failed. */
sealed interface ResourceState {
    data object Missing : ResourceState

    data class Downloading(val fraction: Float) : ResourceState

    data object Ready : ResourceState

    data object Failed : ResourceState
}

/**
 * Princeton WordNet for Define, downloaded on request instead of bundled (TM2, ADR-0057): the app no longer carries
 * its 8 MB archive. The gzipped database comes from FlowFolio's `resources-v1` release, is checked against its pinned
 * SHA-256 and unpacked to where [WordNetStore] reads it. Define keeps Wiktionary and Wikipedia without it.
 */
@Singleton
class WordNetPack @Inject constructor(@ApplicationContext private val context: Context) {
    private val target = WordNetStore.dbFileIn(context.filesDir)
    private val _state = MutableStateFlow(if (target.isFile) ResourceState.Ready else ResourceState.Missing)
    val state: StateFlow<ResourceState> = _state.asStateFlow()

    suspend fun download() = withContext(Dispatchers.IO) {
        if (target.isFile) {
            _state.value = ResourceState.Ready
            return@withContext
        }
        target.parentFile?.mkdirs()
        val gz = File(target.path + ".gz.part")
        val ok = runCatching {
            fetch(gz)
            check(sha256(gz) == SHA256) { "wordnet.db.gz did not arrive whole" }
            val part = File(target.path + ".part")
            GZIPInputStream(gz.inputStream()).use { input -> part.outputStream().use { input.copyTo(it) } }
            check(part.renameTo(target))
        }.isSuccess
        gz.delete()
        _state.value = if (ok && target.isFile) ResourceState.Ready else ResourceState.Failed
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        target.delete()
        _state.value = ResourceState.Missing
    }

    private fun fetch(out: File) {
        val connection = URL(URL_STRING).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            connection.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buffer = ByteArray(BUFFER)
                    var total = 0L
                    var n = input.read(buffer)
                    while (n >= 0) {
                        o.write(buffer, 0, n)
                        total += n
                        _state.value = ResourceState.Downloading((total.toFloat() / SIZE).coerceAtMost(1f))
                        n = input.read(buffer)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
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

    companion object {
        const val URL_STRING = "https://github.com/mgmourad/FlowFolio/releases/download/resources-v1/wordnet.db.gz"
        const val SHA256 = "1c0c65f35a105c0fdfab4881b632437006191550e4c6ecf0b31f1c6e677e1ee1"
        const val SIZE = 8052396L
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER = 64 * 1024
    }
}

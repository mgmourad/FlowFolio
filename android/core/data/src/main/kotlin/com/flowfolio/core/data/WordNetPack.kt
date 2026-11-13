package com.flowfolio.core.data

import android.content.Context
import com.flowfolio.core.voice.PackFetch
import com.flowfolio.core.voice.PackFile
import dagger.hilt.android.qualifiers.ApplicationContext
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
        val file = PackFile(target.name, URL_STRING, SHA256, SIZE, gzipped = true)
        val ok = runCatching {
            PackFetch.fetchAll(listOf(file), target.parentFile!!) { _state.value = ResourceState.Downloading(it) }
        }.onFailure { android.util.Log.w(PackFetch.TAG, "WordNet failed", it) }.isSuccess
        _state.value = if (ok && target.isFile) ResourceState.Ready else ResourceState.Failed
    }

    /** Reads the state from disk again (the Full edition has just put it in place). */
    fun recheck() {
        if (target.isFile) _state.value = ResourceState.Ready
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        target.delete()
        _state.value = ResourceState.Missing
    }

    companion object {
        const val URL_STRING = "https://github.com/mgmourad/FlowFolio/releases/download/resources-v1/wordnet.db.gz"
        const val SHA256 = "1c0c65f35a105c0fdfab4881b632437006191550e4c6ecf0b31f1c6e677e1ee1"
        const val SIZE = 8052396L
    }
}

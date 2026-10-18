package com.flowfolio.feature.reader.cache

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One zoom tile: the part of [page] rendered at [pageWidthPx] x [pageHeightPx] (the page at zoom [level]) whose
 * top-left is tile ([tx], [ty]) of [TILE_PX] squares. Edge tiles are cut to the page ([widthPx] x [heightPx]).
 */
data class TileKey(
    val page: Int,
    val level: Float,
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val tx: Int,
    val ty: Int,
    val night: Boolean = false
) {
    val widthPx: Int get() = minOf(TILE_PX, pageWidthPx - tx * TILE_PX)
    val heightPx: Int get() = minOf(TILE_PX, pageHeightPx - ty * TILE_PX)

    /** 16-bit tiles (ADR-0013): two bytes per pixel. */
    val bytes: Long get() = widthPx.toLong() * heightPx * 2

    companion object {
        const val TILE_PX = 512
    }
}

fun interface TileSource {
    suspend fun render(key: TileKey): ImageBitmap?
}

/**
 * Zoom tiles, held only while the reader is zoomed. [request] takes the tiles to show, most important first; missing
 * ones are rendered in that order, and tiles beyond [maxTiles] are dropped, oldest first, never one that is wanted.
 * Dropped images go to [onEvicted] so their GPU memory can be released promptly (see the page cache).
 */
class TileCache(
    private val source: TileSource,
    private val scope: CoroutineScope,
    private val maxTiles: Int,
    private val onEvicted: (ImageBitmap) -> Unit = {}
) {
    private val lru = LinkedHashMap<TileKey, ImageBitmap>(maxTiles, LOAD_FACTOR, true)
    private val _tiles = MutableStateFlow<Map<TileKey, ImageBitmap>>(emptyMap())
    private var job: Job? = null

    val tiles: StateFlow<Map<TileKey, ImageBitmap>> = _tiles.asStateFlow()

    fun request(wanted: List<TileKey>) {
        job?.cancel()
        val keep = wanted.take(maxTiles)
        job = scope.launch {
            val wantedSet = keep.toSet()
            for (key in keep) {
                val held = synchronized(lru) { lru[key] } != null // the lookup also marks it recently used
                val image = if (held) null else source.render(key)
                if (image != null) {
                    synchronized(lru) { lru[key] = image }
                    trim(wantedSet)
                    publish()
                }
            }
        }
    }

    fun clear() {
        job?.cancel()
        synchronized(lru) {
            lru.values.forEach(onEvicted)
            lru.clear()
        }
        publish()
    }

    private fun trim(wanted: Set<TileKey>) = synchronized(lru) {
        val iterator = lru.entries.iterator()
        while (lru.size > maxTiles && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in wanted) {
                iterator.remove()
                onEvicted(entry.value)
            }
        }
    }

    private fun publish() {
        _tiles.value = synchronized(lru) { HashMap(lru) }
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}

package com.flowfolio.feature.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.roundToInt

/**
 * Islands kept on disk for one book and layout (ADR-0021): their measured sizes, one file per chapter, and their
 * images as PNGs. Equations are stored in black and tinted when loaded ([ink] null); SVG and tables keep their colours
 * and are stored per paper ink. Lives in the app's cache directory, which Android may clear. Thread-safe; does disk
 * work, so call it off the main thread.
 */
internal class IslandStore private constructor(val dir: File, val bodyPx: Float) {
    constructor(root: File, bookId: String, bodyPx: Float, widthPx: Int) :
        this(File(root, "$bookId/v$VERSION-${bodyPx.roundToInt()}-$widthPx"), bodyPx)

    /** Names of the files already written, read once. */
    private val present: MutableSet<String> = HashSet<String>().also { names ->
        dir.mkdirs()
        dir.setLastModified(System.currentTimeMillis())
        dir.list()?.let { names.addAll(it) }
    }

    /** Every size stored for this layout. */
    fun sizes(): Map<Int, IslandBox> {
        val files = synchronized(present) { present.filter { it.startsWith(SIZES) } }
        return files.flatMap { name ->
            runCatching { File(dir, name).readLines() }.getOrDefault(emptyList()).mapNotNull(::parse)
        }.associateBy { it.id }
    }

    /** Keeps [chapter]'s sizes (all of its islands, measured). */
    fun saveSizes(chapter: Int, boxes: Collection<IslandBox>) {
        write("$SIZES$chapter.txt") { file ->
            file.writeText(boxes.joinToString("\n") { "${it.id} ${it.w} ${it.h} ${it.baseline}" })
        }
    }

    fun has(id: Int, ink: Int?): Boolean = synchronized(present) { nameOf(id, ink) in present }

    /** The stored image, mutable (equations are tinted in place), or null when there is none. */
    fun load(id: Int, ink: Int?): Bitmap? {
        if (!has(id, ink)) return null
        val options = BitmapFactory.Options().apply { inMutable = true }
        return runCatching { BitmapFactory.decodeFile(File(dir, nameOf(id, ink)).path, options) }.getOrNull()
    }

    fun save(id: Int, ink: Int?, bmp: Bitmap) {
        write(nameOf(id, ink)) { file ->
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        }
    }

    /** Writes through a temporary file, so a reader never sees half a file. */
    private fun write(name: String, block: (File) -> Unit) {
        val tmp = File(dir, "$name.tmp")
        val ok = runCatching { block(tmp) }.isSuccess && tmp.renameTo(File(dir, name))
        if (ok) synchronized(present) { present += name } else tmp.delete()
    }

    private fun nameOf(id: Int, ink: Int?) = if (ink == null) "$id.png" else "$id-${Integer.toHexString(ink)}.png"

    private fun parse(line: String): IslandBox? {
        val parts = line.split(' ').mapNotNull(String::toIntOrNull)
        // id, width, height, baseline
        return parts.takeIf { it.size == SIZE_FIELDS }?.let { (id, w, h) -> IslandBox(id, w, h, parts.last(), 0, 0) }
    }

    companion object {
        /**
         * Raised whenever stored islands could no longer match the book (island ids come from parse order) or are drawn
         * differently (2: maths inside tables in the paper's ink).
         */
        private const val VERSION = 2
        private const val SIZES = "sizes-"
        private val LAYOUT = Regex("v$VERSION-([0-9]+)-([0-9]+)")
        private const val SIZE_FIELDS = 4

        /** Ignored for PNG, which is lossless. */
        private const val PNG_QUALITY = 100

        /** The whole island cache, all books and layouts. */
        const val CAP_BYTES = 200L * 1024 * 1024

        /**
         * Another text size already stored for [bookId] (M3: islands per layout), nearest [bodyPx], or null: its
         * equations, scaled, stand in while this size's are drawn. Equations do not wrap, so any column width will do.
         */
        fun nearest(root: File, bookId: String, bodyPx: Float, except: File): IslandStore? =
            File(root, bookId).listFiles().orEmpty()
                .filter { it != except && it.isDirectory }
                .mapNotNull { d -> LAYOUT.matchEntire(d.name)?.let { d to it.groupValues[1].toFloat() } }
                .filter { (d, _) -> d.list()?.any { it.endsWith(".png") } == true }
                .minByOrNull { (_, px) -> kotlin.math.abs(px - bodyPx) }
                ?.let { (d, px) -> IslandStore(d, px) }

        /** Deletes the least recently opened layouts until the cache fits [capBytes]; [keep] (in use) stays. */
        fun trim(root: File, keep: File, capBytes: Long = CAP_BYTES) {
            val layouts = root.listFiles().orEmpty().flatMap { it.listFiles().orEmpty().asList() }
            val sizes = layouts.associateWith { d -> d.listFiles().orEmpty().sumOf { it.length() } }
            var total = sizes.values.sum()
            for (d in layouts.filter { it != keep }.sortedBy { it.lastModified() }) {
                if (total <= capBytes) break
                d.deleteRecursively()
                total -= sizes.getValue(d)
            }
        }
    }
}

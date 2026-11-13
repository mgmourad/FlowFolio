package com.flowfolio.core.data

import android.content.Context
import android.util.Log
import com.flowfolio.core.data.ocr.OcrPack
import com.flowfolio.core.voice.PackFetch
import com.flowfolio.core.voice.PackFile
import com.flowfolio.core.voice.VoicePacks
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The Full edition (2026-11-12, owner): the same app with the three optional packs inside it, under
 * `assets/packs/<group>/<served file name>`, exactly as `resources-v1` and `ocr-models-v1` serve them. On first start
 * they are copied to where the downloads would have put them, so nothing is fetched. The Essential edition has no
 * `assets/packs` and downloads them on request.
 */
object BundledPacks {
    const val ROOT = "packs"
    const val OCR = "ocr"
    const val VOICE = "voice"
    const val DICTIONARY = "dictionary"

    /** True in the Full edition. */
    fun present(context: Context): Boolean = runCatching { context.assets.list(ROOT).orEmpty().isNotEmpty() }
        .getOrDefault(false)

    /** Each group's files and the folder they go to. */
    fun groups(context: Context, voiceDir: File): List<Triple<String, List<PackFile>, File>> = listOf(
        Triple(OCR, OcrPack.FILES, OcrPack.dirIn(context.filesDir)),
        Triple(VOICE, VoicePacks.kokoroMultiLang.files.orEmpty(), voiceDir),
        Triple(
            DICTIONARY,
            listOf(
                PackFile(
                    WordNetStore.dbFileIn(context.filesDir).name,
                    WordNetPack.URL_STRING,
                    WordNetPack.SHA256,
                    WordNetPack.SIZE,
                    gzipped = true
                )
            ),
            WordNetStore.dbFileIn(context.filesDir).parentFile!!
        )
    )

    /** Copies (or unpacks) every bundled file not yet in place. Safe to call on every start: present files stay. */
    fun install(context: Context, voiceDir: File) {
        if (!present(context)) return
        for ((group, files, dir) in groups(context, voiceDir)) {
            dir.mkdirs()
            for (f in files) {
                if (PackFetch.isInPlace(f, dir)) continue
                val asset = "$ROOT/$group/${f.url.substringAfterLast('/')}"
                val target = File(dir, f.name)
                val part = File(dir, f.name + ".part")
                runCatching {
                    context.assets.open(asset).use { raw ->
                        val input = if (f.gzipped) GZIPInputStream(raw.buffered(BUFFER), BUFFER) else raw
                        part.outputStream().buffered(BUFFER).use { input.copyTo(it, BUFFER) }
                    }
                    target.delete()
                    check(part.renameTo(target)) { "could not place ${f.name}" }
                }.onFailure {
                    part.delete()
                    Log.w(PackFetch.TAG, "bundled $asset not installed", it)
                }
            }
        }
    }

    private const val BUFFER = 256 * 1024
}

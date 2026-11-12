package com.flowfolio.core.data.flow

import com.flowfolio.core.conversion.ocr.OcrCache
import com.flowfolio.core.conversion.ocr.OcrLine
import com.flowfolio.core.conversion.ocr.OcrPage
import com.flowfolio.core.conversion.ocr.OcrResult
import com.flowfolio.core.conversion.ocr.PixelBox
import java.io.File

/**
 * Each scanned page's OCR, one small file per page in [dir] (TM1): a conversion stopped part-way, paused for another
 * book or stopped by Android, resumes from the pages it has read. Every file carries [stamp] (the engine version), and
 * a file with another stamp is ignored, so a newer engine never reuses older readings. The folder is deleted once the
 * book converts.
 */
class FileOcrCache(private val dir: File, private val stamp: String) : OcrCache {
    override fun get(index: Int): OcrResult? = runCatching {
        val file = fileOf(index).takeIf { it.isFile } ?: return null
        val lines = file.readLines()
        val head = lines.firstOrNull()?.split(' ') ?: return null
        if (head.size < HEAD_FIELDS || head[0] != MAGIC || head[1] != stamp) return null
        val scale = head[2].toFloat()
        val rendered = head[3] == "1"
        val engine = head[4]
        val read = lines.drop(1).filter { it.isNotEmpty() }.map(::lineOf)
        OcrResult(if (engine == NONE) null else OcrPage(read, engine), scale, rendered)
    }.getOrNull()

    override fun put(index: Int, result: OcrResult) {
        runCatching {
            dir.mkdirs()
            val page = result.page
            val text = buildString {
                append("$MAGIC $stamp ${result.scale} ${if (result.rendered) 1 else 0} ${page?.engine ?: NONE}\n")
                page?.lines?.forEach { l ->
                    val b = l.box
                    append("${b.left} ${b.top} ${b.right} ${b.bottom} ${l.confidence}\t")
                    append(l.text.replace('\n', ' ').replace('\t', ' ')).append('\n')
                }
            }
            val tmp = File(dir, "${fileOf(index).name}.tmp")
            tmp.writeText(text)
            tmp.renameTo(fileOf(index))
        }
    }

    private fun fileOf(index: Int) = File(dir, "p%05d.ocr".format(index))

    private fun lineOf(row: String): OcrLine {
        val (numbers, text) = row.split('\t', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val n = numbers.split(' ')
        return OcrLine(text, PixelBox(n[0].toInt(), n[1].toInt(), n[2].toInt(), n[3].toInt()), n[4].toFloat())
    }

    private companion object {
        const val MAGIC = "FFOCR1"
        const val NONE = "-"
        const val HEAD_FIELDS = 5
    }
}

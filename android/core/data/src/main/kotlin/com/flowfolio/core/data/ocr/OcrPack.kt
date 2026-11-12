package com.flowfolio.core.data.ocr

import android.content.Context
import com.flowfolio.core.conversion.ocr.OnnxMetadata
import com.flowfolio.core.conversion.ocr.PaddleOcr
import com.flowfolio.core.conversion.ocr.ScannedPageReader
import com.flowfolio.core.ocr.TesseractOcr
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Where the scanned-books pack stands. */
sealed interface OcrPackState {
    data object Missing : OcrPackState

    data class Downloading(val fraction: Float) : OcrPackState

    data object Ready : OcrPackState

    data object Failed : OcrPackState
}

/**
 * The scanned-books pack (ADR-0054): PaddleOCR's detector and its Latin and Arabic recognisers, plus Tesseract's
 * Arabic model (`ara.traineddata`), about 51 MB, downloaded when the reader asks for it and checked against pinned
 * SHA-256s. Dynamic Flow's conversion process reads the files directly ([readerIn]), so the pack works in either
 * process.
 *
 * The files come from RapidOCR's ModelScope mirror for now; ADR-0054 moves them to FlowFolio's own releases.
 */
@Singleton
class OcrPack @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir = dirIn(context.filesDir)
    private val _state = MutableStateFlow(if (readyIn(dir)) OcrPackState.Ready else OcrPackState.Missing)
    val state: StateFlow<OcrPackState> = _state.asStateFlow()

    val sizeBytes: Long = FILES.sumOf { it.size }

    suspend fun download() = withContext(Dispatchers.IO) {
        if (readyIn(dir)) {
            _state.value = OcrPackState.Ready
            return@withContext
        }
        dir.mkdirs()
        var done = 0L
        val ok = runCatching {
            for (f in FILES) {
                val target = File(dir, f.name)
                if (!(target.isFile && sha256(target) == f.sha256)) {
                    fetch(f, target) { n -> _state.value = OcrPackState.Downloading((done + n).toFloat() / sizeBytes) }
                }
                done += f.size
            }
        }.isSuccess
        _state.value = if (ok && readyIn(dir)) OcrPackState.Ready else OcrPackState.Failed
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        dir.deleteRecursively()
        _state.value = OcrPackState.Missing
    }

    private fun fetch(file: PackFile, target: File, onBytes: (Long) -> Unit) {
        val part = File(target.path + ".part")
        val connection = URL(file.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            connection.inputStream.use { input -> part.outputStream().use { out -> copy(input, out, onBytes) } }
        } finally {
            connection.disconnect()
        }
        check(sha256(part) == file.sha256) { "${file.name} did not arrive whole" }
        check(part.renameTo(target))
    }

    private fun copy(input: java.io.InputStream, out: java.io.OutputStream, onBytes: (Long) -> Unit) {
        val buffer = ByteArray(BUFFER)
        var total = 0L
        var n = input.read(buffer)
        while (n >= 0) {
            out.write(buffer, 0, n)
            total += n
            onBytes(total)
            n = input.read(buffer)
        }
    }

    /** One file of the pack, pinned by its SHA-256. */
    class PackFile(val name: String, val url: String, val sha256: String, val size: Long)

    companion object {
        private const val BASE = "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/"
        const val DETECTOR = "PP-OCRv6_det_small.onnx"
        const val LATIN = "PP-OCRv6_rec_small.onnx"
        const val ARABIC = "arabic_PP-OCRv5_rec_mobile.onnx"
        const val ARA = "ara.traineddata"

        val FILES = listOf(
            PackFile(
                DETECTOR,
                BASE + "PP-OCRv6/det/$DETECTOR",
                "090f04abcd9d9a7498bc4ebf677e4cb9bdce1fe4197ddb7e529f1ef44e1ff94f",
                9_929_594
            ),
            PackFile(
                LATIN,
                BASE + "PP-OCRv6/rec/$LATIN",
                "6f327246b50388f3c176ae304bd95767ea6dc0c9ae92153ef8cbe210b3c14884",
                21_234_383
            ),
            PackFile(
                ARABIC,
                BASE + "PP-OCRv5/rec/$ARABIC",
                "c1192e632d0baa9146ae5b756a0e635e3dc63c1733737ebfd1629e87144e9295",
                8_023_828
            ),
            PackFile(
                ARA,
                "https://github.com/tesseract-ocr/tessdata_best/raw/main/$ARA",
                "ab9d157d8e38ca00e7e39c7d5363a5239e053f5b0dbdb3167dde9d8124335896",
                12_603_724
            )
        )

        fun dirIn(filesDir: File) = File(filesDir, "ocr")

        /** Whole files are only ever renamed into place, so presence is enough here; downloads check the hashes. */
        fun readyIn(dir: File) = FILES.all { File(dir, it.name).let { f -> f.isFile && f.length() == it.size } }

        /**
         * The converter's reader for scanned pages when the pack is in [filesDir], else null. Arabic pages stay
         * pictures until Tesseract joins (ADR-0054); the Arabic recogniser is used here only to recognise them.
         */
        fun readerIn(filesDir: File, threads: Int): ScannedPageReader? {
            val dir = dirIn(filesDir)
            if (!readyIn(dir)) return null
            val detector = AndroidOnnxModel(File(dir, DETECTOR), threads)
            fun reader(name: String, engine: String, rtl: Boolean) = PaddleOcr(
                detector,
                AndroidOnnxModel(File(dir, name), threads),
                OnnxMetadata.read(File(dir, name))["character"].orEmpty(),
                engine,
                rightToLeft = rtl
            )
            // Arabic is read by Tesseract and checked by PaddleOCR's Arabic model (OcrGate); Tesseract's text is kept.
            return ScannedPageReader(
                reader(LATIN, "paddle-v6s", false),
                reader(ARABIC, "paddle-ar-v5", true),
                TesseractOcr(dir, "ara")
            )
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

        private const val BUFFER = 64 * 1024
        private const val TIMEOUT_MS = 30_000
    }
}

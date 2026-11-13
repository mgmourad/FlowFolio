package com.flowfolio.core.data.ocr

import android.content.Context
import com.flowfolio.core.conversion.ocr.OnnxMetadata
import com.flowfolio.core.conversion.ocr.PaddleOcr
import com.flowfolio.core.conversion.ocr.ScannedPageReader
import com.flowfolio.core.ocr.TesseractOcr
import com.flowfolio.core.voice.PackFetch
import com.flowfolio.core.voice.PackFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
 * The files are mirrored on FlowFolio's GitHub releases (`ocr-models-v1`, ADR-0054), with SHA-256s pinned.
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
        // All four files at once, each resuming where a dropped connection left it (PackFetch, 2026-11-12): one
        // failed read used to fail the whole pack, and the files came one after another.
        val ok = runCatching {
            PackFetch.fetchAll(FILES, dir) { _state.value = OcrPackState.Downloading(it) }
        }.onFailure { android.util.Log.w(PackFetch.TAG, "scanned-books pack failed", it) }.isSuccess
        _state.value = if (ok && readyIn(dir)) OcrPackState.Ready else OcrPackState.Failed
    }

    /** Reads the pack's state from disk again (the Full edition has just put it in place). */
    fun recheck() {
        if (readyIn(dir)) _state.value = OcrPackState.Ready
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        dir.deleteRecursively()
        _state.value = OcrPackState.Missing
    }

    companion object {
        private const val BASE = "https://github.com/mgmourad/FlowFolio/releases/download/ocr-models-v1/"
        const val DETECTOR = "PP-OCRv6_det_small.onnx"
        const val LATIN = "PP-OCRv6_rec_small.onnx"
        const val ARABIC = "arabic_PP-OCRv5_rec_mobile.onnx"
        const val ARA = "ara.traineddata"

        val FILES = listOf(
            PackFile(
                DETECTOR,
                BASE + DETECTOR,
                "090f04abcd9d9a7498bc4ebf677e4cb9bdce1fe4197ddb7e529f1ef44e1ff94f",
                9_929_594
            ),
            PackFile(
                LATIN,
                BASE + LATIN,
                "6f327246b50388f3c176ae304bd95767ea6dc0c9ae92153ef8cbe210b3c14884",
                21_234_383
            ),
            PackFile(
                ARABIC,
                BASE + ARABIC,
                "c1192e632d0baa9146ae5b756a0e635e3dc63c1733737ebfd1629e87144e9295",
                8_023_828
            ),
            PackFile(
                ARA,
                BASE + ARA,
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
    }
}

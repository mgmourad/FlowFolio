package com.flowfolio.spike.pdf

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.flowfolio.spike.pdf.bench.BenchRunner
import com.flowfolio.spike.pdf.bench.OnnxProbe
import com.flowfolio.spike.pdf.bench.TAG
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs measurements on launch. Driven from adb:
 *   am start -n com.flowfolio.spike.pdf/.BenchActivity --es engine pdfium|framework|androidx|quality
 */
class BenchActivity : ComponentActivity() {
    private val lines = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            Text(
                lines.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
            )
        }
        // The app must create these folders itself; folders created by adb are not visible to it.
        listOf("books", "models", "results").forEach { getExternalFilesDir(it) }
        val engine = intent.getStringExtra("engine") ?: "pdfium"
        if (engine == "init") {
            Log.i(TAG, "SPIKE_DONE init")
            finish()
            return
        }
        val bookName = intent.getStringExtra("book") ?: "university-physics-volume-1.pdf"
        val book = File(getExternalFilesDir("books"), bookName)
        val width = windowManager.currentWindowMetrics.bounds.width()
        val runner = BenchRunner(this) { msg -> log(msg) }

        lifecycleScope.launch {
            try {
                log("engine=$engine book=${book.name} width=${width}px")
                val file = withContext(Dispatchers.Default) {
                    when (engine) {
                        "onnx" -> {
                            val result = OnnxProbe(File(getExternalFilesDir(null), "models")) { msg -> log(msg) }.run()
                            File(getExternalFilesDir("results"), "onnx.json").apply { writeText(result.toString(2)) }
                        }

                        else -> {
                            if (!book.exists()) error("missing ${book.absolutePath}; push it with adb first")
                            if (engine ==
                                "quality"
                            ) {
                                runner.runQuality(book, width)
                            } else {
                                runner.runEngine(engine, book, width)
                            }
                        }
                    }
                }
                log("wrote ${file.name}")
                BenchRunner.done(file)
            } catch (t: Throwable) {
                Log.e(TAG, "SPIKE_FAILED", t)
                log("FAILED: $t")
                Log.i(TAG, "SPIKE_DONE failed")
            }
        }
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        runOnUiThread { lines += msg }
    }
}

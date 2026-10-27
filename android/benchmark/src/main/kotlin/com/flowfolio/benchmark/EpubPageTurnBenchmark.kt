package com.flowfolio.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.flowfolio.benchmark.Textbook.turnBack
import com.flowfolio.benchmark.Textbook.turnForward
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Page turns over reflowed EPUB pages in a two-page spread (the Phase 2 exit criterion: curl over reflowable content as
 * smooth as over PDF pages, P50 frame CPU under 4 ms and at most 5% janky frames), without and with the baseline
 * profile. The book is Moby-Dick from the IDPF samples: push it first,
 * `adb push samples/corpus/cache/epub-idpf/moby-dick.epub /data/local/tmp/flowfolio/`.
 */
@RunWith(AndroidJUnit4::class)
class EpubPageTurnBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun turnsNoProfile() = turns(CompilationMode.None())

    @Test
    fun turnsBaselineProfile() = turns(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun turns(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            importNovel()
            openNovel()
        }
    ) {
        repeat(TURNS) { turnForward() }
        repeat(TURNS) { turnBack() }
    }

    /** Imports the novel (a no-op when it is already in the library) and answers the one-time lookup question. */
    private fun MacrobenchmarkScope.importNovel() {
        device.executeShellCommand("am start -W -n $ACTIVITY --es importPath $NOVEL_PATH")
        device.wait(Until.findObject(By.text("Not now")), IMPORT_MS)?.click()
    }

    /** Opens the novel in a spread well inside the book (the page persists, so it is set each time). */
    private fun MacrobenchmarkScope.openNovel() {
        device.executeShellCommand(
            "am start -W -n $ACTIVITY --es openBookId $NOVEL_ID --es layout spread --ei startPage $START_PAGE"
        )
        check(device.wait(Until.hasObject(By.res(PAGE_TAG)), OPEN_MS)) { "the novel did not open; is it pushed?" }
        // The first layout and the pages either side are drawn before turning starts.
        Thread.sleep(SETTLE_MS)
    }

    private companion object {
        const val ITERATIONS = 5
        const val TURNS = 8
        const val ACTIVITY = "$PACKAGE/com.flowfolio.MainActivity"
        const val NOVEL_PATH = "/data/local/tmp/flowfolio/moby-dick.epub"
        const val NOVEL_ID = "81bc079841a38e91a02a7776d04786a2fc311cfd300064e9fc533ce7c54cf7b4"
        const val START_PAGE = 200
        const val PAGE_TAG = "reader_page"
        const val IMPORT_MS = 30_000L
        const val OPEN_MS = 15_000L
        const val SETTLE_MS = 3_000L
    }
}

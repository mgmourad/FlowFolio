package com.flowfolio.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.benchmark.Textbook.importTextbook
import com.flowfolio.benchmark.Textbook.openTextbook
import com.flowfolio.benchmark.Textbook.turnBack
import com.flowfolio.benchmark.Textbook.turnForward
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Page turns in a two-page spread on the unfolded Fold: frame durations and overruns while turning forward and back
 * (the Phase 1 exit criterion "smooth curl on a 900-page PDF"), without and with the baseline profile.
 */
@RunWith(AndroidJUnit4::class)
class PageTurnBenchmark {
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
            importTextbook()
            openTextbook()
        }
    ) {
        repeat(TURNS) { turnForward() }
        repeat(TURNS) { turnBack() }
    }

    private companion object {
        const val ITERATIONS = 5
        const val TURNS = 8
    }
}

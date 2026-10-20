package com.flowfolio.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.benchmark.Textbook.importTextbook
import com.flowfolio.benchmark.Textbook.openTextbook
import com.flowfolio.benchmark.Textbook.turnBack
import com.flowfolio.benchmark.Textbook.turnForward
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the baseline profile: the code a reader runs on the hot paths (cold start to the library, opening a book,
 * turning pages both ways), so it is compiled ahead of time from the first launch. Run on the Fold with
 * `.\gradlew.bat :app:generateBaselineProfile`; the result lands in `app/src/release/generated/baselineProfiles/`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /** Cold start to the library: also the startup profile, which orders the app's code for a faster launch. */
    @Test
    fun startup() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun readerHotPaths() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()
        importTextbook()
        openTextbook()
        repeat(TURNS) { turnForward() }
        repeat(TURNS) { turnBack() }
    }

    private companion object {
        const val TURNS = 6
    }
}

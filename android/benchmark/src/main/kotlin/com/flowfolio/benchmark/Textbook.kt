package com.flowfolio.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The app :benchmark drives: the release-like build, not the `.dev` debug app with the owner's library. */
internal const val PACKAGE = "com.flowfolio"

/**
 * The test textbook (OpenStax University Physics Volume 1, local testing only, CLAUDE.md known issue 10). Push it
 * first: `adb push samples/corpus/cache/university-physics-volume-1.pdf /data/local/tmp/flowfolio/`.
 */
internal object Textbook {
    private const val PATH = "/data/local/tmp/flowfolio/university-physics-volume-1.pdf"
    private const val ID = "93652b69c43197d10dc83438b813b5f7515e61e7ea4336da8636ff902cb4aba8"

    /** A page well inside the book, so turns render real text and figures in both directions. */
    private const val START_PAGE = 100

    private const val ACTIVITY = "$PACKAGE/com.flowfolio.MainActivity"
    private const val PAGE_TAG = "reader_page"
    private const val IMPORT_MS = 30_000L
    private const val OPEN_MS = 10_000L
    private const val SWIPE_STEPS = 30

    /** Swipes run across the lower middle of the page, clear of the bars. */
    private const val SWIPE_HEIGHT = 0.6f

    /** Imports the textbook (a no-op when it is already in the library) and answers the one-time lookup question. */
    fun MacrobenchmarkScope.importTextbook() {
        device.executeShellCommand("am start -W -n $ACTIVITY --es importPath $PATH")
        device.wait(Until.findObject(By.text("Not now")), IMPORT_MS)?.click()
    }

    /** Opens the textbook in a two-page spread at [START_PAGE] and waits for the page. */
    fun MacrobenchmarkScope.openTextbook() {
        device.executeShellCommand(
            "am start -W -n $ACTIVITY --es openBookId $ID --es layout spread --ei startPage $START_PAGE"
        )
        check(device.wait(Until.hasObject(By.res(PAGE_TAG)), OPEN_MS)) { "textbook did not open; is it pushed?" }
    }

    /** One forward turn: a swipe from the right page leftwards (never from the screen edge, the back gesture). */
    fun MacrobenchmarkScope.turnForward() = swipe(fromX = 0.85f, toX = 0.45f)

    /** One backward turn: a swipe on the left page rightwards. */
    fun MacrobenchmarkScope.turnBack() = swipe(fromX = 0.15f, toX = 0.55f)

    private fun MacrobenchmarkScope.swipe(fromX: Float, toX: Float) {
        val w = device.displayWidth
        val y = (device.displayHeight * SWIPE_HEIGHT).toInt()
        device.swipe((w * fromX).toInt(), y, (w * toX).toInt(), y, SWIPE_STEPS)
        Thread.sleep(SETTLE_MS)
    }

    /** Long enough for a turn to finish (450 ms at most) before the next one starts. */
    private const val SETTLE_MS = 700L
}

package com.flowfolio.reader

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pinch-zoom on a real device (two-finger gestures cannot be sent with adb). Needs the debug app with the test
 * textbook imported (CLAUDE.md); skipped otherwise. Pass `-e holdMs 8000` to keep each zoomed state on screen for
 * screenshots taken from the host.
 */
@RunWith(AndroidJUnit4::class)
class ReaderZoomOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val holdMs = InstrumentationRegistry.getArguments().getString("holdMs")?.toLongOrNull() ?: 0L

    @Before
    fun openBook() {
        val context = instrumentation.targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("openBookId", BOOK_ID)
            putExtra("layout", "spread")
        }
        context.startActivity(intent)
        assumeTrue("test textbook not imported", device.wait(Until.hasObject(By.res(PAGE)), TIMEOUT_MS))
        device.waitForIdle()
    }

    @Test
    fun pinchZoomsIn_thenDoubleTapLeavesZoom() {
        device.findObject(By.res(PAGE)).pinchOpen(PINCH_PERCENT, PINCH_SPEED)
        assertNotNull("zoom view should appear", device.wait(Until.findObject(By.res(ZOOM)), TIMEOUT_MS))
        Thread.sleep(holdMs.coerceAtLeast(SETTLE_MS)) // let the tiles render
        val centre = device.findObject(By.res(ZOOM)).visibleCenter
        doubleTap(centre.x.toFloat(), centre.y.toFloat())
        assertTrue("double tap should leave zoom", device.wait(Until.gone(By.res(ZOOM)), TIMEOUT_MS))
    }

    @Test
    fun pinchingBackOutLeavesZoom() {
        device.findObject(By.res(PAGE)).pinchOpen(PINCH_PERCENT, PINCH_SPEED)
        assertNotNull(device.wait(Until.findObject(By.res(ZOOM)), TIMEOUT_MS))
        device.findObject(By.res(ZOOM)).pinchClose(1f, PINCH_SPEED)
        assertTrue("pinching back to 1x should leave zoom", device.wait(Until.gone(By.res(ZOOM)), TIMEOUT_MS))
    }

    /** Two quick taps injected directly; separate UiAutomator clicks are too far apart to count as a double tap. */
    private fun doubleTap(x: Float, y: Float) {
        val automation = instrumentation.uiAutomation
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, at: Long) {
            val event = MotionEvent.obtain(start, start + at, action, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            automation.injectInputEvent(event, true)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 0)
        send(MotionEvent.ACTION_UP, TAP_MS)
        send(MotionEvent.ACTION_DOWN, TAP_MS * 2)
        send(MotionEvent.ACTION_UP, TAP_MS * 3)
    }

    private companion object {
        const val TAP_MS = 60L
        const val BOOK_ID = "93652b69c43197d10dc83438b813b5f7515e61e7ea4336da8636ff902cb4aba8"
        const val PAGE = "reader_page"
        const val ZOOM = "reader_zoom"
        const val TIMEOUT_MS = 10_000L
        const val SETTLE_MS = 1_500L
        const val PINCH_PERCENT = 0.6f
        const val PINCH_SPEED = 1_500
    }
}

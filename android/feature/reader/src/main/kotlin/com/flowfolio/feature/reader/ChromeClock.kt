package com.flowfolio.feature.reader

import android.app.Activity
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.View
import android.view.Window
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flowfolio.core.model.ControlsAutoHide
import com.flowfolio.feature.reader.riffle.AutoHide
import com.flowfolio.feature.reader.riffle.ChromePause
import com.flowfolio.feature.reader.riffle.ChromeState
import com.flowfolio.feature.reader.riffle.ChromeTimer
import kotlinx.coroutines.delay

/**
 * When the reader's chrome shows (spec 3.4): the riffle's [ChromeTimer] on the uptime clock. It starts hidden (the
 * clean page); a tap in the middle shows or hides it; using the bars restarts the countdown, page turns do not, and a
 * drag, popover or open panel holds it.
 */
@Stable
internal class ChromeClock(autoHide: AutoHide) : ChromeControl {
    private val timer = ChromeTimer(autoHide, now()).apply { hide() }

    var shown by mutableStateOf(false)
        private set

    /** The clock as of the last tick: read only while drawing the countdown line. */
    private var ticked by mutableLongStateOf(now())

    val autoHide: AutoHide get() = timer.autoHide

    override fun interacted() = timer.interacted(now())

    override fun pause(reason: ChromePause, held: Boolean) {
        if (held) timer.hold(reason) else timer.release(reason, now())
    }

    fun toggle() = sync { timer.toggle(now()) }

    fun hide() = sync { timer.hide() }

    /** The setting changed: takes effect from now, without showing the chrome. */
    fun use(value: AutoHide) {
        if (value != timer.autoHide) timer.setAutoHide(value, now(), show = false)
    }

    fun tick() = sync {
        ticked = now()
        timer.tick(ticked)
    }

    fun remaining(): Float = timer.remaining(ticked)

    private inline fun sync(change: () -> Unit) {
        change()
        shown = timer.state == ChromeState.VISIBLE
    }

    private fun now() = SystemClock.uptimeMillis()
}

/** The chrome clock for the reader, following the auto-hide setting, ticked every frame while the chrome shows. */
@Composable
internal fun rememberChromeClock(setting: ControlsAutoHide): ChromeClock {
    val clock = remember { ChromeClock(setting.toAutoHide()) }
    LaunchedEffect(setting) { clock.use(setting.toAutoHide()) }
    LaunchedEffect(clock.shown) {
        while (clock.shown) {
            // With Never there is no countdown to draw: a slow check is enough.
            if (clock.autoHide.millis == null) delay(NEVER_CHECK_MS) else withFrameNanos { }
            clock.tick()
        }
    }
    SystemBars(clock.shown)
    return clock
}

internal fun ControlsAutoHide.toAutoHide(): AutoHide = AutoHide.valueOf(name)

/**
 * The status and navigation bars come and go with the chrome (spec 3.4); while hidden, a swipe from the edge shows
 * them for a moment. The pages are laid out edge to edge, so their size never changes. Leaving the reader shows them.
 */
@Composable
private fun SystemBars(shown: Boolean) {
    val view = LocalView.current
    val controller = remember(view) { view.window()?.let { WindowCompat.getInsetsController(it, view) } }
    LaunchedEffect(controller, shown) {
        controller ?: return@LaunchedEffect
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (shown) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(controller) { onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) } }
}

/**
 * Enter (or the D-pad's centre) shows or hides the chrome (riffle spec 6). The reader takes focus when it opens so the
 * key reaches it; with the strip focused the key still bubbles up to here.
 */
@Composable
internal fun Modifier.toggleOnEnter(onToggle: () -> Unit): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
    return onKeyEvent { event ->
        val enter = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter
        if (enter && event.type == KeyEventType.KeyUp) onToggle()
        enter
    }.focusRequester(focus).focusable()
}

private fun View.window(): Window? {
    var c = context
    while (c is ContextWrapper) {
        if (c is Activity) return c.window
        c = c.baseContext
    }
    return null
}

private const val NEVER_CHECK_MS = 250L

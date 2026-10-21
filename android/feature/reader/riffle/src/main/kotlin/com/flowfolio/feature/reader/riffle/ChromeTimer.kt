package com.flowfolio.feature.reader.riffle

enum class ChromeState { VISIBLE, HIDDEN }

/** Why the auto-hide countdown is held (spec 3.4). */
enum class ChromePause {
    DRAGGING,
    POPOVER,
    GO_TO_PAGE,
    NUDGE,

    /** One of the host's panels (the reader's contents, Go to page, Aa) is open. */
    PANEL
}

/** The auto-hide delays offered (spec 5.7); null is Never. */
@Suppress("MagicNumber") // the spec's delays, named by their values
enum class AutoHide(val millis: Long?) {
    S3(3_000L),
    S5(5_000L),
    S8(8_000L),
    S15(15_000L),
    NEVER(null);

    companion object {
        val DEFAULT = S5
    }
}

/**
 * When the reader's chrome hides (spec 3.4), on a clock the caller passes in (uptime milliseconds, or a virtual clock
 * in tests). Interaction with the chrome restarts the countdown; page turns do not; while anything holds it (a drag, a
 * popover, the go-to-page field, the nudge arrows) it waits, and starts again in full once nothing does.
 */
class ChromeTimer(autoHide: AutoHide, now: Long) {
    var state: ChromeState = ChromeState.VISIBLE
        private set

    var autoHide: AutoHide = autoHide
        private set

    private var since = now
    private val pauses = HashSet<ChromePause>()

    /** A tap, drag or other touch on the chrome (strip, ribbon, flags, top bar, popovers). */
    fun interacted(now: Long) {
        since = now
    }

    /** A page turned. Deliberately does not restart the countdown, so fast readers still reach the clean view. */
    fun pageTurned() = Unit

    fun hold(reason: ChromePause) {
        pauses += reason
    }

    fun release(reason: ChromePause, now: Long) {
        if (pauses.remove(reason) && pauses.isEmpty()) since = now
    }

    /** A tap in the middle of the page shows or hides the chrome; hiding drops every hold (popovers close with it). */
    fun toggle(now: Long) {
        if (state == ChromeState.VISIBLE) hide() else show(now)
    }

    fun show(now: Long) {
        state = ChromeState.VISIBLE
        since = now
    }

    fun hide() {
        state = ChromeState.HIDDEN
        pauses.clear()
    }

    /** A new delay, chosen in settings: shows the chrome so the reader sees it take effect, unless not [show]. */
    fun setAutoHide(value: AutoHide, now: Long, show: Boolean = true) {
        autoHide = value
        if (show) show(now) else since = now
    }

    /** Advances to [now]; returns true when the chrome hid. */
    fun tick(now: Long): Boolean {
        val delay = autoHide.millis
        val due = state == ChromeState.VISIBLE && delay != null && pauses.isEmpty() && now - since >= delay
        if (due) hide()
        return due
    }

    /** What is left of the countdown, from 1 to 0, for the line under the strip header; 1 while held or Never. */
    fun remaining(now: Long): Float {
        val delay = autoHide.millis
        return if (delay == null || pauses.isNotEmpty() || state == ChromeState.HIDDEN) {
            1f
        } else {
            (1f - (now - since).toFloat() / delay).coerceIn(0f, 1f)
        }
    }
}

package com.flowfolio.feature.reader.riffle

/** What the scrubber tells its host (the reader, or the demo). Every method has a default that does nothing. */
interface RiffleEvents {
    /** The ribbon was let go over [unit] (land there; spec 5.4). */
    fun onRelease(unit: Int) = Unit

    /** A single flag was tapped, or a flag was chosen from a stack's popover. */
    fun onFlag(flag: RiffleFlag) = Unit

    /** Feedback for one step of a drag: the host plays haptics and sounds if the user wants them. */
    fun onFeedback(feedback: RiffleFeedback) = Unit

    /** A fine-scrub speed threshold was crossed. */
    fun onSpeed(speed: ScrubSpeed) = Unit

    /** Any touch on the strip: restarts the chrome's auto-hide countdown (spec 3.4). */
    fun onInteraction() = Unit

    /**
     * Something that holds the chrome's countdown started ([held] true) or ended (spec 3.4): the ribbon held, a popover
     * or the go-to-page field open.
     */
    fun onPause(reason: ChromePause, held: Boolean) = Unit

    /** Go to [unit]: an arrow at an end of the strip, or the go-to-page field (land there with the turn, spec 5.4). */
    fun onGoTo(unit: Int) = Unit
}

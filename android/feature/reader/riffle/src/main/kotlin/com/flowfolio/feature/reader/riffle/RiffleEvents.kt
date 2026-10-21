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

    /** The ribbon was taken or let go: the chrome's countdown waits while it is held. */
    fun onHeld(held: Boolean) = Unit
}

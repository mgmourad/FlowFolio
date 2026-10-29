package com.flowfolio.core.data.flow

import android.content.Intent
import com.flowfolio.core.model.FlowFailure
import com.flowfolio.core.model.FlowPause

/**
 * What the conversion process ([FlowService]) and the app say to each other: the start request, and two broadcasts sent
 * only to the app's own package (its receiver is not exported) with how far a book is and how it ended.
 */
internal object FlowProtocol {
    const val ACTION_CONVERT = "com.flowfolio.flow.CONVERT"
    const val ACTION_PROGRESS = "com.flowfolio.flow.PROGRESS"
    const val ACTION_DONE = "com.flowfolio.flow.DONE"
    const val EXTRA_BOOK_ID = "bookId"
    const val EXTRA_TITLE = "title"
    const val EXTRA_FILE = "file"
    const val EXTRA_URI = "uri"
    const val EXTRA_FRACTION = "fraction"
    const val EXTRA_PAUSE = "pause"
    const val EXTRA_FAILURE = "failure"

    /** [fraction] done (0 to 1), or -1 while the book waits for another; [pause] when it is waiting for the phone. */
    fun progress(pkg: String, bookId: String, fraction: Float, pause: FlowPause?): Intent = Intent(ACTION_PROGRESS)
        .setPackage(pkg)
        .putExtra(EXTRA_BOOK_ID, bookId)
        .putExtra(EXTRA_FRACTION, fraction)
        .putExtra(EXTRA_PAUSE, pause?.name)

    /** The book finished: converted when [failure] is null. */
    fun done(pkg: String, bookId: String, failure: FlowFailure?): Intent = Intent(ACTION_DONE)
        .setPackage(pkg)
        .putExtra(EXTRA_BOOK_ID, bookId)
        .putExtra(EXTRA_FAILURE, failure?.name)
}

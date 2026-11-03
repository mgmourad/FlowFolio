package com.flowfolio.core.data

import java.net.HttpURLConnection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a reader is told when a check for updates does not come back with a release (M5 round 6).
 *
 * Every non-200 used to collapse into one bare exception, which the screen reported as "could not reach the
 * releases page". A repository with no public releases answers 404 promptly, so the message blamed the reader's
 * connection for a page that had replied. These cases keep the two apart.
 */
class UpdateFailureTest {
    @Test
    fun `a 404 is a missing releases page, not an absent network`() {
        assertEquals(UpdateFailure.MISSING, Updates.failureFor(HttpURLConnection.HTTP_NOT_FOUND))
    }

    @Test
    fun `rate limiting is its own answer, since waiting is the fix`() {
        assertEquals(UpdateFailure.BUSY, Updates.failureFor(HttpURLConnection.HTTP_FORBIDDEN))
        assertEquals(UpdateFailure.BUSY, Updates.failureFor(Updates.TOO_MANY_REQUESTS))
    }

    @Test
    fun `anything else is unexpected rather than guessed at`() {
        assertEquals(UpdateFailure.UNEXPECTED, Updates.failureFor(HttpURLConnection.HTTP_INTERNAL_ERROR))
        assertEquals(UpdateFailure.UNEXPECTED, Updates.failureFor(HttpURLConnection.HTTP_BAD_REQUEST))
    }

    @Test
    fun `the reason survives being thrown`() {
        assertEquals(UpdateFailure.MISSING, UpdateUnavailable(UpdateFailure.MISSING).reason)
    }
}

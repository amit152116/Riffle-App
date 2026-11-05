package com.amit152116.riffle.work

/** Real (non-cancellation) analysis failures retry linearly, three times in all, then give up. */
object AnalysisRetryPolicy {
    const val MAX_ATTEMPTS = 3
    const val RETRY_DELAY_SECONDS = 30L
    fun shouldRetry(runAttemptCount: Int): Boolean = runAttemptCount.coerceAtLeast(0) < MAX_ATTEMPTS
}

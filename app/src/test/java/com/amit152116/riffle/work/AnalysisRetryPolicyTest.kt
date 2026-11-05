package com.amit152116.riffle.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisRetryPolicyTest {
    @Test fun retriesUntilTheAttemptCapIsReached() {
        assertTrue(AnalysisRetryPolicy.shouldRetry(0))
        assertTrue(AnalysisRetryPolicy.shouldRetry(1))
        assertTrue(AnalysisRetryPolicy.shouldRetry(2))
        assertFalse(AnalysisRetryPolicy.shouldRetry(3))
        assertFalse(AnalysisRetryPolicy.shouldRetry(10))
    }

    @Test fun negativeAttemptCounts_areTreatedAsTheFirstAttempt() {
        assertTrue(AnalysisRetryPolicy.shouldRetry(-5))
    }

    @Test fun constantsMatchTheSpec() {
        assertEquals(3, AnalysisRetryPolicy.MAX_ATTEMPTS)
        assertEquals(30L, AnalysisRetryPolicy.RETRY_DELAY_SECONDS)
    }
}

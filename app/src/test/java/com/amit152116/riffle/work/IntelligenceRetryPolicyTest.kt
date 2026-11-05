package com.amit152116.riffle.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceRetryPolicyTest {
    @Test fun retriesAFewTimes_thenGivesUp() {
        assertFalse(IntelligenceRetryPolicy.shouldGiveUp(0))
        assertFalse(IntelligenceRetryPolicy.shouldGiveUp(IntelligenceRetryPolicy.MAX_ATTEMPTS - 1))
        assertTrue(IntelligenceRetryPolicy.shouldGiveUp(IntelligenceRetryPolicy.MAX_ATTEMPTS))
        assertTrue(IntelligenceRetryPolicy.shouldGiveUp(IntelligenceRetryPolicy.MAX_ATTEMPTS + 10))
    }

    @Test fun maxAttempts_isThree() = assertEquals(3, IntelligenceRetryPolicy.MAX_ATTEMPTS)
}

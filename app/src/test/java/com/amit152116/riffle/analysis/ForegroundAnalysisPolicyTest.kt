package com.amit152116.riffle.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundAnalysisPolicyTest {
    private val p = ForegroundAnalysisPolicy

    @Test fun pendingAnalysis_alwaysRunsAChunk() {
        assertEquals(ForegroundStep.RUN_CHUNK, p.next(pendingAnalysis = 5, pendingHash = 0, hashWaitPolls = 0))
        assertEquals(ForegroundStep.RUN_CHUNK, p.next(5, 100, ForegroundAnalysisPolicy.MAX_HASH_WAIT_POLLS + 50))
    }

    @Test fun nothingPendingButHashingStillRunning_waitsForTheHashPass_untilTheLimit() {
        assertEquals(ForegroundStep.WAIT_FOR_HASH, p.next(0, 10, 0))
        assertEquals(ForegroundStep.WAIT_FOR_HASH, p.next(0, 10, ForegroundAnalysisPolicy.MAX_HASH_WAIT_POLLS - 1))
        assertEquals(ForegroundStep.FINISH, p.next(0, 10, ForegroundAnalysisPolicy.MAX_HASH_WAIT_POLLS))
    }

    @Test fun nothingPendingAnywhere_finishes() {
        assertEquals(ForegroundStep.FINISH, p.next(0, 0, 0))
    }

    @Test fun negativeCounts_areTreatedAsZero() {
        assertEquals(ForegroundStep.FINISH, p.next(-3, -1, 0))
        assertEquals(ForegroundStep.WAIT_FOR_HASH, p.next(-3, 4, 0))
    }

    @Test fun progress_isAPercentageOfWhatWasPendingAtStart() {
        assertEquals(0, p.progressPercent(startPending = 400, pending = 400))
        assertEquals(50, p.progressPercent(400, 200))
        assertEquals(100, p.progressPercent(400, 0))
    }

    @Test fun progress_isClamped_andSafeForEmptyOrGrowingQueues() {
        assertEquals(100, p.progressPercent(0, 0))
        assertEquals(100, p.progressPercent(-5, 3))
        assertEquals(0, p.progressPercent(100, 250)) // queue grew (new files): never negative
        assertEquals(100, p.progressPercent(100, -4))
    }

    @Test fun notificationText_showsDoneOfTotal() {
        assertEquals("Analysing music: 150 of 400", p.notificationText(startPending = 400, pending = 250))
        assertEquals("Analysing music: 0 of 400", p.notificationText(400, 900))
        assertEquals("Analysing music: 400 of 400", p.notificationText(400, 0))
    }
}

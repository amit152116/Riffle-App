package com.amit152116.riffle.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The design targets the estimator constants must keep satisfying (spec: "Chunk budget"). */
class AnalysisBudgetCalibrationTest {
    private val b = AnalysisBudget()

    @Test fun aTypicalSong_costsMuchLessThanOneChunk_soManySongsShareAChunk() {
        assertTrue(b.estimateSeconds(240.0) < b.chunkBudgetSeconds / 6) // at least 6 four-minute songs per chunk at the padded estimate (~8 in reality)
    }

    @Test fun twentyToThirtyMinuteTracks_areNotFlaggedOversized() {
        for (minutes in listOf(20.0, 25.0, 30.0)) {
            assertFalse("minutes=$minutes", b.exceedsSingleRun(b.estimateSeconds(minutes * 60)))
        }
    }

    @Test fun aThreeHourTrack_isStillBoundedByTheWindowedCost() {
        assertTrue(b.estimateSeconds(3 * 3600.0) <= AnalysisBudgetConfig().maxEstimateSeconds)
    }

    @Test fun aSixMinuteFullTrack_fitsAloneInAChunk() {
        assertTrue(b.estimateSeconds(360.0) < b.chunkBudgetSeconds)
    }
}

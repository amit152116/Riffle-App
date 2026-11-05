package com.amit152116.riffle.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisBudgetTest {
    private val b = AnalysisBudget()

    @Test fun estimate_growsWithDuration_forFullTracks() {
        assertTrue(b.estimateSeconds(120.0) < b.estimateSeconds(240.0))
        assertTrue(b.estimateSeconds(240.0) < b.estimateSeconds(360.0))
    }

    @Test fun estimate_forWindowedTracks_isBoundedByTheAnalysedSecondsNotTheLength() {
        val long = b.estimateSeconds(1800.0)
        val longer = b.estimateSeconds(3600.0)
        // only the decode/fingerprint part grows: a 2x longer track costs strictly less than 2x
        assertTrue(longer < long * 1.95)
        assertTrue(long > b.estimateSeconds(360.0) * 0.5)
    }

    @Test fun estimate_isAlwaysFinite_andWithinTheClamp_forEveryDuration() {
        val cfg = AnalysisBudgetConfig()
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0, 1e-9, 1.0, 1e6, 1e12, Double.MAX_VALUE)
            .forEach {
                val e = b.estimateSeconds(it)
                assertTrue("duration=$it -> $e", e.isFinite())
                assertTrue("duration=$it -> $e", e >= cfg.minEstimateSeconds && e <= cfg.maxEstimateSeconds)
            }
    }

    @Test fun unknownDuration_usesTheConservativeFallback() {
        assertEquals(b.estimateSeconds(300.0), b.estimateSeconds(null), 1e-9)
        assertEquals(b.estimateSeconds(300.0), b.estimateSeconds(Double.NaN), 1e-9)
    }

    @Test fun estimate_dependsOnlyOnDuration_notOnAnythingElse() {
        assertEquals(b.estimateSeconds(431.0), AnalysisBudget().estimateSeconds(431.0), 0.0)
    }

    @Test fun firstTrackOfAChunk_isAlwaysStarted_evenIfItsEstimateExceedsTheWholeBudget() {
        assertTrue(b.shouldStartNext(0.0, 1e9, 0))
        assertTrue(b.shouldStartNext(1e9, 1e9, 0))
        assertTrue(b.shouldStartNext(Double.NaN, Double.NaN, 0))
    }

    @Test fun laterTracks_startOnlyWhileTheyFitTheRemainingBudget() {
        assertTrue(b.shouldStartNext(100.0, 80.0, 3))   // 180 <= 180
        assertFalse(b.shouldStartNext(100.0, 80.1, 3))  // over
        assertFalse(b.shouldStartNext(179.9, 1.0, 5))
    }

    @Test fun nonFiniteInputs_afterTheFirstTrack_stopTheChunk_safely() {
        assertFalse(b.shouldStartNext(Double.NaN, 5.0, 2))
        assertFalse(b.shouldStartNext(10.0, Double.NaN, 2)) // NaN estimate is sanitised to the clamp maximum
        assertFalse(b.shouldStartNext(Double.POSITIVE_INFINITY, 5.0, 2))
    }

    @Test fun badConfig_isSanitised_neverThrows_andBehavesLikeTheDefaults() {
        val bad = AnalysisBudget(AnalysisBudgetConfig(
            chunkBudgetSeconds = -5.0, singleRunBudgetSeconds = Double.NaN, decodeSecondsPerAudioSecond = 0.0,
            featureSecondsPerAnalysedSecond = -1.0, windowedAnalysedSeconds = Double.POSITIVE_INFINITY,
            minEstimateSeconds = Double.NaN, maxEstimateSeconds = -3.0, fallbackDurationSeconds = 0.0
        ))
        assertEquals(180.0, bad.chunkBudgetSeconds, 0.0)
        assertEquals(b.estimateSeconds(240.0), bad.estimateSeconds(240.0), 1e-9)
        assertEquals(b.estimateSeconds(null), bad.estimateSeconds(null), 1e-9)
    }

    @Test fun tinyPositiveBudget_isHonoured_soOnlyTheFirstTrackRuns() {
        val tiny = AnalysisBudget(AnalysisBudgetConfig(chunkBudgetSeconds = 0.001))
        assertTrue(tiny.shouldStartNext(0.0, 5.0, 0))
        assertFalse(tiny.shouldStartNext(0.0, 5.0, 1))
    }

    @Test fun exceedsSingleRun_comparesAgainstTheSingleRunBudget() {
        assertFalse(b.exceedsSingleRun(150.0))
        assertTrue(b.exceedsSingleRun(150.01))
        assertFalse(b.exceedsSingleRun(Double.NaN)) // sanitised to a finite value below the flag threshold check
    }

    @Test fun systemClock_isMonotonic() {
        val a = SystemMonotonicClock.nowNanos()
        val c = SystemMonotonicClock.nowNanos()
        assertTrue(c >= a)
    }
}

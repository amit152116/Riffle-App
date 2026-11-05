package com.amit152116.riffle.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPlanTest {
    private fun windowed(d: Double, cfg: AnalysisPlanConfig = AnalysisPlanConfig()) =
        AnalysisPlan.forDuration(d, cfg) as AnalysisPlan.Windowed

    @Test fun upToSixMinutes_isFull_exactlyAtTheBoundary() {
        assertEquals(AnalysisPlan.Full, AnalysisPlan.forDuration(1.0))
        assertEquals(AnalysisPlan.Full, AnalysisPlan.forDuration(240.0))
        assertEquals(AnalysisPlan.Full, AnalysisPlan.forDuration(360.0))
    }

    @Test fun justOverSixMinutes_isWindowed() {
        assertEquals(6, windowed(360.001).windows.size)
    }

    @Test fun unknownOrInvalidDurations_areFull_andNeverThrow() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -5.0).forEach {
            assertEquals("duration=$it", AnalysisPlan.Full, AnalysisPlan.forDuration(it))
        }
    }

    @Test fun windows_areWithinTheTrack_sorted_equalLength_andDoNotOverlap() {
        for (d in listOf(361.0, 600.0, 1500.0, 1800.0, 7200.0)) {
            val w = windowed(d).windows
            assertEquals(6, w.size)
            w.forEach {
                assertTrue("d=$d $it", it.startSec >= 0.0 && it.endSec <= d + 1e-9)
                assertEquals(45.0, it.lengthSec, 1e-9)
            }
            for (i in 1 until w.size) assertTrue("d=$d overlap at $i", w[i].startSec >= w[i - 1].endSec)
        }
    }

    @Test fun windows_areCentredOnEvenSlices() {
        val w = windowed(1500.0).windows
        assertEquals(125.0, (w[0].startSec + w[0].endSec) / 2, 1e-9) // (0 + 0.5) / 6 * 1500
        assertEquals(1375.0, (w[5].startSec + w[5].endSec) / 2, 1e-9) // (5 + 0.5) / 6 * 1500
    }

    @Test fun customConfig_isHonoured() {
        val w = windowed(1000.0, AnalysisPlanConfig(fullAnalysisMaxSeconds = 100.0, windowCount = 3, windowSeconds = 30.0)).windows
        assertEquals(3, w.size)
        assertEquals(30.0, w[0].lengthSec, 1e-9)
        assertEquals(AnalysisPlan.Full, AnalysisPlan.forDuration(100.0, AnalysisPlanConfig(fullAnalysisMaxSeconds = 100.0)))
    }

    @Test fun invalidConfig_fallsBackToDefaults_insteadOfThrowing() {
        val bad = AnalysisPlanConfig(fullAnalysisMaxSeconds = Double.NaN, windowCount = 0, windowSeconds = -1.0)
        val plan = AnalysisPlan.forDuration(900.0, bad) as AnalysisPlan.Windowed
        assertEquals(1, plan.windows.size)
        assertEquals(45.0, plan.windows[0].lengthSec, 1e-9)
    }

    @Test fun windowLongerThanTheTrack_isClampedToTheTrack() {
        val w = windowed(400.0, AnalysisPlanConfig(fullAnalysisMaxSeconds = 100.0, windowCount = 2, windowSeconds = 900.0)).windows
        w.forEach { assertTrue(it.startSec >= 0.0 && it.endSec <= 400.0 + 1e-9) }
    }
}

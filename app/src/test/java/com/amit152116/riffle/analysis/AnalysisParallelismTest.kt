package com.amit152116.riffle.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisParallelismTest {
    private val p = AnalysisParallelism

    @Test fun foreground_usesUpToFourSlots() {
        assertEquals(4, AnalysisParallelism.choose(cores = 8, charging = false, foreground = true))
        assertEquals(4, AnalysisParallelism.choose(cores = 8, charging = true, foreground = true))
    }

    @Test fun backgroundWhileCharging_usesTwo_onBatteryUsesOne() {
        assertEquals(2, AnalysisParallelism.choose(cores = 8, charging = true, foreground = false))
        assertEquals(1, AnalysisParallelism.choose(cores = 8, charging = false, foreground = false))
    }

    @Test fun fewCores_areNeverOversubscribed() {
        assertEquals(1, AnalysisParallelism.choose(cores = 1, charging = true, foreground = true))
        assertEquals(1, AnalysisParallelism.choose(cores = 2, charging = true, foreground = true))
        assertEquals(2, AnalysisParallelism.choose(cores = 4, charging = true, foreground = true))
    }

    @Test fun garbageCoreCounts_fallBackToOne_andNeverExceedTheCap() {
        for (c in listOf(0, -1, Int.MIN_VALUE)) assertEquals(1, AnalysisParallelism.choose(c, true, true))
        assertTrue(AnalysisParallelism.choose(Int.MAX_VALUE, true, true) <= 4)
    }

    @Test fun heavyTrackLimit_isTwo_andOnlyAppliesToLongOrUnknownDurations() {
        assertTrue(AnalysisParallelism.isHeavy(null))
        assertTrue(AnalysisParallelism.isHeavy(Double.NaN))
        assertTrue(AnalysisParallelism.isHeavy(241.0))
        assertTrue(!AnalysisParallelism.isHeavy(240.0))
        assertTrue(!AnalysisParallelism.isHeavy(30.0))
    }

    @Test fun smallJavaHeaps_capTheSlots_soDecodeBuffersCannotExhaustMemory() {
        // ~96 MB of heap per slot: a 4-minute 48 kHz track holds ~46 MB of PCM, briefly twice that while trimmed.
        assertEquals(4, p.choose(cores = 8, charging = true, foreground = true, maxHeapMb = 512))
        assertEquals(4, p.choose(8, true, true, maxHeapMb = 384))
        assertEquals(2, p.choose(8, true, true, maxHeapMb = 256))
        assertEquals(1, p.choose(8, true, true, maxHeapMb = 128))
        assertEquals(1, p.choose(8, true, true, maxHeapMb = 0))
        assertEquals(1, p.choose(8, true, true, maxHeapMb = -5))
    }

    @Test fun heapDoesNotRaiseTheSlotsAboveThePolicy() {
        assertEquals(1, p.choose(8, charging = false, foreground = false, maxHeapMb = 4096))
        assertEquals(2, p.choose(8, charging = true, foreground = false, maxHeapMb = 4096))
    }

    @Test fun heavyTrackLimit_dependsOnTheHeap() {
        assertEquals(2, p.maxConcurrentHeavy(maxHeapMb = 512))
        assertEquals(2, p.maxConcurrentHeavy(384))
        assertEquals(1, p.maxConcurrentHeavy(256))
        assertEquals(1, p.maxConcurrentHeavy(0))
    }
}

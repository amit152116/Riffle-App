package com.amit152116.riffle.analysis

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintSegmenterTest {
    private val s = FingerprintSegmenter()
    private val hopSec = FingerprintSegmenter.HOP_SAMPLES_44K / 44_100.0

    @Test fun segmentLength_isAWholeNumberOfHops_nearTheConfiguredSeconds() {
        val len = s.segmentHops
        assertTrue(len > 0)
        assertEquals(300.0, len * hopSec, hopSec) // within one hop of the configured 300 s
    }

    @Test fun warmup_isAWholeNumberOfHops_atLeastTheConfiguredSeconds() {
        assertTrue(s.warmupHops * hopSec >= 12.0)
        assertTrue(s.warmupHops * hopSec < 12.0 + hopSec)
    }

    @Test fun segmentCount_coversTheDuration_withoutAnEmptyTail() {
        assertEquals(1, s.segmentCount(10.0))
        assertEquals(1, s.segmentCount(s.segmentHops * hopSec))
        assertEquals(2, s.segmentCount(s.segmentHops * hopSec + 0.5))
        assertEquals(6, s.segmentCount(1500.0)) // 1500 s / 299.99 s per segment
        assertEquals(1, s.segmentCount(Double.NaN)); assertEquals(1, s.segmentCount(-3.0))
    }

    @Test fun firstSegmentHasNoWarmup_laterOnesStartOneWarmupEarly() {
        assertEquals(0.0, s.feedRange(0).startSec, 0.0)
        assertEquals(0, s.dropItems(0))
        val r = s.feedRange(2)
        assertEquals(2 * s.segmentHops * hopSec - s.warmupHops * hopSec, r.startSec, 1e-9)
        assertEquals(s.warmupHops, s.dropItems(2))
        assertEquals(3 * s.segmentHops * hopSec, r.endSec, 1e-9)
    }

    @Test fun feedRanges_overlapOnlyByTheWarmup_andTileTheTrack() {
        for (i in 1 until 6) {
            val prev = s.feedRange(i - 1); val cur = s.feedRange(i)
            assertEquals(s.warmupHops * hopSec, prev.endSec - cur.startSec, 1e-9)
        }
    }

    @Test fun stitch_dropsTheWarmupItemsOfLaterSegmentsAndConcatenates() {
        val a = IntArray(10) { it }
        val b = IntArray(s.warmupHops + 4) { 1000 + it }
        val c = IntArray(s.warmupHops + 3) { 2000 + it }
        val out = s.stitch(listOf(a, b, c))
        assertEquals(10 + 4 + 3, out.size)
        assertArrayEquals(
            intArrayOf(1000 + s.warmupHops, 1001 + s.warmupHops, 1002 + s.warmupHops, 1003 + s.warmupHops),
            out.copyOfRange(10, 14)
        )
    }

    @Test fun stitch_ofASegmentShorterThanTheWarmup_contributesNothing_andNeverThrows() {
        assertEquals(5, s.stitch(listOf(IntArray(5), IntArray(3))).size)
        assertEquals(0, s.stitch(emptyList()).size)
    }

    @Test fun invalidConfig_isSanitised() {
        val bad = FingerprintSegmenter(SegmenterConfig(segmentSeconds = -1.0, warmupSeconds = Double.NaN))
        assertTrue(bad.segmentHops > bad.warmupHops)
    }
}

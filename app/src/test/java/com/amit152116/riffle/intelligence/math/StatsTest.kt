package com.amit152116.riffle.intelligence.math

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {
    @Test fun percentile_matchesNumpyLinearInterpolation() {
        val s = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(2.5, Stats.percentileSorted(s, 50.0), 1e-12)
        assertEquals(1.75, Stats.percentileSorted(s, 25.0), 1e-12)
        assertEquals(3.25, Stats.percentileSorted(s, 75.0), 1e-12)
        assertEquals(1.0, Stats.percentileSorted(s, 0.0), 1e-12)
        assertEquals(4.0, Stats.percentileSorted(s, 100.0), 1e-12)
    }

    @Test fun percentile_singleValue() =
        assertEquals(5.0, Stats.percentileSorted(doubleArrayOf(5.0), 66.0), 1e-12)

    @Test fun median_averagesMiddlePair() {
        assertEquals(2.5, Stats.median(doubleArrayOf(3.0, 1.0, 2.0, 4.0)), 1e-12)
        assertEquals(2.0, Stats.median(doubleArrayOf(3.0, 1.0, 2.0)), 1e-12)
    }

    @Test fun upperMedian_isPythonSortedIndexLenOver2() =
        assertEquals(3.0, Stats.upperMedian(doubleArrayOf(3.0, 1.0, 2.0, 4.0)), 1e-12)

    @Test fun mean() = assertEquals(2.0, Stats.mean(doubleArrayOf(1.0, 2.0, 3.0)), 1e-12)
}

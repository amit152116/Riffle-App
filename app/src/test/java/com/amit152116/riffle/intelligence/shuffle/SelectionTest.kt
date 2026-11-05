package com.amit152116.riffle.intelligence.shuffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SelectionTest {
    @Test fun weights_areStableForHugeScoreGaps() {
        val w = Selection.weights(doubleArrayOf(0.0, 1000.0, 2000.0), 0.1)
        assertTrue(w.all { it.isFinite() })
        assertEquals(1.0, w[0], 0.0)
        assertEquals(0.0, w[2], 0.0)
    }

    @Test fun weights_areRelativeToTheBestScore() {
        val a = Selection.weights(doubleArrayOf(5.0, 5.5), 1.0)
        val b = Selection.weights(doubleArrayOf(-5.0, -4.5), 1.0)
        assertEquals(a[1], b[1], 1e-12)
    }

    @Test fun zeroOrNegativeTemperature_isArgmin_andNeverDividesByZero() {
        assertEquals(0, Selection.pick(doubleArrayOf(0.1, 0.2, 0.3), 0.0, Random(1)))
        assertEquals(0, Selection.pick(doubleArrayOf(0.1, 0.2, 0.3), -1.0, Random(1)))
    }

    @Test fun singleCandidate_isReturned() =
        assertEquals(0, Selection.pick(doubleArrayOf(3.0), 0.5, Random(1)))

    @Test fun equalScores_areSampledRoughlyEvenly() {
        val rng = Random(1)
        val first = (1..1000).count { Selection.pick(doubleArrayOf(0.0, 0.0), 1.0, rng) == 0 }
        assertTrue(first in 400..600)
    }

    @Test fun lowTemperature_prefersTheBest() {
        val rng = Random(1)
        val first = (1..200).count { Selection.pick(doubleArrayOf(0.0, 1.0), 0.05, rng) == 0 }
        assertTrue(first > 190)
    }
}

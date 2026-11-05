package com.amit152116.riffle.intelligence.similarity

import com.amit152116.riffle.intelligence.TestData.rec
import com.amit152116.riffle.intelligence.model.SimilarityConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class BpmSpreadTest {
    private val cfg = SimilarityConfig()

    @Test fun fewerThanFourValid_usesFallback() =
        assertEquals(60.0, BpmSpread.compute(listOf(rec(1, bpm = 100.0), rec(2, bpm = 0.0), rec(3, bpm = null)), cfg), 0.0)

    @Test fun zeroAndNullBpm_areIgnored() {
        val recs = listOf(100.0, 110.0, 120.0, 130.0).mapIndexed { i, b -> rec(i + 1L, bpm = b) } +
            rec(9, bpm = 0.0) + rec(10, bpm = null)
        assertEquals(15.0, BpmSpread.compute(recs, cfg), 1e-9)
    }

    @Test fun iqr_isFloored() {
        val recs = listOf(100.0, 100.0, 100.0, 101.0).mapIndexed { i, b -> rec(i + 1L, bpm = b) }
        assertEquals(10.0, BpmSpread.compute(recs, cfg), 0.0)
    }
}

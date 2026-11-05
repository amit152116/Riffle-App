package com.amit152116.riffle.intelligence.similarity

import com.amit152116.riffle.intelligence.TestData.angleMfcc
import com.amit152116.riffle.intelligence.TestData.rec
import com.amit152116.riffle.intelligence.model.SimilarityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityIndexBuilderTest {
    private fun ids(index: SimilarityIndex, id: Long) = index.neighbors.getValue(id).map { it.audioContentId }

    private val angles = listOf(1L to 0.0, 2L to 10.0, 3L to 20.0, 4L to 90.0)
    private fun angled() = angles.map { (id, deg) -> rec(id, mfcc = angleMfcc(deg)) }

    @Test fun topK_isNearestFirst_andExcludesSelf() {
        val index = SimilarityIndexBuilder(SimilarityConfig(topK = 2)).build(angled())
        assertEquals(listOf(2L, 3L), ids(index, 1))
        assertEquals(listOf(3L, 2L), ids(index, 4))
        assertTrue(index.neighbors.all { (id, list) -> list.none { it.audioContentId == id } })
    }

    @Test fun kLargerThanPopulation_returnsAllOthers() {
        val index = SimilarityIndexBuilder(SimilarityConfig(topK = 20)).build(angled())
        assertEquals(3, index.neighbors.getValue(1).size)
    }

    @Test fun equalScores_breakTiesByAudioContentId() {
        val recs = listOf(
            rec(5, mfcc = angleMfcc(0.0)), rec(3, mfcc = angleMfcc(0.0)),
            rec(1, mfcc = angleMfcc(0.0)), rec(9, mfcc = angleMfcc(90.0))
        )
        val index = SimilarityIndexBuilder(SimilarityConfig(topK = 2)).build(recs)
        assertEquals(listOf(1L, 3L), ids(index, 9))
    }

    @Test fun inputOrderDoesNotMatter() {
        val a = SimilarityIndexBuilder(SimilarityConfig(topK = 2)).build(angled())
        val b = SimilarityIndexBuilder(SimilarityConfig(topK = 2)).build(angled().reversed())
        angles.forEach { (id, _) -> assertEquals(ids(a, id), ids(b, id)) }
    }

    @Test fun scoresAreAscending_andBreakdownPresent() {
        val index = SimilarityIndexBuilder(SimilarityConfig(topK = 3)).build(angled())
        val scores = index.neighbors.getValue(1).map { it.breakdown.combined }
        assertEquals(scores.sorted(), scores)
        assertTrue(index.neighbors.getValue(1).all { it.breakdown.mfccDistance in 0.0..1.0 })
    }

    @Test fun degenerateSizes_doNotThrow() {
        val builder = SimilarityIndexBuilder(SimilarityConfig())
        assertTrue(builder.build(emptyList()).neighbors.isEmpty())
        assertTrue(builder.build(listOf(rec(1))).neighbors.getValue(1).isEmpty())
        assertEquals(listOf(2L), ids(builder.build(listOf(rec(1), rec(2))), 1))
    }

    @Test fun spreadIsRecorded() {
        val recs = listOf(100.0, 110.0, 120.0, 130.0).mapIndexed { i, b -> rec(i + 1L, bpm = b) }
        assertEquals(15.0, SimilarityIndexBuilder(SimilarityConfig()).build(recs).bpmSpread, 1e-9)
    }
}

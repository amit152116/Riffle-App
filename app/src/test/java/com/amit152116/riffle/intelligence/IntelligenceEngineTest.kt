package com.amit152116.riffle.intelligence

import com.amit152116.riffle.intelligence.TestData.rec
import com.amit152116.riffle.intelligence.model.ClusterConfig
import com.amit152116.riffle.intelligence.model.InputHash
import com.amit152116.riffle.intelligence.model.SimilarityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceEngineTest {
    private val engine = IntelligenceEngine()

    @Test fun build_producesNeighboursForEveryRecording_andClusters() {
        val out = engine.build(TestData.blobs(4, 10), previous = null)
        assertEquals(40, out.similarity.neighbors.size)
        assertTrue(out.similarity.neighbors.values.all { it.size == 20 })
        assertEquals(4, out.clusters.k)
        assertEquals(40, out.clusters.assignments.size)
    }

    @Test fun inputHash_isInputHashCompute() {
        val recs = TestData.blobs(2, 5)
        assertEquals(InputHash.compute(SimilarityConfig(), ClusterConfig(), recs), engine.inputHash(recs))
    }

    @Test fun configHash_changesWithEitherConfig() {
        assertNotEquals(engine.configHash(), IntelligenceEngine(similarityConfig = SimilarityConfig(topK = 5)).configHash())
        assertNotEquals(engine.configHash(), IntelligenceEngine(clusterConfig = ClusterConfig(seed = 1)).configHash())
    }

    @Test fun singleRecording_hasNoNeighbours_andOneCluster() {
        val out = engine.build(listOf(rec(1)), null)
        assertTrue(out.similarity.neighbors.getValue(1).isEmpty())
        assertEquals("All", out.clusters.clusters.single().displayName)
    }

    @Test fun emptyInput_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { engine.build(emptyList(), null) }
    }
}

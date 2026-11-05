package com.amit152116.riffle.intelligence.model

import com.amit152116.riffle.intelligence.TestData.rec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InputHashTest {
    private val sim = SimilarityConfig()
    private val clu = ClusterConfig()
    private val recs = listOf(rec(1), rec(2), rec(3))

    @Test fun sameInputs_sameHash() =
        assertEquals(InputHash.compute(sim, clu, recs), InputHash.compute(sim, clu, recs))

    @Test fun orderIndependent() =
        assertEquals(InputHash.compute(sim, clu, recs), InputHash.compute(sim, clu, recs.reversed()))

    @Test fun removingRecording_changesHash() =
        assertNotEquals(InputHash.compute(sim, clu, recs), InputHash.compute(sim, clu, recs.drop(1)))

    @Test fun addingRecording_changesHash() =
        assertNotEquals(InputHash.compute(sim, clu, recs), InputHash.compute(sim, clu, recs + rec(4)))

    @Test fun extractorVersionChange_changesHash() {
        val changed = listOf(rec(1, extractorVersion = "ex-2"), rec(2), rec(3))
        assertNotEquals(InputHash.compute(sim, clu, recs), InputHash.compute(sim, clu, changed))
    }

    @Test fun featuresConfigHashChange_changesHash() {
        val changed = listOf(rec(1, configHash = "cfg-2"), rec(2), rec(3))
        assertNotEquals(InputHash.compute(sim, clu, recs), InputHash.compute(sim, clu, changed))
    }

    @Test fun similarityWeightChange_changesHash() =
        assertNotEquals(
            InputHash.compute(sim, clu, recs),
            InputHash.compute(sim.copy(mfccWeight = 0.4), clu, recs)
        )

    @Test fun clusterSeedChange_changesHash() =
        assertNotEquals(
            InputHash.compute(sim, clu, recs),
            InputHash.compute(sim, clu.copy(seed = 7), recs)
        )

    @Test fun eligibilityChange_changesHash() {
        assertNotEquals(
            InputHash.compute(sim, clu, recs, EligibilityConfig()),
            InputHash.compute(sim, clu, recs, EligibilityConfig(minDurationSeconds = 10.0))
        )
        assertNotEquals(
            InputHash.compute(sim, clu, recs, EligibilityConfig()),
            InputHash.compute(sim, clu, recs, EligibilityConfig(silenceLufsFloor = -40.0))
        )
    }

    @Test fun algorithmVersionChange_changesHash() =
        assertNotEquals(
            InputHash.compute(sim, clu, recs),
            InputHash.compute(sim.copy(algorithmVersion = sim.algorithmVersion + 1), clu, recs)
        )
}

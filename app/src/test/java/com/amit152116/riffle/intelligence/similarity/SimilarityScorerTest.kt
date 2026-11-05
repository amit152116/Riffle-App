package com.amit152116.riffle.intelligence.similarity

import com.amit152116.riffle.intelligence.TestData.angleMfcc
import com.amit152116.riffle.intelligence.TestData.rec
import com.amit152116.riffle.intelligence.model.SimilarityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimilarityScorerTest {
    private val cfg = SimilarityConfig()

    private fun breakdown(a: com.amit152116.riffle.intelligence.model.EligibleRecording,
                          b: com.amit152116.riffle.intelligence.model.EligibleRecording,
                          cal: SimilarityCalibration = SimilarityCalibration.neutral(60.0)): ScoreBreakdown {
        val table = RecordingTable(listOf(a, b), cal)
        return SimilarityScorer(table, cfg, cal).breakdown(0, 1)
    }

    @Test fun identicalRecordings_scoreZero() {
        val r = breakdown(rec(1), rec(2))
        assertEquals(0.0, r.combined, 1e-9)
        assertEquals(0.0, r.mfccDistance, 1e-9)
    }

    @Test fun allFourComponents_weightedSum() {
        // mfcc 90deg apart -> (1-0)/2 = 0.5; bpm 30/60 = 0.5; key C->G = 1/6; energy 0.2
        val a = rec(1, bpm = 100.0, key = "C", energy = 0.5, mfcc = angleMfcc(0.0))
        val b = rec(2, bpm = 130.0, key = "G", energy = 0.7, mfcc = angleMfcc(90.0))
        val r = breakdown(a, b)
        assertEquals(0.5, r.mfccDistance, 1e-9)
        assertEquals(0.5, r.bpmDistance!!, 1e-9)
        assertEquals(1.0 / 6.0, r.keyDistance!!, 1e-9)
        assertEquals(0.2, r.energyDistance!!, 1e-9)
        assertEquals(0.5 * 0.5 + 0.25 * 0.5 + 0.15 / 6.0 + 0.1 * 0.2, r.combined, 1e-9)
    }

    @Test fun missingBpm_isImputedWithTheLibraryNeutral_notOmittedAndNotAPerfectMatch() {
        val a = rec(1, bpm = 0.0, key = "C", energy = 0.5, mfcc = angleMfcc(0.0))
        val b = rec(2, bpm = 100.0, key = "G", energy = 0.7, mfcc = angleMfcc(90.0))
        val r = breakdown(a, b, SimilarityCalibration.neutral(60.0).copy(bpmNeutral = 0.4))
        assertNull(r.bpmDistance) // null = "was missing", not "was zero"
        assertEquals(0.5 * 0.5 + 0.25 * 0.4 + 0.15 / 6.0 + 0.1 * 0.2, r.combined, 1e-9)
    }

    @Test fun missingKey_isImputedWithTheLibraryNeutral() {
        val r = breakdown(rec(1, key = null), rec(2, key = "G"), SimilarityCalibration.neutral(60.0).copy(keyNeutral = 0.3))
        assertNull(r.keyDistance)
        assertNotNull(r.bpmDistance)
        assertEquals(0.5 * 0.0 + 0.25 * 0.0 + 0.15 * 0.3 + 0.1 * 0.0, r.combined, 1e-9)
    }

    @Test fun trackMissingBpm_isNoCloserThanATypicalPair_soItCannotBecomeAHub() {
        // Under omit-and-renormalise a BPM-less track scored better than real neighbours (it silently
        // dropped its hardest-to-match component). With the neutral imputation it scores like a typical pair.
        val cal = SimilarityCalibration.neutral(60.0).copy(bpmNeutral = 0.6)
        // a 36 BPM gap is 36/60 = 0.6 of the range: exactly the neutral distance
        val typical = breakdown(rec(1, bpm = 100.0, mfcc = angleMfcc(0.0)), rec(2, bpm = 136.0, mfcc = angleMfcc(0.0)), cal).combined
        val missing = breakdown(rec(1, bpm = 100.0, mfcc = angleMfcc(0.0)), rec(3, bpm = 0.0, mfcc = angleMfcc(0.0)), cal).combined
        assertEquals(typical, missing, 0.05)
        assertTrue(missing > 0.1)
    }

    @Test fun mfccDistance_isDividedByTheLibraryScale() {
        val cal = SimilarityCalibration.neutral(60.0).copy(mfccScale = 0.5)
        // 90deg apart: cosine distance 1.0 -> 1.0 / 0.5 capped at 1
        assertEquals(1.0, breakdown(rec(1, mfcc = angleMfcc(0.0)), rec(2, mfcc = angleMfcc(90.0)), cal).mfccDistance, 1e-12)
        // 10deg apart: cosine distance ~0.0152 -> ~0.0304
        assertEquals((1 - Math.cos(Math.toRadians(10.0))) / 0.5, breakdown(rec(1, mfcc = angleMfcc(0.0)), rec(2, mfcc = angleMfcc(10.0)), cal).mfccDistance, 1e-9)
    }

    @Test fun energyDistance_isDividedByTheLibraryEnergyRange() {
        val cal = SimilarityCalibration.neutral(60.0).copy(energyScale = 0.2)
        assertEquals(0.5, breakdown(rec(1, energy = 0.20), rec(2, energy = 0.30), cal).energyDistance!!, 1e-9)
    }

    @Test fun unparseableKey_countsAsMaximalDistance() =
        assertEquals(1.0, breakdown(rec(1, key = "H"), rec(2, key = "C")).keyDistance!!, 1e-9)

    @Test fun zeroNormMfcc_isFiniteAndHalf() {
        val silent = rec(1, mfcc = DoubleArray(13))
        val r = breakdown(silent, rec(2, mfcc = angleMfcc(0.0)))
        assertEquals(0.5, r.mfccDistance, 1e-12)
        assertTrue(r.combined.isFinite())
        assertEquals(0.5, breakdown(silent, rec(3, mfcc = DoubleArray(13))).mfccDistance, 1e-12)
    }

    @Test fun symmetricAndBounded_overRandomRecordings() {
        val rng = Random(3)
        val recs = (1..40).map {
            rec(
                it.toLong(),
                bpm = if (rng.nextInt(5) == 0) 0.0 else rng.nextDouble(60.0, 200.0),
                key = if (rng.nextInt(5) == 0) null else listOf("C", "D", "F#", "Bb").random(rng),
                scale = listOf("major", "minor").random(rng),
                energy = rng.nextDouble(),
                mfcc = DoubleArray(13) { rng.nextDouble(-5.0, 5.0) }
            )
        }
        val cal = SimilarityCalibration.neutral(40.0)
        val scorer = SimilarityScorer(RecordingTable(recs, cal), cfg, cal)
        for (i in recs.indices) for (j in recs.indices) {
            val ij = scorer.combined(i, j)
            val ji = scorer.combined(j, i)
            assertEquals(ij, ji, 1e-12)
            assertTrue(ij in 0.0..1.0)
        }
    }
}

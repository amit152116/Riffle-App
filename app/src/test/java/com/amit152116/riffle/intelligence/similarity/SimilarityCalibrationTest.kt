package com.amit152116.riffle.intelligence.similarity

import com.amit152116.riffle.intelligence.TestData.rec
import com.amit152116.riffle.intelligence.model.EligibleRecording
import com.amit152116.riffle.intelligence.model.SimilarityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimilarityCalibrationTest {
    private val cfg = SimilarityConfig()

    /** c0 (log-energy) dominates every vector, as with real MFCC means; c1..c12 carry the timbre. */
    private fun library(n: Int, seed: Int = 1): List<EligibleRecording> {
        val rng = Random(seed)
        return (1..n).map { id ->
            rec(
                id.toLong(),
                bpm = rng.nextDouble(80.0, 180.0),
                energy = rng.nextDouble(0.05, 0.30),
                mfcc = DoubleArray(13) { d -> if (d == 0) -600.0 + rng.nextDouble(-50.0, 50.0) else rng.nextDouble(-20.0, 20.0) }
            )
        }
    }

    private fun mfccDistances(recs: List<EligibleRecording>, cal: SimilarityCalibration): List<Double> {
        val scorer = SimilarityScorer(RecordingTable(recs, cal), cfg, cal)
        return (0 until recs.size).flatMap { i -> (i + 1 until recs.size).map { j -> scorer.breakdown(i, j).mfccDistance } }.sorted()
    }

    @Test fun fit_dropsTheDominantFirstCoefficient_andStandardises() {
        val cal = SimilarityCalibration.fit(library(60), cfg)
        assertEquals(1, cal.mfccStartDim)
        assertEquals(12, cal.mfccDims)
        assertEquals(-600.0, cal.mfccMean[0], 25.0)
        assertTrue(cal.mfccStd.all { it > 0.0 })
    }

    @Test fun rawCosineOnThisLibrary_isNearlyZero_theProblemBeingFixed() {
        val recs = library(60)
        val d = mfccDistances(recs, SimilarityCalibration.neutral(60.0))
        assertTrue("median raw distance was ${d[d.size / 2]}", d[d.size / 2] < 0.01)
    }

    @Test fun calibratedMfccDistance_spansTheWholeRange() {
        val recs = library(60)
        val d = mfccDistances(recs, SimilarityCalibration.fit(recs, cfg))
        assertTrue("median ${d[d.size / 2]}", d[d.size / 2] > 0.3)
        assertTrue(d.last() <= 1.0)
        assertEquals(1.0, d[d.size * 95 / 100], 0.05) // the p95 pair is "maximally different" by construction
    }

    @Test fun fit_isDeterministic() {
        val recs = library(80)
        val a = SimilarityCalibration.fit(recs, cfg)
        val b = SimilarityCalibration.fit(recs.reversed(), cfg)
        assertEquals(a.mfccScale, b.mfccScale, 0.0)
        assertEquals(a.bpmNeutral, b.bpmNeutral, 0.0)
        assertEquals(a.keyNeutral, b.keyNeutral, 0.0)
    }

    @Test fun energyScale_isTheLibrariesOwnRange_notOne() {
        val cal = SimilarityCalibration.fit(library(80), cfg)
        assertTrue("energyScale ${cal.energyScale}", cal.energyScale in 0.1..0.3)
    }

    @Test fun neutrals_areTheLibraryMedianPairDistances_inZeroOne() {
        val cal = SimilarityCalibration.fit(library(80), cfg)
        assertTrue(cal.bpmNeutral in 0.05..1.0)
        assertTrue(cal.keyNeutral in 0.05..1.0)
    }

    @Test fun tinyLibrary_keepsRawMfcc_butStillCalibratesBpm() {
        val recs = listOf(100.0, 110.0, 120.0, 130.0).mapIndexed { i, b -> rec(i + 1L, bpm = b) }
        val cal = SimilarityCalibration.fit(recs, cfg)
        assertEquals(0, cal.mfccStartDim)
        assertEquals(2.0, cal.mfccScale, 0.0)
        assertEquals(15.0, cal.bpmSpread, 1e-9)
    }

    @Test fun identicalRecordings_zeroVariance_areFinite() {
        val recs = (1..20).map { rec(it.toLong()) }
        val cal = SimilarityCalibration.fit(recs, cfg)
        assertTrue(cal.mfccScale > 0.0 && cal.mfccScale.isFinite())
        assertTrue(cal.mfccStd.all { it > 0.0 })
        assertTrue(mfccDistances(recs, cal).all { it.isFinite() })
    }

    @Test fun silentAudio_zeroMfccVector_isFinite() {
        val recs = library(30) + rec(999, mfcc = DoubleArray(13))
        val cal = SimilarityCalibration.fit(recs, cfg)
        assertTrue(mfccDistances(recs, cal).all { it.isFinite() && it in 0.0..1.0 })
    }
}

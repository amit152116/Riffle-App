package com.amit152116.riffle.analysis

import com.amit152116.riffle.dsp.AudioFeatures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.pow

class FeatureAggregatorTest {
    private fun feat(
        bpm: Float = 120f, conf: Float = 2f, key: String = "C", scale: String = "major", keyStrength: Float = 0.5f,
        lufs: Float = -10f, dance: Float = 1f, energy: Float = 0.2f, centroid: Float = 1500f, onset: Float = 4f,
        dyn: Float = 3f, diss: Float = 0.3f, zcr: Float = 0.1f, mfcc: FloatArray = FloatArray(13) { it.toFloat() },
        duration: Float = 45f
    ) = AudioFeatures(bpm, conf, key, scale, keyStrength, lufs, dance, energy, centroid, onset, dyn, diss, zcr, mfcc, duration,
        intArrayOf(7, 8), 2, "cfg")

    @Test fun singleWindow_isReturnedUnchanged_withTheFullDuration() {
        val w = feat(bpm = 97.3f, lufs = -12.345f)
        val r = FeatureAggregator.combine(listOf(w), 1500f)
        assertEquals(97.3f, r.bpm, 0f)
        assertEquals(-12.345f, r.loudnessLufs, 0f)
        assertEquals(1500f, r.duration, 0f)
        assertArrayEquals(w.mfccMean, r.mfccMean, 0f)
    }

    @Test fun bpm_isTheConfidenceWeightedMedian_ignoringInvalidWindows() {
        val ws = listOf(feat(bpm = 100f, conf = 1f), feat(bpm = 0f, conf = 5f), feat(bpm = 120f, conf = 4f), feat(bpm = 200f, conf = 1f))
        // valid: 100(w1) 120(w4) 200(w1) -> total 6, half 3 -> cumulative 1, 5 -> 120
        assertEquals(120f, FeatureAggregator.combine(ws, 900f).bpm, 0f)
    }

    @Test fun bpmConfidence_isTheMeanOfTheValidWindows() {
        val ws = listOf(feat(bpm = 100f, conf = 1f), feat(bpm = 0f, conf = 5f), feat(bpm = 120f, conf = 3f))
        assertEquals(2f, FeatureAggregator.combine(ws, 900f).bpmConfidence, 1e-6f)
    }

    @Test fun noValidBpm_givesZero_notNaN() {
        val r = FeatureAggregator.combine(listOf(feat(bpm = 0f), feat(bpm = Float.NaN)), 900f)
        assertEquals(0f, r.bpm, 0f)
        assertEquals(0f, r.bpmConfidence, 0f)
    }

    @Test fun key_isTheStrengthWeightedVote() {
        val ws = listOf(
            feat(key = "C", scale = "major", keyStrength = 0.4f), feat(key = "A", scale = "minor", keyStrength = 0.9f),
            feat(key = "C", scale = "major", keyStrength = 0.4f), feat(key = "A", scale = "minor", keyStrength = 0.2f)
        )
        val r = FeatureAggregator.combine(ws, 900f)
        assertEquals("A", r.keyName); assertEquals("minor", r.scale)
        assertEquals(0.55f, r.keyStrength, 1e-6f) // (0.9 + 0.2) / 2
    }

    @Test fun keyTie_breaksDeterministically() {
        val ws = listOf(feat(key = "D", keyStrength = 0.5f), feat(key = "C", keyStrength = 0.5f))
        assertEquals("C", FeatureAggregator.combine(ws, 900f).keyName)
        assertEquals("C", FeatureAggregator.combine(ws.reversed(), 900f).keyName)
    }

    @Test fun blankKeys_areIgnored_andAllBlankGivesEmpty() {
        val r = FeatureAggregator.combine(listOf(feat(key = ""), feat(key = "")), 900f)
        assertEquals("", r.keyName); assertEquals("", r.scale); assertEquals(0f, r.keyStrength, 0f)
        assertEquals("G", FeatureAggregator.combine(listOf(feat(key = ""), feat(key = "G")), 900f).keyName)
    }

    @Test fun loudness_isAPowerMean() {
        val r = FeatureAggregator.combine(listOf(feat(lufs = -10f), feat(lufs = -20f)), 900f)
        val expected = (10 * log10((10.0.pow(-1.0) + 10.0.pow(-2.0)) / 2)).toFloat()
        assertEquals(expected, r.loudnessLufs, 1e-4f)
        assertTrue(r.loudnessLufs > -15f) // louder than the arithmetic mean
    }

    @Test fun loudness_skipsNonFinite_andDefaultsToTheGateFloor() {
        assertEquals(-10f, FeatureAggregator.combine(listOf(feat(lufs = -10f), feat(lufs = Float.NEGATIVE_INFINITY)), 900f).loudnessLufs, 1e-5f)
        assertEquals(-70f, FeatureAggregator.combine(listOf(feat(lufs = Float.NaN), feat(lufs = Float.NEGATIVE_INFINITY)), 900f).loudnessLufs, 0f)
    }

    @Test fun scalarFeatures_areMeans_skippingNonFinite() {
        val r = FeatureAggregator.combine(
            listOf(feat(dance = 1f, energy = 0.1f, centroid = 1000f, onset = 2f, dyn = 2f, diss = 0.2f, zcr = 0.1f),
                feat(dance = 3f, energy = 0.3f, centroid = 2000f, onset = 4f, dyn = 4f, diss = 0.4f, zcr = 0.3f),
                feat(dance = Float.NaN, energy = Float.NaN, centroid = Float.NaN, onset = Float.NaN, dyn = Float.NaN, diss = Float.NaN, zcr = Float.NaN)),
            900f
        )
        assertEquals(2f, r.danceability, 1e-6f); assertEquals(0.2f, r.energy, 1e-6f)
        assertEquals(1500f, r.spectralCentroid, 1e-3f); assertEquals(3f, r.onsetRate, 1e-6f)
        assertEquals(3f, r.dynamicComplexity, 1e-6f); assertEquals(0.3f, r.dissonance, 1e-6f); assertEquals(0.2f, r.zcr, 1e-6f)
    }

    @Test fun mfcc_isTheElementWiseMean() {
        val r = FeatureAggregator.combine(listOf(feat(mfcc = FloatArray(13) { 2f }), feat(mfcc = FloatArray(13) { 4f })), 900f)
        assertArrayEquals(FloatArray(13) { 3f }, r.mfccMean, 1e-6f)
    }

    @Test fun resultCarriesNoFingerprint_theFullDuration_andTheConfigHash() {
        val r = FeatureAggregator.combine(listOf(feat(), feat()), 1234f)
        assertEquals(0, r.fingerprint.size); assertEquals(-1, r.fingerprintAlgorithm)
        assertEquals(1234f, r.duration, 0f); assertEquals("cfg", r.configHash)
    }

    @Test fun silentWindowAmongMusic_doesNotPoisonTheResult() {
        val silent = feat(bpm = 0f, conf = 0f, lufs = Float.NEGATIVE_INFINITY, dance = 0f, energy = 0f, mfcc = FloatArray(13))
        val r = FeatureAggregator.combine(listOf(feat(bpm = 128f), silent, feat(bpm = 128f)), 900f)
        assertEquals(128f, r.bpm, 0f)
        assertTrue(r.loudnessLufs.isFinite() && r.energy.isFinite())
    }

    @Test fun emptyList_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { FeatureAggregator.combine(emptyList(), 10f) }
    }
}

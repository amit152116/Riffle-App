package com.amit152116.riffle.data.db.cluster

import com.amit152116.riffle.data.db.analysis.AudioFeatures
import com.amit152116.riffle.intelligence.model.toLittleEndianBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IntelligenceMappingTest {
    private val goodMfcc = DoubleArray(13) { it + 0.5 }.toLittleEndianBytes()

    private fun features(
        mfcc: ByteArray? = goodMfcc,
        energy: Double? = 0.6,
        bpm: Double? = 121.0
    ) = AudioFeatures(
        audioContentId = 7, bpm = bpm, bpmConfidence = 0.9, keyName = "A", scale = "minor", keyStrength = 0.8,
        loudnessLufs = -12.0, danceability = 0.7, energy = energy, spectralCentroid = 2000.0, onsetRate = 4.0,
        dynamicComplexity = 3.0, dissonance = 0.2, zcr = 0.1, mfccMean = mfcc,
        extractorVersion = "ex-1", configHash = "cfg-1", analyzedAt = 1L
    )

    @Test fun validRow_mapsEveryField() {
        val r = features().toEligibleRecordingOrNull()!!
        assertEquals(7L, r.audioContentId)
        assertEquals(121.0, r.bpm!!, 0.0)
        assertEquals("A", r.keyName)
        assertEquals("minor", r.scale)
        assertEquals(0.6, r.energy, 0.0)
        assertEquals(2000.0, r.spectralCentroid!!, 0.0)
        assertEquals(12.5, r.mfcc[12], 0.0)
        assertEquals("ex-1", r.extractorVersion)
        assertEquals("cfg-1", r.featuresConfigHash)
    }

    @Test fun missingOrShortMfcc_isNull() {
        assertNull(features(mfcc = null).toEligibleRecordingOrNull())
        assertNull(features(mfcc = ByteArray(8)).toEligibleRecordingOrNull())
        assertNull(features(mfcc = ByteArray(105)).toEligibleRecordingOrNull())
    }

    @Test fun missingEnergy_isNull() = assertNull(features(energy = null).toEligibleRecordingOrNull())

    @Test fun nonFiniteValues_areNull() {
        assertNull(features(energy = Double.NaN).toEligibleRecordingOrNull())
        val nanMfcc = DoubleArray(13).also { it[3] = Double.NaN }.toLittleEndianBytes()
        assertNull(features(mfcc = nanMfcc).toEligibleRecordingOrNull())
    }

    @Test fun nullOrZeroBpm_isKept_thoughInvalid() {
        assertNotNull(features(bpm = null).toEligibleRecordingOrNull())
        assertEquals(false, features(bpm = 0.0).toEligibleRecordingOrNull()!!.hasValidBpm)
    }
}

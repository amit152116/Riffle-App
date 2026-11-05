package com.amit152116.riffle.data.db.analysis

import com.amit152116.riffle.data.db.FingerprintPurpose
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.dsp.AudioFeatures as DspAudioFeatures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AnalysisMappingTest {
    @Test
    fun packMfcc_encodesAsLittleEndianFloat64() {
        val packed = packMfcc(floatArrayOf(1.5f, -2.25f))

        val expected = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putDouble(1.5).putDouble(-2.25).array()
        assertArrayEquals(expected, packed)
    }

    @Test
    fun packFingerprint_encodesAsLittleEndianUInt32() {
        val packed = packFingerprint(intArrayOf(1, -1, 305419896))

        val expected = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(1).putInt(-1).putInt(305419896).array()
        assertArrayEquals(expected, packed)
    }

    @Test
    fun packMfcc_emptyArray_producesEmptyByteArray() {
        assertArrayEquals(ByteArray(0), packMfcc(FloatArray(0)))
    }

    @Test
    fun toRoomAudioFeatures_mapsAllFieldsAndConvertsMfccToBytes() {
        val dsp = DspAudioFeatures(
            bpm = 120f, bpmConfidence = 0.9f, keyName = "A", scale = "minor",
            keyStrength = 0.8f, loudnessLufs = -12.3f, danceability = 0.7f, energy = 0.6f,
            spectralCentroid = 2000f, onsetRate = 4f, dynamicComplexity = 3f, dissonance = 0.2f,
            zcr = 0.1f, mfccMean = floatArrayOf(1f, 2f), duration = 180f,
            fingerprint = intArrayOf(1, 2, 3), fingerprintAlgorithm = 2, configHash = "cfg-1"
        )

        val room = dsp.toRoomAudioFeatures(audioContentId = 42L, analyzedAt = 1_000L)

        assertEquals(42L, room.audioContentId)
        assertEquals(120.0, room.bpm)
        assertEquals("A", room.keyName)
        assertEquals(EssentiaExtractor.EXTRACTOR_VERSION, room.extractorVersion)
        assertEquals("cfg-1", room.configHash)
        assertArrayEquals(packMfcc(floatArrayOf(1f, 2f)), room.mfccMean)
        assertEquals(1_000L, room.analyzedAt)
    }

    @Test
    fun toFingerprint_mapsAllFieldsAndConvertsFingerprintToBytes() {
        val dsp = DspAudioFeatures(
            bpm = 120f, bpmConfidence = 0.9f, keyName = "A", scale = "minor",
            keyStrength = 0.8f, loudnessLufs = -12.3f, danceability = 0.7f, energy = 0.6f,
            spectralCentroid = 2000f, onsetRate = 4f, dynamicComplexity = 3f, dissonance = 0.2f,
            zcr = 0.1f, mfccMean = floatArrayOf(1f, 2f), duration = 180f,
            fingerprint = intArrayOf(1, 2, 3), fingerprintAlgorithm = 2, configHash = "cfg-1"
        )

        val fingerprint = dsp.toFingerprint(audioContentId = 42L, computedAt = 1_000L)!!

        assertEquals(42L, fingerprint.audioContentId)
        assertEquals(EssentiaExtractor.ANALYZER, fingerprint.analyzer)
        assertEquals(EssentiaExtractor.EXTRACTOR_VERSION, fingerprint.analyzerVersion)
        assertEquals("cfg-1", fingerprint.configHash)
        assertEquals(FingerprintPurpose.CANONICAL, fingerprint.purpose)
        assertEquals(2, fingerprint.algorithm)
        assertArrayEquals(packFingerprint(intArrayOf(1, 2, 3)), fingerprint.fingerprintRaw)
        assertEquals(3, fingerprint.fingerprintLength)
        assertEquals(1_000L, fingerprint.computedAt)
    }

    @Test
    fun toFingerprint_algorithmFailedSentinel_returnsNullInsteadOfPersistingEmptyFingerprint() {
        // EssentiaWrapper.cpp signals a chromaprint failure with fingerprintAlgorithm = -1 and
        // an empty fingerprint array ("not computed", not aborted - see runPipeline's catch
        // around computeChromaprint). Persisting that as a CANONICAL row would make
        // getAudioContentNeedingAnalysis treat this AudioContent as permanently fingerprinted,
        // even though it has no real fingerprint - it must stay pending instead.
        val dsp = DspAudioFeatures(
            bpm = 120f, bpmConfidence = 0.9f, keyName = "A", scale = "minor",
            keyStrength = 0.8f, loudnessLufs = -12.3f, danceability = 0.7f, energy = 0.6f,
            spectralCentroid = 2000f, onsetRate = 4f, dynamicComplexity = 3f, dissonance = 0.2f,
            zcr = 0.1f, mfccMean = floatArrayOf(1f, 2f), duration = 180f,
            fingerprint = intArrayOf(), fingerprintAlgorithm = -1, configHash = "cfg-1"
        )

        assertNull(dsp.toFingerprint(audioContentId = 42L, computedAt = 1_000L))
    }
}

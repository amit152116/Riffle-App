package com.amit152116.riffle.data.db.analysis

import com.amit152116.riffle.data.db.FingerprintPurpose
import com.amit152116.riffle.data.db.fingerprint.Fingerprint
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.dsp.AudioFeatures as DspAudioFeatures
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Little-endian float64 per value - matches ../riffle/riffle/store.py::pack_mfcc byte for byte. */
fun packMfcc(values: FloatArray): ByteArray {
    val buffer = ByteBuffer.allocate(values.size * 8).order(ByteOrder.LITTLE_ENDIAN)
    values.forEach { buffer.putDouble(it.toDouble()) }
    return buffer.array()
}

/** Little-endian uint32 per value - matches ../riffle/riffle/store.py::pack_fingerprint byte for byte. */
fun packFingerprint(values: IntArray): ByteArray {
    val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    values.forEach { buffer.putInt(it) }
    return buffer.array()
}

fun DspAudioFeatures.toRoomAudioFeatures(audioContentId: Long, analyzedAt: Long): AudioFeatures =
    AudioFeatures(
        audioContentId = audioContentId,
        bpm = bpm.toDouble(),
        bpmConfidence = bpmConfidence.toDouble(),
        keyName = keyName,
        scale = scale,
        keyStrength = keyStrength.toDouble(),
        loudnessLufs = loudnessLufs.toDouble(),
        danceability = danceability.toDouble(),
        energy = energy.toDouble(),
        spectralCentroid = spectralCentroid.toDouble(),
        onsetRate = onsetRate.toDouble(),
        dynamicComplexity = dynamicComplexity.toDouble(),
        dissonance = dissonance.toDouble(),
        zcr = zcr.toDouble(),
        mfccMean = packMfcc(mfccMean),
        extractorVersion = EssentiaExtractor.EXTRACTOR_VERSION,
        configHash = configHash,
        analyzedAt = analyzedAt
    )

/**
 * Null when chromaprint failed for this audio (EssentiaWrapper.cpp signals this with
 * fingerprintAlgorithm = -1 and an empty fingerprint array - "not computed", not aborted;
 * see runPipeline's catch around computeChromaprint). Persisting that as a real CANONICAL
 * row would make getAudioContentNeedingAnalysis treat this AudioContent as permanently
 * fingerprinted even though there's no real fingerprint - the caller must skip the insert
 * instead, so the row stays pending and gets retried on a future pass.
 */
fun DspAudioFeatures.toFingerprint(audioContentId: Long, computedAt: Long): Fingerprint? {
    if (fingerprintAlgorithm == -1) return null
    return Fingerprint(
        audioContentId = audioContentId,
        analyzer = EssentiaExtractor.ANALYZER,
        analyzerVersion = EssentiaExtractor.EXTRACTOR_VERSION,
        configHash = configHash,
        purpose = FingerprintPurpose.CANONICAL,
        algorithm = fingerprintAlgorithm,
        fingerprintRaw = packFingerprint(fingerprint),
        fingerprintLength = fingerprint.size,
        computedAt = computedAt
    )
}

package com.amit152116.riffle.data.db.cluster

import com.amit152116.riffle.data.db.analysis.AudioFeatures
import com.amit152116.riffle.intelligence.model.EligibleRecording
import com.amit152116.riffle.intelligence.model.toLittleEndianDoubles

/** Null when the row can't feed the engine: no/short MFCC blob, no energy, or any non-finite value. */
fun AudioFeatures.toEligibleRecordingOrNull(): EligibleRecording? {
    val bytes = mfccMean ?: return null
    if (bytes.size != EligibleRecording.MFCC_DIM * 8) return null
    val e = energy ?: return null
    if (!e.isFinite()) return null
    val mfcc = bytes.toLittleEndianDoubles()
    if (mfcc.any { !it.isFinite() }) return null
    return EligibleRecording(
        audioContentId = audioContentId, bpm = bpm, keyName = keyName, scale = scale, energy = e,
        danceability = danceability, loudnessLufs = loudnessLufs, spectralCentroid = spectralCentroid,
        onsetRate = onsetRate, mfcc = mfcc, extractorVersion = extractorVersion, featuresConfigHash = configHash
    )
}

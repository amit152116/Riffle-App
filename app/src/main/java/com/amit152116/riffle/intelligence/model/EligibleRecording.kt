package com.amit152116.riffle.intelligence.model

/** One recording (AudioContent) with the analysis features the engine consumes. */
class EligibleRecording(
    val audioContentId: Long,
    val bpm: Double?,
    val keyName: String?,
    val scale: String?,
    val energy: Double,
    val danceability: Double?,
    val loudnessLufs: Double?,
    val spectralCentroid: Double?,
    val onsetRate: Double?,
    val mfcc: DoubleArray,
    val extractorVersion: String?,
    val featuresConfigHash: String?
) {
    init {
        require(mfcc.size == MFCC_DIM) { "mfcc must have $MFCC_DIM values, got ${mfcc.size}" }
    }

    /** Essentia writes 0.0 (not NULL) for beatless/silent tracks, so <= 0 counts as missing. */
    val hasValidBpm: Boolean get() = bpm != null && bpm.isFinite() && bpm > 0.0

    companion object {
        const val MFCC_DIM = 13
    }
}

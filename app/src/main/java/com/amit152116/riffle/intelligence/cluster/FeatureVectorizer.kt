package com.amit152116.riffle.intelligence.cluster

import com.amit152116.riffle.intelligence.math.Stats
import com.amit152116.riffle.intelligence.model.EligibleRecording

/**
 * 19-d vector: bpm, energy, danceability, loudnessLufs, spectralCentroid, onsetRate, then 13 MFCC.
 * Key is deliberately NOT part of it (pitch class is circular; one-hot encoding distorts k-means).
 */
object FeatureVectorizer {
    const val DIM = 19
    const val MFCC_OFFSET = 6

    /** bpm, energy, danceability, loudness, onsetRate. */
    val MUSICAL_DIMS = intArrayOf(0, 1, 2, 3, 5)

    /** spectralCentroid (brightness) + 13 MFCC. */
    val TIMBRE_DIMS: IntArray = intArrayOf(4) + IntArray(13) { MFCC_OFFSET + it }

    class Vectorized(val n: Int, val data: DoubleArray, val medians: DoubleArray)

    /** Missing scalars (null, non-finite, BPM <= 0) are IMPUTED with the column median - not measured. */
    fun vectorize(recordings: List<EligibleRecording>): Vectorized {
        val n = recordings.size
        val medians = DoubleArray(DIM)
        for (c in 0 until MFCC_OFFSET) {
            val values = recordings.mapNotNull { scalar(it, c) }
            medians[c] = if (values.isEmpty()) 0.0 else Stats.median(values.toDoubleArray())
        }
        val data = DoubleArray(n * DIM)
        recordings.forEachIndexed { i, r ->
            for (c in 0 until MFCC_OFFSET) data[i * DIM + c] = scalar(r, c) ?: medians[c]
            for (d in 0 until EligibleRecording.MFCC_DIM) data[i * DIM + MFCC_OFFSET + d] = r.mfcc[d]
        }
        return Vectorized(n, data, medians)
    }

    private fun scalar(r: EligibleRecording, column: Int): Double? {
        val v: Double? = when (column) {
            0 -> if (r.hasValidBpm) r.bpm else null
            1 -> r.energy
            2 -> r.danceability
            3 -> r.loudnessLufs
            4 -> r.spectralCentroid
            5 -> r.onsetRate
            else -> null
        }
        return v?.takeIf { it.isFinite() }
    }
}

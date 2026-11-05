package com.amit152116.riffle.intelligence

import com.amit152116.riffle.intelligence.model.EligibleRecording
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object TestData {
    fun rec(
        id: Long,
        bpm: Double? = 120.0,
        key: String? = "C",
        scale: String? = "major",
        energy: Double = 0.5,
        danceability: Double? = 0.5,
        loudness: Double? = -10.0,
        centroid: Double? = 1500.0,
        onset: Double? = 3.0,
        mfcc: DoubleArray = angleMfcc(0.0),
        extractorVersion: String? = "ex-1",
        configHash: String? = "cfg-1"
    ) = EligibleRecording(
        id, bpm, key, scale, energy, danceability, loudness, centroid, onset, mfcc,
        extractorVersion, configHash
    )

    /** Cosine distance between two angleMfcc vectors depends only on the angle difference. */
    fun angleMfcc(degrees: Double): DoubleArray {
        val r = Math.toRadians(degrees)
        return DoubleArray(13).also { it[0] = cos(r); it[1] = sin(r) }
    }

    /**
     * blobCount well-separated groups of perBlob recordings. Ids run startId.. in blob order, so
     * the blob of a recording is (id - startId) / perBlob.
     */
    fun blobs(blobCount: Int, perBlob: Int, startId: Long = 1L, seed: Int = 1): List<EligibleRecording> {
        val rng = Random(seed)
        val out = ArrayList<EligibleRecording>()
        var id = startId
        for (b in 0 until blobCount) {
            repeat(perBlob) {
                // Every MFCC column must carry between-blob structure: standard scaling blows a
                // pure-noise column up to unit variance and would swamp the blob separation.
                val mfcc = DoubleArray(13) { d ->
                    10.0 * sin((b + 1) * (d + 1).toDouble()) + rng.nextDouble(-0.1, 0.1)
                }
                out += EligibleRecording(
                    audioContentId = id++,
                    bpm = 80.0 + 25.0 * b + rng.nextDouble(-1.0, 1.0),
                    keyName = "C",
                    scale = "major",
                    energy = 0.2 + 0.15 * b + rng.nextDouble(-0.01, 0.01),
                    danceability = 0.3 + 0.1 * b + rng.nextDouble(-0.01, 0.01),
                    loudnessLufs = -20.0 + 3.0 * b + rng.nextDouble(-0.2, 0.2),
                    spectralCentroid = 1000.0 + 500.0 * b + rng.nextDouble(-10.0, 10.0),
                    onsetRate = 2.0 + b + rng.nextDouble(-0.1, 0.1),
                    mfcc = mfcc,
                    extractorVersion = "ex-1",
                    featuresConfigHash = "cfg-1"
                )
            }
        }
        return out
    }
}

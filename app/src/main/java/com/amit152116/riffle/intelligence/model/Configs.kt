package com.amit152116.riffle.intelligence.model

import java.security.MessageDigest

fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun canonicalOf(prefix: String, vararg parts: Pair<String, Any?>): String =
    parts.joinToString(";") { (k, v) -> "$prefix.$k=$v" }

data class SimilarityConfig(
    val algorithmVersion: Int = 2,
    val mfccWeight: Double = 0.5,
    val bpmWeight: Double = 0.25,
    val keyWeight: Double = 0.15,
    val energyWeight: Double = 0.1,
    val topK: Int = 20,
    val bpmSpreadFloor: Double = 10.0,
    val bpmSpreadFallback: Double = 60.0,
    val bpmSpreadMinSamples: Int = 4,
    val keyDistanceDivisor: Double = 6.0,
    /** MFCC c0 is log-energy: ~98% of the vector norm on real libraries, so cosine ignores the timbre. */
    val mfccDropFirst: Boolean = true,
    val mfccScalePercentile: Double = 95.0,
    val energyRangeLowPercentile: Double = 10.0,
    val energyRangeHighPercentile: Double = 90.0,
    val calibrationMinRecordings: Int = 8,
    val calibrationMaxPairs: Int = 20_000,
    val calibrationSeed: Int = 17
) {
    /** Every parameter that changes results, in a fixed order, for the rebuild-gate hash. */
    fun canonical(): String = canonicalOf(
        "similarity",
        "algorithmVersion" to algorithmVersion,
        "mfccWeight" to mfccWeight,
        "bpmWeight" to bpmWeight,
        "keyWeight" to keyWeight,
        "energyWeight" to energyWeight,
        "topK" to topK,
        "bpmSpreadFloor" to bpmSpreadFloor,
        "bpmSpreadFallback" to bpmSpreadFallback,
        "bpmSpreadMinSamples" to bpmSpreadMinSamples,
        "keyDistanceDivisor" to keyDistanceDivisor,
        "mfccDropFirst" to mfccDropFirst,
        "mfccScalePercentile" to mfccScalePercentile,
        "energyRangeLowPercentile" to energyRangeLowPercentile,
        "energyRangeHighPercentile" to energyRangeHighPercentile,
        "calibrationMinRecordings" to calibrationMinRecordings,
        "calibrationMaxPairs" to calibrationMaxPairs,
        "calibrationSeed" to calibrationSeed,
        "mfccNormalisation" to "libraryStandardisedCosine",
        "bpmNormalisation" to "iqr",
        "keyDistanceMethod" to "circleOfFifths",
        "missingComponent" to "libraryNeutral",
        "excludeSameAudioContent" to true
    )
}

/**
 * What counts as music for similarity, clusters and shuffle. Real libraries carry ringtones, voice
 * notes and silent files: they become neighbour hubs, get shuffled, and make k-means spend clusters
 * on outliers. Unknown duration (null) is kept; the silence rule still applies.
 */
data class EligibilityConfig(
    val minDurationSeconds: Double = 30.0,
    val silenceLufsFloor: Double = -50.0
) {
    fun canonical(): String = canonicalOf(
        "eligibility",
        "minDurationSeconds" to minDurationSeconds,
        "silenceLufsFloor" to silenceLufsFloor
    )
}

data class ClusterConfig(
    val algorithmVersion: Int = 2,
    /** Standardised feature values are clipped to +-zClip so a handful of outliers cannot dominate. */
    val zClip: Double = 3.0,
    val musicalWeight: Double = 0.6,
    val timbreWeight: Double = 0.4,
    val seed: Int = 42,
    val nInit: Int = 10,
    val maxIter: Int = 300,
    val tolerance: Double = 1e-4,
    val kFloor: Int = 5,
    val kCeil: Int = 15,
    val minClusterSize: Int = 10,
    val silhouetteSampleCap: Int = 2000,
    val tercileLowPercentile: Double = 33.0,
    val tercileHighPercentile: Double = 66.0,
    val tercileMinSamples: Int = 6,
    val bpmTercileFallbackLow: Double = 80.0,
    val bpmTercileFallbackHigh: Double = 120.0,
    val energyTercileFallbackLow: Double = 0.3,
    val energyTercileFallbackHigh: Double = 0.7,
    val majorShareForMajor: Double = 0.6,
    val majorShareForMinor: Double = 0.4,
    val matchFactor: Double = 1.0
) {
    fun canonical(): String = canonicalOf(
        "cluster",
        "algorithmVersion" to algorithmVersion,
        "musicalWeight" to musicalWeight,
        "timbreWeight" to timbreWeight,
        "seed" to seed,
        "nInit" to nInit,
        "maxIter" to maxIter,
        "tolerance" to tolerance,
        "kFloor" to kFloor,
        "kCeil" to kCeil,
        "minClusterSize" to minClusterSize,
        "silhouetteSampleCap" to silhouetteSampleCap,
        "tercileLowPercentile" to tercileLowPercentile,
        "tercileHighPercentile" to tercileHighPercentile,
        "tercileMinSamples" to tercileMinSamples,
        "bpmTercileFallbackLow" to bpmTercileFallbackLow,
        "bpmTercileFallbackHigh" to bpmTercileFallbackHigh,
        "energyTercileFallbackLow" to energyTercileFallbackLow,
        "energyTercileFallbackHigh" to energyTercileFallbackHigh,
        "majorShareForMajor" to majorShareForMajor,
        "majorShareForMinor" to majorShareForMinor,
        "matchFactor" to matchFactor,
        "zClip" to zClip,
        "smallClusterPolicy" to "mergeIntoNearest",
        "imputation" to "columnMedian",
        "keyInVector" to false
    )
}

/** Not persisted or hashed: shuffle output is transient. */
data class ShuffleConfig(
    val temperature: Double = 0.1,
    val window: Int = 5,
    val artistWindow: Int = 3,
    val artistRepeatPenalty: Double = 0.5,
    val bpmJumpPenalty: Double = 0.3,
    val bpmJumpFactor: Double = 0.25,
    val keyHarmonyBonus: Double = 0.2,
    val keyHarmonyMaxDistance: Int = 1
)

package com.amit152116.riffle.intelligence.similarity

import com.amit152116.riffle.intelligence.math.Stats
import com.amit152116.riffle.intelligence.model.EligibleRecording
import com.amit152116.riffle.intelligence.model.EligibleRecording.Companion.MFCC_DIM
import com.amit152116.riffle.intelligence.model.SimilarityConfig
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Library-relative scales for the similarity score - no fixed thresholds.
 *
 * Raw MFCC means are dominated by c0 (log-energy, ~98% of the norm), so raw cosine distance between
 * any two songs is ~0.003 and the MFCC term (50% of the score on paper) contributes nothing. Here MFCC
 * is standardised per coefficient across the library, c0 is dropped, and the p95 pairwise distance is
 * what counts as "maximally different". Energy is scaled by the library's own p10-p90 range. A missing
 * BPM or key is imputed with the library's typical pair distance - neither dropped from the score
 * (which made BPM-less tracks look closer than real neighbours: a hub) nor treated as a perfect match.
 */
data class SimilarityCalibration(
    val mfccMean: DoubleArray,
    val mfccStd: DoubleArray,
    /** First MFCC coefficient used: 1 = c0 dropped, 0 = raw (tiny libraries and unit tests). */
    val mfccStartDim: Int,
    /** Cosine distance (of the standardised vectors) treated as maximally different. */
    val mfccScale: Double,
    /** |energy difference| treated as maximally different. */
    val energyScale: Double,
    /** |BPM difference| treated as maximally different. */
    val bpmSpread: Double,
    /** Normalised BPM distance used when either BPM is missing. */
    val bpmNeutral: Double,
    /** Normalised key distance used when either key is missing. */
    val keyNeutral: Double
) {
    val mfccDims: Int get() = MFCC_DIM - mfccStartDim

    companion object {
        private const val NEUTRAL_FLOOR = 0.05

        /** Reproduces the uncalibrated behaviour: raw MFCC cosine / 2, unscaled energy. */
        fun neutral(bpmSpread: Double = 60.0) = SimilarityCalibration(
            mfccMean = DoubleArray(MFCC_DIM),
            mfccStd = DoubleArray(MFCC_DIM) { 1.0 },
            mfccStartDim = 0,
            mfccScale = 2.0,
            energyScale = 1.0,
            bpmSpread = bpmSpread,
            bpmNeutral = 0.5,
            keyNeutral = 0.5
        )

        fun fit(recordings: List<EligibleRecording>, config: SimilarityConfig): SimilarityCalibration {
            val recs = recordings.sortedBy { it.audioContentId }
            val spread = BpmSpread.compute(recs, config)
            val n = recs.size
            if (n < config.calibrationMinRecordings) return neutral(spread)

            val mean = DoubleArray(MFCC_DIM)
            for (r in recs) for (d in 0 until MFCC_DIM) mean[d] += r.mfcc[d]
            for (d in 0 until MFCC_DIM) mean[d] /= n
            val std = DoubleArray(MFCC_DIM)
            for (r in recs) for (d in 0 until MFCC_DIM) {
                val x = r.mfcc[d] - mean[d]
                std[d] += x * x
            }
            for (d in 0 until MFCC_DIM) {
                val s = sqrt(std[d] / n)
                std[d] = if (s < 1e-9 || !s.isFinite()) 1.0 else s
            }

            val start = if (config.mfccDropFirst) 1 else 0
            val dims = MFCC_DIM - start
            val z = Array(n) { i -> DoubleArray(dims) { d -> (recs[i].mfcc[start + d] - mean[start + d]) / std[start + d] } }
            val norms = DoubleArray(n) { i -> sqrt(z[i].sumOf { it * it }) }

            val (pa, pb) = samplePairs(n, config)
            val mfccD = ArrayList<Double>(pa.size)
            val bpmD = ArrayList<Double>()
            val keyD = ArrayList<Double>()
            for (t in pa.indices) {
                val i = pa[t]
                val j = pb[t]
                if (norms[i] > 0.0 && norms[j] > 0.0) {
                    var dot = 0.0
                    for (d in 0 until dims) dot += z[i][d] * z[j][d]
                    mfccD += 1.0 - (dot / (norms[i] * norms[j])).coerceIn(-1.0, 1.0)
                }
                val a = recs[i]
                val b = recs[j]
                if (a.hasValidBpm && b.hasValidBpm) bpmD += min(abs(a.bpm!! - b.bpm!!) / spread, 1.0)
                KeyDistance.distance(a.keyName, a.scale, b.keyName, b.scale)?.let { keyD += min(it / config.keyDistanceDivisor, 1.0) }
            }

            val mfccScale = if (mfccD.isEmpty()) 1.0 else {
                Stats.percentileSorted(mfccD.sorted().toDoubleArray(), config.mfccScalePercentile).let { if (it > 1e-9) it else 1.0 }
            }
            val energies = recs.map { it.energy }.sorted().toDoubleArray()
            val energyScale = (Stats.percentileSorted(energies, config.energyRangeHighPercentile) -
                Stats.percentileSorted(energies, config.energyRangeLowPercentile)).coerceAtLeast(1e-3)

            fun neutralOf(v: List<Double>) = if (v.isEmpty()) 0.5 else Stats.median(v.toDoubleArray()).coerceAtLeast(NEUTRAL_FLOOR)
            return SimilarityCalibration(mean, std, start, mfccScale, energyScale, spread, neutralOf(bpmD), neutralOf(keyD))
        }

        /** All pairs when few enough, otherwise a deterministic random sample; never i == j. */
        private fun samplePairs(n: Int, config: SimilarityConfig): Pair<IntArray, IntArray> {
            val total = n.toLong() * (n - 1) / 2
            if (total <= config.calibrationMaxPairs) {
                val a = IntArray(total.toInt())
                val b = IntArray(total.toInt())
                var t = 0
                for (i in 0 until n) for (j in i + 1 until n) {
                    a[t] = i
                    b[t] = j
                    t++
                }
                return a to b
            }
            val rng = Random(config.calibrationSeed)
            val a = IntArray(config.calibrationMaxPairs)
            val b = IntArray(config.calibrationMaxPairs)
            for (t in a.indices) {
                val i = rng.nextInt(n)
                var j = rng.nextInt(n - 1)
                if (j >= i) j++
                a[t] = i
                b[t] = j
            }
            return a to b
        }
    }
}

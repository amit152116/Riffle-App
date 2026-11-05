package com.amit152116.riffle.intelligence.similarity

import com.amit152116.riffle.intelligence.model.SimilarityConfig
import kotlin.math.abs
import kotlin.math.min

/**
 * Each `*Distance` is a 0-1 normalised distance (not a feature value); lower = more similar. A null
 * bpm/key/energy distance means the component was missing for the pair and the library-neutral value
 * was used in `combined`.
 */
data class ScoreBreakdown(
    val mfccDistance: Double,
    val bpmDistance: Double?,
    val keyDistance: Double?,
    val energyDistance: Double?,
    val combined: Double
)

class SimilarityScorer(
    private val table: RecordingTable,
    private val config: SimilarityConfig,
    private val calibration: SimilarityCalibration
) {
    private val scratch = DoubleArray(4)
    private val weightSum = config.mfccWeight + config.bpmWeight + config.keyWeight + config.energyWeight

    /** Non-allocating hot path. NOT thread-safe: shares one scratch array. */
    fun combined(i: Int, j: Int): Double = compute(i, j, scratch)

    fun breakdown(i: Int, j: Int): ScoreBreakdown {
        val out = DoubleArray(4)
        val combined = compute(i, j, out)
        return ScoreBreakdown(
            mfccDistance = out[0],
            bpmDistance = out[1].takeUnless { it.isNaN() },
            keyDistance = out[2].takeUnless { it.isNaN() },
            energyDistance = out[3].takeUnless { it.isNaN() },
            combined = combined
        )
    }

    /** out = [mfcc, bpm, key, energy] distances, NaN where the neutral value stood in. */
    private fun compute(i: Int, j: Int, out: DoubleArray): Double {
        val mfccD = mfccDistance(i, j)
        out[0] = mfccD
        var total = config.mfccWeight * mfccD

        val bi = table.bpm[i]
        val bj = table.bpm[j]
        if (!bi.isNaN() && !bj.isNaN()) {
            val d = min(abs(bi - bj) / calibration.bpmSpread, 1.0)
            out[1] = d
            total += config.bpmWeight * d
        } else {
            out[1] = Double.NaN
            total += config.bpmWeight * calibration.bpmNeutral
        }

        val kd = KeyDistance.distance(table.keyPosition[i], table.keyPosition[j])
        if (kd != null) {
            val d = min(kd / config.keyDistanceDivisor, 1.0)
            out[2] = d
            total += config.keyWeight * d
        } else {
            out[2] = Double.NaN
            total += config.keyWeight * calibration.keyNeutral
        }

        val ed = min(abs(table.energy[i] - table.energy[j]) / calibration.energyScale, 1.0)
        out[3] = ed
        total += config.energyWeight * ed

        return total / weightSum
    }

    private fun mfccDistance(i: Int, j: Int): Double {
        val ni = table.mfccNorm[i]
        val nj = table.mfccNorm[j]
        if (ni == 0.0 || nj == 0.0) return ZERO_NORM_DISTANCE
        var dot = 0.0
        val dims = table.mfccDims
        val oi = i * dims
        val oj = j * dims
        for (d in 0 until dims) dot += table.mfcc[oi + d] * table.mfcc[oj + d]
        val cos = (dot / (ni * nj)).coerceIn(-1.0, 1.0)
        return min((1.0 - cos) / calibration.mfccScale, 1.0)
    }

    private companion object {
        /** A vector with no direction (silent audio) is neither similar nor maximally different. */
        const val ZERO_NORM_DISTANCE = 0.5
    }
}

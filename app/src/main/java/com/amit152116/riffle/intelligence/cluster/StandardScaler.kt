package com.amit152116.riffle.intelligence.cluster

import com.amit152116.riffle.intelligence.model.ClusterConfig
import kotlin.math.sqrt

class StandardScaler(val mean: DoubleArray, val std: DoubleArray) {
    val dim: Int get() = mean.size

    /** clip(z, +-clip) * factor with z = (x - mean) / std, row-major n x dim. */
    fun transform(data: DoubleArray, n: Int, factors: DoubleArray, clip: Double = Double.POSITIVE_INFINITY): DoubleArray {
        val out = DoubleArray(n * dim)
        for (i in 0 until n) for (d in 0 until dim) {
            out[i * dim + d] = ((data[i * dim + d] - mean[d]) / std[d]).coerceIn(-clip, clip) * factors[d]
        }
        return out
    }

    fun transformVector(vec: DoubleArray, factors: DoubleArray, clip: Double = Double.POSITIVE_INFINITY): DoubleArray {
        require(vec.size == dim) { "vector has ${vec.size} values, scaler expects $dim" }
        return DoubleArray(dim) { ((vec[it] - mean[it]) / std[it]).coerceIn(-clip, clip) * factors[it] }
    }

    companion object {
        /** Below this a column is treated as constant (floating-point noise would otherwise be amplified). */
        private const val CONSTANT_EPS = 1e-12

        fun fit(data: DoubleArray, n: Int, dim: Int): StandardScaler {
            require(n > 0) { "cannot fit a scaler on zero rows" }
            val mean = DoubleArray(dim)
            for (i in 0 until n) for (d in 0 until dim) mean[d] += data[i * dim + d]
            for (d in 0 until dim) mean[d] /= n
            val std = DoubleArray(dim)
            for (i in 0 until n) for (d in 0 until dim) {
                val diff = data[i * dim + d] - mean[d]
                std[d] += diff * diff
            }
            for (d in 0 until dim) {
                val s = sqrt(std[d] / n)
                std[d] = if (s < CONSTANT_EPS || !s.isFinite()) 1.0 else s
            }
            return StandardScaler(mean, std)
        }
    }
}

object GroupWeights {
    /**
     * Per-dimension multiplier applied after standard scaling. Weights are per GROUP and split
     * evenly within it, so a group's share of squared distance is its weight regardless of how
     * many dimensions it has (13 MFCC would otherwise carry 68% purely by count).
     */
    fun factors(config: ClusterConfig): DoubleArray {
        val f = DoubleArray(FeatureVectorizer.DIM)
        val musical = sqrt(config.musicalWeight / FeatureVectorizer.MUSICAL_DIMS.size)
        val timbre = sqrt(config.timbreWeight / FeatureVectorizer.TIMBRE_DIMS.size)
        FeatureVectorizer.MUSICAL_DIMS.forEach { f[it] = musical }
        FeatureVectorizer.TIMBRE_DIMS.forEach { f[it] = timbre }
        return f
    }
}

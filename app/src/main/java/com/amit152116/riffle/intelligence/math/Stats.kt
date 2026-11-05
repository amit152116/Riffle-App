package com.amit152116.riffle.intelligence.math

import kotlin.math.ceil
import kotlin.math.floor

object Stats {
    /** numpy's default (linear-interpolation) percentile of an already-sorted array. */
    fun percentileSorted(sorted: DoubleArray, p: Double): Double {
        require(sorted.isNotEmpty()) { "percentile of empty array" }
        if (sorted.size == 1) return sorted[0]
        val pos = p / 100.0 * (sorted.size - 1)
        val lo = floor(pos).toInt()
        val hi = ceil(pos).toInt()
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }

    fun mean(values: DoubleArray): Double {
        require(values.isNotEmpty()) { "mean of empty array" }
        return values.sum() / values.size
    }

    /** True median (averages the middle pair for even sizes). */
    fun median(values: DoubleArray): Double {
        require(values.isNotEmpty()) { "median of empty array" }
        val s = values.sortedArray()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }

    /** Python parity: `sorted(values)[len(values) // 2]` (no averaging). */
    fun upperMedian(values: DoubleArray): Double {
        require(values.isNotEmpty()) { "median of empty array" }
        return values.sortedArray()[values.size / 2]
    }
}

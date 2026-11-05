package com.amit152116.riffle.intelligence.cluster

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

object Silhouette {
    /**
     * Mean silhouette over at most `sampleCap` deterministically sampled points (exact when
     * n <= sampleCap); labels come from the full fit, distances are among sampled points only.
     * Null when fewer than two clusters are represented in the sample.
     */
    fun score(data: DoubleArray, n: Int, dim: Int, labels: IntArray, k: Int, sampleCap: Int, seed: Int): Double? {
        val idx: IntArray =
            if (n <= sampleCap) IntArray(n) { it }
            else (0 until n).shuffled(Random(seed)).take(sampleCap).sorted().toIntArray()
        val m = idx.size
        val counts = IntArray(k)
        idx.forEach { counts[labels[it]]++ }
        if (counts.count { it > 0 } < 2) return null

        var total = 0.0
        val sumDist = DoubleArray(k)
        for (a in 0 until m) {
            val i = idx[a]
            sumDist.fill(0.0)
            for (b in 0 until m) {
                if (a == b) continue
                sumDist[labels[idx[b]]] += dist(data, i, idx[b], dim)
            }
            val own = labels[i]
            if (counts[own] <= 1) continue // singleton contributes 0 (sklearn convention)
            val aVal = sumDist[own] / (counts[own] - 1)
            var bVal = Double.MAX_VALUE
            for (c in 0 until k) {
                if (c == own || counts[c] == 0) continue
                bVal = min(bVal, sumDist[c] / counts[c])
            }
            val denom = max(aVal, bVal)
            total += if (denom == 0.0) 0.0 else (bVal - aVal) / denom
        }
        return total / m
    }

    private fun dist(data: DoubleArray, i: Int, j: Int, dim: Int): Double {
        var s = 0.0
        for (d in 0 until dim) {
            val diff = data[i * dim + d] - data[j * dim + d]
            s += diff * diff
        }
        return sqrt(s)
    }
}

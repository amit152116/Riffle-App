package com.amit152116.riffle.intelligence.cluster

import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

class KMeansResult(val centroids: DoubleArray, val labels: IntArray, val inertia: Double, val k: Int)

object KMeans {
    /** k-means++ init, `nInit` seeded restarts, Lloyd iterations; best inertia wins. Deterministic. */
    fun fit(
        data: DoubleArray, n: Int, dim: Int, k: Int,
        seed: Int, nInit: Int, maxIter: Int, tolerance: Double
    ): KMeansResult {
        require(k in 1..n) { "k=$k must be in 1..$n" }
        var best: KMeansResult? = null
        for (run in 0 until nInit) {
            val result = fitOnce(data, n, dim, k, Random(seed + run * 7919), maxIter, tolerance)
            val b = best
            if (b == null || result.inertia < b.inertia) best = result
        }
        return best!!
    }

    private fun fitOnce(
        data: DoubleArray, n: Int, dim: Int, k: Int, rng: Random, maxIter: Int, tolerance: Double
    ): KMeansResult {
        val centroids = initPlusPlus(data, n, dim, k, rng)
        val labels = IntArray(n)
        val counts = IntArray(k)
        val sums = DoubleArray(k * dim)
        var iteration = 0
        while (true) {
            assign(data, n, dim, k, centroids, labels)
            counts.fill(0)
            sums.fill(0.0)
            for (i in 0 until n) {
                val l = labels[i]
                counts[l]++
                for (d in 0 until dim) sums[l * dim + d] += data[i * dim + d]
            }
            var maxShift = 0.0
            val reseeded = HashSet<Int>()
            for (c in 0 until k) {
                if (counts[c] > 0) {
                    var shift2 = 0.0
                    for (d in 0 until dim) {
                        val nv = sums[c * dim + d] / counts[c]
                        val diff = nv - centroids[c * dim + d]
                        shift2 += diff * diff
                        centroids[c * dim + d] = nv
                    }
                    maxShift = max(maxShift, sqrt(shift2))
                } else {
                    val far = farthestPoint(data, n, dim, centroids, labels, counts, reseeded)
                    if (far >= 0) {
                        for (d in 0 until dim) centroids[c * dim + d] = data[far * dim + d]
                        reseeded.add(far)
                        maxShift = Double.MAX_VALUE
                    }
                }
            }
            iteration++
            if (maxShift < tolerance || iteration >= maxIter) break
        }
        assign(data, n, dim, k, centroids, labels)
        var inertia = 0.0
        for (i in 0 until n) inertia += dist2(data, i, centroids, labels[i], dim)
        return KMeansResult(centroids, labels, inertia, k)
    }

    private fun initPlusPlus(data: DoubleArray, n: Int, dim: Int, k: Int, rng: Random): DoubleArray {
        val centroids = DoubleArray(k * dim)
        val first = rng.nextInt(n)
        for (d in 0 until dim) centroids[d] = data[first * dim + d]
        val minDist2 = DoubleArray(n) { dist2(data, it, centroids, 0, dim) }
        for (c in 1 until k) {
            val total = minDist2.sum()
            val chosen = if (total <= 0.0) {
                rng.nextInt(n)
            } else {
                var r = rng.nextDouble() * total
                var idx = 0
                while (idx < n - 1 && r > minDist2[idx]) {
                    r -= minDist2[idx]
                    idx++
                }
                idx
            }
            for (d in 0 until dim) centroids[c * dim + d] = data[chosen * dim + d]
            for (i in 0 until n) minDist2[i] = minOf(minDist2[i], dist2(data, i, centroids, c, dim))
        }
        return centroids
    }

    private fun assign(data: DoubleArray, n: Int, dim: Int, k: Int, centroids: DoubleArray, labels: IntArray) {
        for (i in 0 until n) {
            var best = 0
            var bestD = Double.MAX_VALUE
            for (c in 0 until k) {
                val dd = dist2(data, i, centroids, c, dim)
                if (dd < bestD) {
                    bestD = dd
                    best = c
                }
            }
            labels[i] = best
        }
    }

    private fun farthestPoint(
        data: DoubleArray, n: Int, dim: Int, centroids: DoubleArray, labels: IntArray,
        counts: IntArray, exclude: Set<Int>
    ): Int {
        var far = -1
        var farD = -1.0
        for (i in 0 until n) {
            if (i in exclude || counts[labels[i]] <= 1) continue
            val dd = dist2(data, i, centroids, labels[i], dim)
            if (dd > farD) {
                farD = dd
                far = i
            }
        }
        return far
    }

    private fun dist2(data: DoubleArray, i: Int, centroids: DoubleArray, c: Int, dim: Int): Double {
        var s = 0.0
        for (d in 0 until dim) {
            val diff = data[i * dim + d] - centroids[c * dim + d]
            s += diff * diff
        }
        return s
    }
}

package com.amit152116.riffle.intelligence.cluster

import kotlin.random.Random

/** Three 20-point blobs in 2-D at (0,0), (10,0), (0,10). truth[i] is the blob index. */
class Blobs2D(seed: Int = 1) {
    val n = 60
    val dim = 2
    val data = DoubleArray(n * dim)
    val truth = IntArray(n)

    init {
        val rng = Random(seed)
        val centers = listOf(0.0 to 0.0, 10.0 to 0.0, 0.0 to 10.0)
        for (b in 0..2) repeat(20) { i ->
            val idx = b * 20 + i
            data[idx * 2] = centers[b].first + rng.nextDouble(-0.3, 0.3)
            data[idx * 2 + 1] = centers[b].second + rng.nextDouble(-0.3, 0.3)
            truth[idx] = b
        }
    }
}

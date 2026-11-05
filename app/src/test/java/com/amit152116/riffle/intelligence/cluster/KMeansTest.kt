package com.amit152116.riffle.intelligence.cluster

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KMeansTest {
    private val b = Blobs2D()

    private fun fit(k: Int, seed: Int = 42) = KMeans.fit(b.data, b.n, b.dim, k, seed, 10, 300, 1e-4)

    @Test fun recoversWellSeparatedBlobs() {
        val r = fit(3)
        val perBlob = (0..2).map { blob -> (0 until 20).map { r.labels[blob * 20 + it] }.toSet() }
        assertTrue(perBlob.all { it.size == 1 })
        assertEquals(3, perBlob.flatten().toSet().size)
    }

    @Test fun sameSeed_isReproducible() {
        val a = fit(3)
        val c = fit(3)
        assertArrayEquals(a.labels, c.labels)
        assertArrayEquals(a.centroids, c.centroids, 0.0)
    }

    @Test fun kOne_centroidIsTheMean() {
        val r = fit(1)
        val meanX = (0 until b.n).sumOf { b.data[it * 2] } / b.n
        assertEquals(meanX, r.centroids[0], 1e-9)
    }

    @Test fun inertia_dropsAsKGrows() = assertTrue(fit(3).inertia < fit(2).inertia)

    @Test fun fewerDistinctPointsThanK_doesNotProduceNaN() {
        val data = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        val r = KMeans.fit(data, 6, 2, 3, 1, 5, 50, 1e-4)
        assertTrue(r.labels.all { it in 0..2 })
        assertTrue(r.centroids.all { it.isFinite() })
        assertTrue(r.inertia.isFinite())
    }

    @Test(expected = IllegalArgumentException::class)
    fun kGreaterThanN_isRejected() {
        KMeans.fit(doubleArrayOf(0.0, 0.0), 1, 2, 2, 1, 1, 10, 1e-4)
    }
}

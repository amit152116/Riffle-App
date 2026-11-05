package com.amit152116.riffle.intelligence.cluster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SilhouetteTest {
    private val b = Blobs2D()

    @Test fun separatedBlobs_scoreNearOne() =
        assertTrue(Silhouette.score(b.data, b.n, b.dim, b.truth, 3, 2000, 1)!! > 0.9)

    @Test fun mixedLabels_scoreLowerThanTruth() {
        val mixed = IntArray(b.n) { it % 3 }
        assertTrue(Silhouette.score(b.data, b.n, b.dim, mixed, 3, 2000, 1)!! < Silhouette.score(b.data, b.n, b.dim, b.truth, 3, 2000, 1)!!)
    }

    @Test fun singleCluster_isNull() =
        assertNull(Silhouette.score(b.data, b.n, b.dim, IntArray(b.n), 1, 2000, 1))

    @Test fun capAtOrAboveN_isExactAndIndependentOfCap() =
        assertEquals(
            Silhouette.score(b.data, b.n, b.dim, b.truth, 3, b.n, 1)!!,
            Silhouette.score(b.data, b.n, b.dim, b.truth, 3, 10_000, 9)!!,
            0.0
        )

    @Test fun sampling_isDeterministicForASeed_andCloseToExact() {
        val exact = Silhouette.score(b.data, b.n, b.dim, b.truth, 3, 10_000, 1)!!
        val s1 = Silhouette.score(b.data, b.n, b.dim, b.truth, 3, 30, 5)
        val s2 = Silhouette.score(b.data, b.n, b.dim, b.truth, 3, 30, 5)
        assertNotNull(s1)
        assertEquals(s1, s2)
        assertEquals(exact, s1!!, 0.1)
    }
}

package com.amit152116.riffle.intelligence.cluster

import com.amit152116.riffle.intelligence.TestData
import com.amit152116.riffle.intelligence.TestData.rec
import com.amit152116.riffle.intelligence.model.ClusterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterBuilderTest {
    private val cfg = ClusterConfig()
    private val builder = ClusterBuilder(cfg)

    private fun previousOf(r: ClusterResult) =
        PreviousRun(r.clusters.map { PreviousCentroid(it.clusterId, it.rawCentroid) }, r.nextClusterId)

    private fun idOf(r: ClusterResult, contentId: Long) = r.assignments.first { it.audioContentId == contentId }.clusterId

    @Test fun nineteenRecordings_isASingleClusterNamedAll() {
        val r = builder.build(TestData.blobs(1, 19))
        assertEquals(1, r.k)
        assertEquals("All", r.clusters.single().displayName)
        assertTrue(r.assignments.all { it.clusterId == r.clusters.single().clusterId })
        assertNull(r.silhouette)
    }

    @Test fun twentyRecordings_evaluatesOnlyK2() {
        val r = builder.build(TestData.blobs(2, 10))
        assertEquals(2, r.k)
        assertTrue(r.kScores.startsWith("2:"))
        assertEquals(1, r.kScores.split(",").size)
        assertEquals(listOf(10, 10), r.clusters.map { it.size })
    }

    @Test fun thirtyRecordings_evaluatesK2AndK3_andPicksThree() {
        val r = builder.build(TestData.blobs(3, 10))
        assertTrue(r.kScores.startsWith("2:") && r.kScores.contains(",3:"))
        assertEquals(3, r.k)
    }

    @Test fun fortyRecordings_evaluatesK2ToK4() {
        val r = builder.build(TestData.blobs(4, 10))
        assertEquals(3, r.kScores.split(",").size)
        assertEquals(4, r.k)
    }

    @Test fun hundredRecordings_startsAtTheFloorOfFive() {
        val r = builder.build(TestData.blobs(5, 20))
        assertTrue(r.kScores.startsWith("5"))
        assertTrue("kScores=${r.kScores}", r.kScores.split(",").none { it.startsWith("4") || it.startsWith("3") || it.startsWith("2") })
        assertEquals(5, r.k)
        assertNotNull(r.silhouette)
    }

    @Test fun undersizedOutlierGroup_isMergedIntoTheNearestCluster_insteadOfRejectingEveryK() {
        // The real-data failure: a few junk recordings (silent, ~zero features) made k-means give them a
        // cluster of their own; the min-size rule then rejected every k and the library collapsed to one cluster.
        val real = TestData.blobs(4, 12)
        val junk = (1..3).map {
            rec(900L + it, bpm = 60.0 + it, energy = 0.0, loudness = -70.0, centroid = 0.0, onset = 0.0,
                mfcc = DoubleArray(13) { d -> if (d == 0) -1264.0 else 0.0 })
        }
        val r = builder.build(real + junk) // n=51 -> maxK=5 -> k=5 finds 4 real groups + the junk group
        assertEquals(4, r.k)
        assertTrue(r.clusters.all { it.size >= 10 })
        assertTrue("kScores=${r.kScores}", r.kScores.contains("5→4:"))
        assertTrue(r.assignments.filter { it.audioContentId >= 900 }.map { it.clusterId }.toSet().size == 1)
    }

    @Test fun ifMergingLeavesOnlyOneCluster_theCandidateIsDiscarded() {
        val tight = TestData.blobs(1, 25)
        val far = (1..5).map { rec(100L + it, bpm = 200.0, energy = 0.95, mfcc = DoubleArray(13).also { m -> m[5] = 500.0 }) }
        val r = builder.build(tight + far)
        // whichever k survives, no cluster may be undersized and the result must be sane
        assertTrue(r.clusters.all { it.size >= 10 } || r.k == 1)
        assertTrue(r.assignments.all { it.distance.isFinite() })
    }

    @Test fun identicalRecordings_zeroVariance_doNotCrashOrProduceNaN() {
        val r = builder.build((1..25).map { rec(it.toLong()) })
        assertEquals(1, r.k)
        assertTrue(r.assignments.all { it.distance.isFinite() })
        assertTrue(r.scalerStd.all { it > 0.0 })
    }

    @Test fun singleRecording_isASingleCluster() {
        val r = builder.build(listOf(rec(1)))
        assertEquals(1, r.k)
        assertEquals(1, r.assignments.size)
        assertTrue(r.assignments.single().distance.isFinite())
    }

    @Test fun silentMfcc_doesNotProduceNaN() {
        val blobs = TestData.blobs(2, 10)
        val withSilent = blobs.mapIndexed { i, r -> if (i % 5 == 0) rec(r.audioContentId, mfcc = DoubleArray(13)) else r }
        val r = builder.build(withSilent)
        assertTrue(r.assignments.all { it.distance.isFinite() })
        assertTrue(r.clusters.all { c -> c.rawCentroid.all { it.isFinite() } })
    }

    @Test fun sameInput_isDeterministic() {
        val recs = TestData.blobs(4, 10)
        val a = builder.build(recs)
        val b = builder.build(recs.reversed())
        assertEquals(a.assignments.map { it.audioContentId to it.clusterId }, b.assignments.map { it.audioContentId to it.clusterId })
    }

    @Test fun freshRun_numbersClustersFromZero_andRecordsNextId() {
        val r = builder.build(TestData.blobs(4, 10))
        assertEquals(setOf(0, 1, 2, 3), r.clusters.map { it.clusterId }.toSet())
        assertEquals(4, r.nextClusterId)
        assertTrue(r.clusters.all { it.matchDistance == null })
    }

    @Test fun rerunWithDifferentSeed_keepsEveryClusterId() {
        val recs = TestData.blobs(4, 10)
        val first = builder.build(recs)
        val second = ClusterBuilder(cfg.copy(seed = 7)).build(recs, previousOf(first))
        recs.forEach { assertEquals(idOf(first, it.audioContentId), idOf(second, it.audioContentId)) }
        assertTrue(second.clusters.all { it.matchDistance != null })
        assertEquals(first.nextClusterId, second.nextClusterId)
    }

    @Test fun newBlob_getsAFreshId_andOldClustersKeepTheirs() {
        val old = TestData.blobs(4, 10)
        val first = builder.build(old)
        val extended = old + TestData.blobs(1, 10, startId = 500L, seed = 9).map {
            rec(it.audioContentId, bpm = 250.0, energy = 0.99, mfcc = DoubleArray(13).also { m -> m[12] = 300.0 })
        }
        val second = builder.build(extended, previousOf(first))
        old.forEach { assertEquals(idOf(first, it.audioContentId), idOf(second, it.audioContentId)) }
        val newId = idOf(second, 500L)
        assertTrue(newId >= first.nextClusterId)
        assertEquals(newId + 1, second.nextClusterId)
        assertNull(second.clusters.first { it.clusterId == newId }.matchDistance)
    }

    @Test fun matchAboveThreshold_isRejected_andIdsAreNeverReused() {
        val recs = TestData.blobs(4, 10)
        val absurd = PreviousRun(listOf(PreviousCentroid(3, DoubleArray(FeatureVectorizer.DIM) { 1e6 })), nextClusterId = 10)
        val r = builder.build(recs, absurd)
        assertEquals(setOf(10, 11, 12, 13), r.clusters.map { it.clusterId }.toSet())
        assertTrue(r.clusters.all { it.matchDistance == null })
        assertEquals(14, r.nextClusterId)
    }

    @Test fun previousRunWithNoCentroids_behavesLikeAFreshRun() {
        val r = builder.build(TestData.blobs(4, 10), PreviousRun(emptyList(), nextClusterId = 5))
        assertEquals(setOf(5, 6, 7, 8), r.clusters.map { it.clusterId }.toSet())
    }

    @Test fun labelsAreStructuredAndNamesUnique() {
        val r = builder.build(TestData.blobs(4, 10))
        assertEquals(r.clusters.size, r.clusters.map { it.displayName }.toSet().size)
        assertTrue(r.clusters.all { it.label.baseName.isNotBlank() })
    }
}

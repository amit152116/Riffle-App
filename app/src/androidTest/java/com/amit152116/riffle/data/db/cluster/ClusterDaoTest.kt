package com.amit152116.riffle.data.db.cluster

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.AbsentReason
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.library.LibraryDao
import com.amit152116.riffle.intelligence.model.ClusterMode
import com.amit152116.riffle.intelligence.model.EnergyBand
import com.amit152116.riffle.intelligence.model.TempoBand
import com.amit152116.riffle.intelligence.model.TimbreBand
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClusterDaoTest {
    private lateinit var db: RiffleDatabase
    private lateinit var dao: ClusterDao
    private lateinit var libraryDao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        dao = db.clusterDao()
        libraryDao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun content(hash: String, present: Boolean = true): Long {
        val id = libraryDao.insertAudioContent(TestFixtures.audioContent(audioHash = hash))
        libraryDao.insertTrack(
            TestFixtures.track(
                path = "/$hash.mp3", audioContentId = id, mediaStoreId = null, present = present,
                absentReason = if (present) null else AbsentReason.MISSING
            )
        )
        return id
    }

    private fun centroid(clusterId: Int) = ClusterCentroid(
        runId = 0, clusterId = clusterId, label = "L$clusterId", centroid = ByteArray(8), nTracks = 1
    )

    @Test
    fun insertClusterRunCentroidAndAssignment_roundTrips() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val runId = dao.insertClusterRun(TestFixtures.clusterRun())

        dao.insertClusterCentroid(
            ClusterCentroid(
                runId = runId, clusterId = 0, label = "Upbeat", centroid = ByteArray(8), nTracks = 10,
                tempoBand = TempoBand.UPBEAT, energyBand = EnergyBand.ENERGETIC,
                mode = ClusterMode.MIXED, timbre = TimbreBand.BRIGHT, matchDistance = 0.25
            )
        )
        dao.insertClusterAssignment(
            ClusterAssignment(runId = runId, audioContentId = contentId, clusterId = 0, distanceToCentroid = 0.1)
        )

        assertEquals(0, dao.getClusterAssignment(runId, contentId)!!.clusterId)
        val c = dao.getCentroids(runId).single()
        assertEquals(TempoBand.UPBEAT, c.tempoBand)
        assertEquals(ClusterMode.MIXED, c.mode)
        assertEquals(0.25, c.matchDistance!!, 0.0)
    }

    @Test
    fun clusterRun_newMetadataDefaultsToNullAndNextIdZero() = runBlocking {
        assertNull(dao.getLatestRun())
        dao.insertClusterRun(TestFixtures.clusterRun())
        val stored = dao.getLatestRun()!!
        assertNull(stored.inputHash)
        assertEquals(0, stored.nextClusterId)
    }

    @Test
    fun contentSimilarity_isDirected() = runBlocking {
        val a = content("a")
        val b = content("b")

        dao.insertContentSimilarity(TestFixtures.contentSimilarity(audioContentId = a, neighborContentId = b))

        assertNotNull(dao.getContentSimilarity(a, b))
        assertNull(dao.getContentSimilarity(b, a))
    }

    @Test
    fun getPresentNeighbors_ordersByScore_skipsAbsentRecordings_andAppliesLimit() = runBlocking {
        val a = content("a")
        val near = content("near")
        val far = content("far")
        val gone = content("gone", present = false)
        dao.insertContentSimilarities(
            listOf(
                TestFixtures.contentSimilarity(a, far, combinedScore = 0.5),
                TestFixtures.contentSimilarity(a, gone, combinedScore = 0.05),
                TestFixtures.contentSimilarity(a, near, combinedScore = 0.2)
            )
        )

        assertEquals(listOf(near, far), dao.getPresentNeighbors(a, 10).map { it.neighborContentId })
        assertEquals(listOf(near), dao.getPresentNeighbors(a, 1).map { it.neighborContentId })
    }

    @Test
    fun replaceIntelligence_replacesSimilarity_prunesOldChildren_butKeepsRunRows() = runBlocking {
        val a = content("a")
        val b = content("b")
        val run1 = dao.replaceIntelligence(
            null, TestFixtures.clusterRun(nClusters = 1),
            listOf(centroid(0)), listOf(ClusterAssignment(0, a, 0, 0.0)),
            listOf(TestFixtures.contentSimilarity(a, b))
        )

        val run2 = dao.replaceIntelligence(
            run1, TestFixtures.clusterRun(nClusters = 2),
            listOf(centroid(0), centroid(1)), listOf(ClusterAssignment(0, a, 1, 0.0), ClusterAssignment(0, b, 0, 0.0)),
            listOf(TestFixtures.contentSimilarity(b, a))
        )

        assertTrue(run2 > run1)
        assertEquals(2, dao.countRuns())
        assertEquals(run2, dao.getLatestRun()!!.id)
        assertTrue(dao.getCentroids(run1).isEmpty())
        assertTrue(dao.getAssignments(run1).isEmpty())
        assertEquals(2, dao.getCentroids(run2).size)
        assertEquals(2, dao.getAssignments(run2).size)
        assertNull(dao.getContentSimilarity(a, b))
        assertNotNull(dao.getContentSimilarity(b, a))
        assertEquals(1, dao.countContentSimilarities())
    }

    @Test
    fun replaceIntelligence_rollsBackEverythingWhenAnInsertFails() = runBlocking {
        val a = content("a")
        val b = content("b")
        val run1 = dao.replaceIntelligence(
            null, TestFixtures.clusterRun(),
            listOf(centroid(0)), listOf(ClusterAssignment(0, a, 0, 0.0)),
            listOf(TestFixtures.contentSimilarity(a, b))
        )

        try {
            dao.replaceIntelligence(
                run1, TestFixtures.clusterRun(nClusters = 9),
                listOf(centroid(0)), listOf(ClusterAssignment(0, a, 0, 0.0)),
                listOf(TestFixtures.contentSimilarity(a, 9_999L)) // FK violation on the last insert
            )
            fail("expected a foreign-key violation")
        } catch (e: Exception) {
            // expected: the transaction must have rolled back
        }

        assertEquals(run1, dao.getLatestRun()!!.id)
        assertEquals(1, dao.countRuns())
        assertEquals(1, dao.getCentroids(run1).size)
        assertEquals(1, dao.getAssignments(run1).size)
        assertNotNull(dao.getContentSimilarity(a, b))
    }
}

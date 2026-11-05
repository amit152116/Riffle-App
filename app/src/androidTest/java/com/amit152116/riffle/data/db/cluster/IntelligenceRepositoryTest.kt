package com.amit152116.riffle.data.db.cluster

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.AbsentReason
import com.amit152116.riffle.data.db.IntelligenceTestData
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntelligenceRepositoryTest {
    private lateinit var db: RiffleDatabase
    private lateinit var repo: IntelligenceRepository
    private val dao get() = db.clusterDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RiffleDatabase::class.java).build()
        repo = IntelligenceRepository(db.analysisDao(), db.clusterDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun rebuild_persistsRunCentroidsAssignmentsAndNeighbours() = runBlocking {
        IntelligenceTestData.insertBlobs(db, 4, 10)

        val result = repo.rebuildIfStale()

        assertTrue(result.rebuilt)
        assertEquals(40, result.nRecordings)
        assertEquals(4, result.nClusters)
        val run = dao.getLatestRun()!!
        assertEquals(40, run.nTracks)
        assertNotNull(run.inputHash)
        assertNotNull(run.configHash)
        assertNotNull(run.durationMs)
        assertEquals(4, run.nextClusterId)
        assertEquals(4, dao.getCentroids(run.id).size)
        assertEquals(40, dao.getAssignments(run.id).size)
        assertEquals(40 * 20, dao.countContentSimilarities())
        val c = dao.getCentroids(run.id).first()
        assertTrue(c.label!!.isNotBlank())
        assertNotNull(c.tempoBand)
        assertEquals(19 * 8, c.centroid!!.size)
    }

    @Test
    fun secondRebuildWithUnchangedData_isANoOp() = runBlocking {
        IntelligenceTestData.insertBlobs(db, 4, 10)
        repo.rebuildIfStale()

        val again = repo.rebuildIfStale()

        assertFalse(again.rebuilt)
        assertEquals(1, dao.countRuns())
    }

    @Test
    fun losingTheLastPresentTrack_changesTheHash_andRebuilds() = runBlocking {
        val ids = IntelligenceTestData.insertBlobs(db, 4, 10)
        repo.rebuildIfStale()
        val firstRun = dao.getLatestRun()!!
        val track = db.libraryDao().getTrackByPath("/h0-0-0.mp3")!!
        db.libraryDao().updateTrack(track.copy(present = false, absentReason = AbsentReason.MISSING))

        val result = repo.rebuildIfStale()

        assertTrue(result.rebuilt)
        assertEquals(39, result.nRecordings)
        assertEquals(2, dao.countRuns())
        assertTrue(dao.getCentroids(firstRun.id).isEmpty())
        assertTrue(dao.getAssignments(firstRun.id).isEmpty())
        assertNull(dao.getClusterAssignment(dao.getLatestRun()!!.id, ids.first()))
    }

    @Test
    fun copiesOfOneRecording_areOneNode() = runBlocking {
        IntelligenceTestData.insertBlobs(db, 4, 10)
        IntelligenceTestData.insertRecording(db, "multi", blob = 0, jitter = 0.05, copies = 5)

        assertEquals(41, repo.rebuildIfStale().nRecordings)
    }

    @Test
    fun newRecording_keepsExistingClusterIds() = runBlocking {
        IntelligenceTestData.insertBlobs(db, 4, 10)
        repo.rebuildIfStale()
        val before = dao.getCentroids(dao.getLatestRun()!!.id).map { it.clusterId }.toSet()
        IntelligenceTestData.insertRecording(db, "extra", blob = 0, jitter = 0.05)

        val result = repo.rebuildIfStale()

        assertTrue(result.rebuilt)
        assertEquals(before, dao.getCentroids(dao.getLatestRun()!!.id).map { it.clusterId }.toSet())
    }

    @Test
    fun emptyLibrary_isANoOp() = runBlocking {
        val result = repo.rebuildIfStale()
        assertFalse(result.rebuilt)
        assertNull(dao.getLatestRun())
    }

    @Test
    fun recordingsWithUnusableMfcc_areSkipped() = runBlocking {
        IntelligenceTestData.insertBlobs(db, 2, 10)
        val id = db.libraryDao().insertAudioContent(TestFixtures.audioContent(audioHash = "bad"))
        db.analysisDao().insertAudioFeatures(TestFixtures.audioFeatures(audioContentId = id, mfccMean = ByteArray(8)))
        db.libraryDao().insertTrack(TestFixtures.track(path = "/bad.mp3", audioContentId = id, mediaStoreId = null))

        assertEquals(20, repo.rebuildIfStale().nRecordings)
    }

    @Test
    fun singleRecording_isASingleClusterWithNoNeighbours() = runBlocking {
        IntelligenceTestData.insertRecording(db, "only", blob = 0)

        val result = repo.rebuildIfStale()

        assertTrue(result.rebuilt)
        assertEquals(1, result.nClusters)
        assertEquals(0, dao.countContentSimilarities())
        assertEquals("All", dao.getCentroids(dao.getLatestRun()!!.id).single().label)
    }
}

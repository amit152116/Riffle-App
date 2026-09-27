package com.amit152116.riffle.data.db.cluster

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.library.LibraryDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun insertClusterRunCentroidAndAssignment_roundTrips() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val runId = dao.insertClusterRun(TestFixtures.clusterRun())

        dao.insertClusterCentroid(
            ClusterCentroid(runId = runId, clusterId = 0, label = "Upbeat", centroid = ByteArray(8), nTracks = 10)
        )
        dao.insertClusterAssignment(
            ClusterAssignment(runId = runId, audioContentId = contentId, clusterId = 0, distanceToCentroid = 0.1)
        )

        val assignment = dao.getClusterAssignment(runId, contentId)
        assertNotNull(assignment)
        assertEquals(0, assignment!!.clusterId)
    }

    @Test
    fun insertTrackSimilarity_isDirected() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackA = libraryDao.insertTrack(TestFixtures.track(path = "/a.mp3", audioContentId = contentId))
        val trackB = libraryDao.insertTrack(TestFixtures.track(path = "/b.mp3", audioContentId = contentId))

        dao.insertTrackSimilarity(TestFixtures.trackSimilarity(trackId = trackA, neighborId = trackB))

        val forward = dao.getTrackSimilarity(trackA, trackB)
        val backward = dao.getTrackSimilarity(trackB, trackA)

        assertNotNull(forward)
        assertEquals(null, backward)
    }
}

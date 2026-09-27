package com.amit152116.riffle.data.local.cluster

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.local.RiffleDatabase
import com.amit152116.riffle.data.local.TestFixtures
import com.amit152116.riffle.data.local.library.LibraryDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClusterRepositoryTest {
    private lateinit var db: RiffleDatabase
    private lateinit var repository: ClusterRepository
    private lateinit var libraryDao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        repository = ClusterRepository(db.clusterDao())
        libraryDao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertTrackSimilarity_selfLoop_isRejected() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackId = libraryDao.insertTrack(TestFixtures.track(audioContentId = contentId))

        val inserted = repository.insertTrackSimilarity(
            TestFixtures.trackSimilarity(trackId = trackId, neighborId = trackId)
        )

        assertEquals(false, inserted)
        assertNull(repository.getTrackSimilarity(trackId, trackId))
    }
}

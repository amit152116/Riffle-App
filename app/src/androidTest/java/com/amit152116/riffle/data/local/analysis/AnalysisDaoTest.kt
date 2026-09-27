package com.amit152116.riffle.data.local.analysis

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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisDaoTest {
    private lateinit var db: RiffleDatabase
    private lateinit var dao: AnalysisDao
    private lateinit var libraryDao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        dao = db.analysisDao()
        libraryDao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndReplaceAudioFeatures_overwritesOnReanalysis() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        dao.insertAudioFeatures(TestFixtures.audioFeatures(audioContentId = contentId, bpm = 120.0))
        dao.insertAudioFeatures(TestFixtures.audioFeatures(audioContentId = contentId, bpm = 128.0))

        val loaded = dao.getAudioFeaturesByAudioContentId(contentId)

        assertNotNull(loaded)
        assertEquals(128.0, loaded!!.bpm)
    }

    @Test
    fun insertQualityFlag_roundTrips() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackId = libraryDao.insertTrack(TestFixtures.track(audioContentId = contentId))

        dao.insertQualityFlag(
            QualityFlag(
                audioContentId = contentId,
                trackId = trackId,
                peakDb = -1.0,
                meanDb = -12.0,
                clipping = true,
                lowVolume = false,
                silenceSections = 2,
                analyzedAt = 1_000L
            )
        )

        val loaded = dao.getQualityFlagByAudioContentId(contentId)
        assertNotNull(loaded)
        assertEquals(true, loaded!!.clipping)
        assertEquals(2, loaded.silenceSections)
    }
}

package com.amit152116.riffle.data.db.analysis

import com.amit152116.riffle.data.db.AbsentReason
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

    private suspend fun content(
        hash: String,
        present: Boolean = true,
        mfcc: ByteArray? = ByteArray(104),
        energy: Double? = 0.5,
        duration: Double? = 180.0,
        loudness: Double? = -12.0
    ): Long {
        val id = libraryDao.insertAudioContent(TestFixtures.audioContent(audioHash = hash, duration = duration))
        dao.insertAudioFeatures(TestFixtures.audioFeatures(audioContentId = id, mfccMean = mfcc, energy = energy, loudnessLufs = loudness))
        libraryDao.insertTrack(
            TestFixtures.track(
                path = "/$hash.mp3", audioContentId = id, mediaStoreId = null, present = present,
                absentReason = if (present) null else AbsentReason.MISSING
            )
        )
        return id
    }

    @Test
    fun getEligibleFeatures_requiresMfccEnergyAndAPresentTrack_orderedByContentId() = runBlocking {
        val ok2 = content("ok2")
        val ok1 = content("ok1")
        content("gone", present = false)
        content("noMfcc", mfcc = null)
        content("noEnergy", energy = null)

        assertEquals(listOf(ok2, ok1).sorted(), dao.getEligibleFeatures(30.0, -50.0).map { it.audioContentId })
    }

    @Test
    fun getEligibleFeatures_excludesShortAndSilentAudio_butKeepsUnknownDuration() = runBlocking {
        val music = content("music")
        val unknownDuration = content("unknownDuration", duration = null)
        val exactlyMin = content("exactlyMin", duration = 30.0)
        content("tooShort", duration = 29.9)
        content("zeroLength", duration = 0.0)
        content("silent", loudness = -70.0)
        content("atFloor", loudness = -50.0) // floor itself is excluded: strictly louder than the floor is music
        val unknownLoudness = content("unknownLoudness", loudness = null)

        assertEquals(
            setOf(music, unknownDuration, exactlyMin, unknownLoudness),
            dao.getEligibleFeatures(30.0, -50.0).map { it.audioContentId }.toSet()
        )
    }

    @Test
    fun getEligibleFeatures_thresholdsAreParameters() = runBlocking {
        val id = content("clip", duration = 20.0)
        assertEquals(emptyList<Long>(), dao.getEligibleFeatures(30.0, -50.0).map { it.audioContentId })
        assertEquals(listOf(id), dao.getEligibleFeatures(10.0, -50.0).map { it.audioContentId })
    }

    @Test
    fun getEligibleFeatures_countsARecordingOnceEvenWithManyPresentTracks() = runBlocking {
        val id = content("dup")
        repeat(3) { i ->
            libraryDao.insertTrack(TestFixtures.track(path = "/dup-extra-$i.mp3", audioContentId = id, mediaStoreId = null))
        }
        assertEquals(1, dao.getEligibleFeatures(30.0, -50.0).size)
    }
}

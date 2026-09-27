package com.amit152116.riffle.data.local.fingerprint

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
class FingerprintDaoTest {
    private lateinit var db: RiffleDatabase
    private lateinit var dao: FingerprintDao
    private lateinit var libraryDao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        dao = db.fingerprintDao()
        libraryDao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndReadFingerprint_roundTrips() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val fpId = dao.insertFingerprint(TestFixtures.fingerprint(audioContentId = contentId))

        val loaded = dao.getFingerprintById(fpId)

        assertNotNull(loaded)
        assertEquals(contentId, loaded!!.audioContentId)
        assertEquals(3, loaded.fpRaw.size)
    }

    @Test
    fun insertRunTrack_compositeKeyRoundTrips() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackId = libraryDao.insertTrack(TestFixtures.track(audioContentId = contentId))
        val runId = dao.insertMatchRun(TestFixtures.matchRun())

        dao.insertRunTrack(
            RunTrack(
                runId = runId,
                trackId = trackId,
                path = "/storage/emulated/0/Music/song.mp3",
                size = 4_000_000L,
                mtime = 1_700_000_000.0,
                audioHash = "hash-1",
                hashMethod = com.amit152116.riffle.data.local.HashMethod.STREAMHASH
            )
        )

        val loaded = dao.getRunTrack(runId, trackId)
        assertNotNull(loaded)
        assertEquals("hash-1", loaded!!.audioHash)
    }
}

package com.amit152116.riffle.data.db.library

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDaoTest {
    private lateinit var db: RiffleDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        dao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndReadTrack_roundTrips() = runBlocking {
        val contentId = dao.insertAudioContent(TestFixtures.audioContent())
        val trackId = dao.insertTrack(TestFixtures.track(audioContentId = contentId))

        val loaded = dao.getTrackById(trackId)

        assertNotNull(loaded)
        assertEquals("/storage/emulated/0/Music/song.mp3", loaded!!.path)
        assertEquals(contentId, loaded.audioContentId)
    }

    @Test
    fun insertTrack_duplicatePath_throws() {
        runBlocking { dao.insertTrack(TestFixtures.track(path = "/same/path.mp3")) }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertTrack(TestFixtures.track(path = "/same/path.mp3")) }
        }
    }

    @Test
    fun insertAudioContent_duplicateHash_throws() {
        runBlocking { dao.insertAudioContent(TestFixtures.audioContent(audioHash = "dup-hash")) }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertAudioContent(TestFixtures.audioContent(audioHash = "dup-hash")) }
        }
    }

    @Test
    fun insertTrack_withUnknownAudioContentId_throwsForeignKeyViolation() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertTrack(TestFixtures.track(audioContentId = 999_999L)) }
        }
    }

    @Test
    fun insertIngestError_roundTrips() = runBlocking {
        dao.insertIngestError(TestFixtures.ingestError())

        val loaded = dao.getIngestErrorByPath("/storage/emulated/0/Music/broken.mp3")

        assertNotNull(loaded)
        assertEquals("tag_read", loaded!!.stage)
    }
}

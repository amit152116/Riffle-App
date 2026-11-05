package com.amit152116.riffle.data.db.library

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.HashMethod
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.playback.entities.BlacklistedTrack
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun getTracksNeedingHash_excludesHashedAndAbsentTracks() = runBlocking {
        val contentId = dao.insertAudioContent(TestFixtures.audioContent())
        dao.insertTrack(TestFixtures.track(path = "/hashed.mp3", audioContentId = contentId))
        dao.insertTrack(TestFixtures.track(path = "/pending.mp3", audioContentId = null))
        dao.insertTrack(TestFixtures.track(path = "/absent.mp3", audioContentId = null, present = false))

        val pending = dao.getTracksNeedingHash()

        assertEquals(1, pending.size)
        assertEquals("/pending.mp3", pending[0].path)
    }

    @Test
    fun getPresentTracks_excludesAbsentAndBelowMinDuration() = runBlocking {
        dao.insertTrack(TestFixtures.track(path = "/present-long.mp3", durationMs = 60_000L))
        dao.insertTrack(TestFixtures.track(path = "/present-short.mp3", durationMs = 5_000L))
        dao.insertTrack(TestFixtures.track(path = "/absent.mp3", present = false))

        val tracks = dao.getPresentTracks(minDurationMs = 30_000L)

        assertEquals(1, tracks.size)
        assertEquals("/present-long.mp3", tracks[0].path)
    }

    @Test
    fun getPresentTracks_excludesBlacklistedTracks() = runBlocking {
        val keptId = dao.insertTrack(TestFixtures.track(path = "/kept.mp3"))
        dao.insertTrack(TestFixtures.track(path = "/blacklisted.mp3"))
        val blacklistedId = dao.getTrackByPath("/blacklisted.mp3")!!.id

        db.playbackDao().insertBlacklisted(BlacklistedTrack(trackId = blacklistedId, addedAt = 1_000L))

        val tracks = dao.getPresentTracks()

        assertEquals(1, tracks.size)
        assertEquals(keptId, tracks[0].id)
    }

    @Test
    fun getPresentTracks_ordersByTitleCaseInsensitive() = runBlocking {
        dao.insertTrack(TestFixtures.track(path = "/b.mp3", tagTitle = "banana"))
        dao.insertTrack(TestFixtures.track(path = "/a.mp3", tagTitle = "Apple"))
        dao.insertTrack(TestFixtures.track(path = "/c.mp3", tagTitle = "cherry"))

        val tracks = dao.getPresentTracks()

        assertEquals(listOf("Apple", "banana", "cherry"), tracks.map { it.tagTitle })
    }

    @Test
    fun getAnyPresentTrackPath_pathTieBreak_picksAlphabeticallySmallest() = runBlocking {
        val contentId = dao.insertAudioContent(TestFixtures.audioContent())
        dao.insertTrack(TestFixtures.track(path = "/z-track.mp3", audioContentId = contentId))
        dao.insertTrack(TestFixtures.track(path = "/a-track.mp3", audioContentId = contentId))

        assertEquals("/a-track.mp3", dao.getAnyPresentTrackPath(contentId))
    }

    @Test
    fun getAnyPresentTrackPath_noPresentSiblings_returnsNull() = runBlocking {
        val contentId = dao.insertAudioContent(TestFixtures.audioContent())
        dao.insertTrack(TestFixtures.track(path = "/absent.mp3", audioContentId = contentId, present = false))

        assertNull(dao.getAnyPresentTrackPath(contentId))
    }

    @Test
    fun getAudioContentNeedingAnalysis_returnsRowMissingBothTables() = runBlocking {
        val contentId = dao.insertAudioContent(TestFixtures.audioContent())
        dao.insertTrack(TestFixtures.track(path = "/pending.mp3", audioContentId = contentId))

        val pending = dao.getAudioContentNeedingAnalysis(extractorVersion = "v1", configHash = "cfg-1")

        assertEquals(1, pending.size)
        assertEquals(contentId, pending[0].id)
    }

    @Test
    fun getAudioContentNeedingAnalysis_excludesRowsWithBothTablesAtCurrentVersion() = runBlocking {
        val contentId = dao.insertAudioContent(TestFixtures.audioContent())
        dao.insertTrack(TestFixtures.track(path = "/done.mp3", audioContentId = contentId))
        db.analysisDao().insertAudioFeatures(
            TestFixtures.audioFeatures(audioContentId = contentId, extractorVersion = "v1", configHash = "cfg-1")
        )
        db.fingerprintDao().insertFingerprint(
            TestFixtures.fingerprint(audioContentId = contentId, analyzerVersion = "v1", configHash = "cfg-1")
        )

        val pending = dao.getAudioContentNeedingAnalysis(extractorVersion = "v1", configHash = "cfg-1")

        assertEquals(0, pending.size)
    }

    @Test
    fun getAudioContentNeedingAnalysis_versionBump_reincludesRow() = runBlocking {
        val contentId = dao.insertAudioContent(TestFixtures.audioContent())
        dao.insertTrack(TestFixtures.track(path = "/stale.mp3", audioContentId = contentId))
        db.analysisDao().insertAudioFeatures(
            TestFixtures.audioFeatures(audioContentId = contentId, extractorVersion = "v1", configHash = "cfg-1")
        )
        db.fingerprintDao().insertFingerprint(
            TestFixtures.fingerprint(audioContentId = contentId, analyzerVersion = "v1", configHash = "cfg-1")
        )

        val pending = dao.getAudioContentNeedingAnalysis(extractorVersion = "v2", configHash = "cfg-2")

        assertEquals(1, pending.size)
    }

    @Test
    fun getAudioContentNeedingAnalysis_onlyAbsentTracks_excludesRow() = runBlocking {
        val contentId = dao.insertAudioContent(TestFixtures.audioContent())
        dao.insertTrack(TestFixtures.track(path = "/absent.mp3", audioContentId = contentId, present = false))

        val pending = dao.getAudioContentNeedingAnalysis(extractorVersion = "v1", configHash = "cfg-1")

        assertEquals(0, pending.size)
    }

    @Test
    fun getOrCreateAudioContentIdAndAssign_newHash_insertsAndAssigns() = runBlocking {
        val track = TestFixtures.track(path = "/new.mp3")
        dao.insertTrack(track)
        val inserted = dao.getTrackByPath("/new.mp3")!!

        val contentId = dao.getOrCreateAudioContentIdAndAssign(inserted, TestFixtures.audioContent(audioHash = "new-hash"))

        val loaded = dao.getTrackByPath("/new.mp3")
        assertEquals(contentId, loaded!!.audioContentId)
        assertNotNull(dao.getAudioContentByHash("new-hash", HashMethod.STREAMHASH))
    }

    @Test
    fun getOrCreateAudioContentIdAndAssign_repeatHash_reusesExistingRowNotDuplicate() = runBlocking {
        val existingContentId = dao.insertAudioContent(TestFixtures.audioContent(audioHash = "shared-hash"))
        dao.insertTrack(TestFixtures.track(path = "/first.mp3", audioContentId = existingContentId))
        dao.insertTrack(TestFixtures.track(path = "/second.mp3"))
        val second = dao.getTrackByPath("/second.mp3")!!

        val contentId = dao.getOrCreateAudioContentIdAndAssign(second, TestFixtures.audioContent(audioHash = "shared-hash"))

        assertEquals(existingContentId, contentId)
        assertEquals(existingContentId, dao.getTrackByPath("/second.mp3")!!.audioContentId)
    }
}

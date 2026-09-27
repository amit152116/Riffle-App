package com.amit152116.riffle.data.local.dedup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.local.DupDecision
import com.amit152116.riffle.data.local.QuarantineState
import com.amit152116.riffle.data.local.RiffleDatabase
import com.amit152116.riffle.data.local.TestFixtures
import com.amit152116.riffle.data.local.fingerprint.FingerprintDao
import com.amit152116.riffle.data.local.library.LibraryDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DedupDaoTest {
    private lateinit var db: RiffleDatabase
    private lateinit var dao: DedupDao
    private lateinit var libraryDao: LibraryDao
    private lateinit var fingerprintDao: FingerprintDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        dao = db.dedupDao()
        libraryDao = db.libraryDao()
        fingerprintDao = db.fingerprintDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertDupGroupAndMembers_roundTrips() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackId = libraryDao.insertTrack(TestFixtures.track(audioContentId = contentId))
        val runId = fingerprintDao.insertMatchRun(TestFixtures.matchRun())
        val groupId = dao.insertDupGroup(TestFixtures.dupGroup(runId = runId))

        dao.insertGroupContent(GroupContent(groupId = groupId, audioContentId = contentId))
        dao.insertGroupMember(GroupMember(groupId = groupId, trackId = trackId, isKeeper = true, rankScore = "1.0"))

        val group = dao.getDupGroupById(groupId)
        val member = dao.getGroupMember(groupId, trackId)

        assertNotNull(group)
        assertEquals(DupDecision.PROPOSED, group!!.decision)
        assertNotNull(member)
        assertEquals(true, member!!.isKeeper)
    }

    @Test
    fun insertQuarantineLog_roundTrips() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackId = libraryDao.insertTrack(TestFixtures.track(audioContentId = contentId))
        val runId = fingerprintDao.insertMatchRun(TestFixtures.matchRun())
        val groupId = dao.insertDupGroup(TestFixtures.dupGroup(runId = runId))

        val logId = dao.insertQuarantineLog(
            QuarantineLog(
                runId = runId,
                trackId = trackId,
                groupId = groupId,
                srcPath = "/music/dup.mp3",
                dstPath = "/music/.quarantine/dup.mp3",
                srcHash = "h1",
                dstHash = "h1",
                movedAt = 1_000L,
                state = QuarantineState.MOVED
            )
        )

        val loaded = dao.getQuarantineLogById(logId)
        assertNotNull(loaded)
        assertEquals(QuarantineState.MOVED, loaded!!.state)
    }
}

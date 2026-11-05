package com.amit152116.riffle.data.db.dedup

import org.junit.Assert.assertTrue
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.DupDecision
import com.amit152116.riffle.data.db.QuarantineState
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.fingerprint.FingerprintDao
import com.amit152116.riffle.data.db.library.LibraryDao
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

    private suspend fun content(hash: String) =
        libraryDao.insertAudioContent(TestFixtures.audioContent(audioHash = hash))

    private suspend fun group(runId: Long, decision: DupDecision, vararg members: Long) {
        val gid = dao.insertDupGroup(TestFixtures.dupGroup(runId, decision = decision))
        members.forEach { dao.insertGroupContent(GroupContent(gid, it)) }
    }

    @Test
    fun exclusions_comeOnlyFromApprovedOrAppliedGroups() = runBlocking {
        val runId = fingerprintDao.insertMatchRun(TestFixtures.matchRun())
        val a = content("a"); val b = content("b"); val c = content("c")
        val d = content("d"); val e = content("e"); val f = content("f")
        group(runId, DupDecision.APPROVED, a, b, c)
        group(runId, DupDecision.APPLIED, d, e)
        group(runId, DupDecision.PROPOSED, f, a)
        group(runId, DupDecision.REJECTED, f, b)

        val exclusions = DedupRepository(dao).getDuplicateExclusions()

        assertEquals(setOf(b, c), exclusions.getValue(a))
        assertEquals(setOf(a, c), exclusions.getValue(b))
        assertEquals(setOf(e), exclusions.getValue(d))
        assertTrue(f !in exclusions)
    }

    @Test
    fun exclusions_areEmptyWithoutAnyGroups() = runBlocking {
        assertTrue(DedupRepository(dao).getDuplicateExclusions().isEmpty())
    }
}

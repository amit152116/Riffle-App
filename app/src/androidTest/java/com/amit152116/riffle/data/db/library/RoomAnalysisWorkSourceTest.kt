package com.amit152116.riffle.data.db.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomAnalysisWorkSourceTest {
    private lateinit var db: RiffleDatabase
    private lateinit var repo: LibraryRepository
    private val version = "v"
    private val cfg = "c"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RiffleDatabase::class.java).build()
        repo = LibraryRepository(db.libraryDao())
    }

    @After fun tearDown() { db.close() }

    private suspend fun content(hash: String, durationSec: Double?, path: String = "content://media/$hash", present: Boolean = true): Long {
        val id = db.libraryDao().insertAudioContent(TestFixtures.audioContent(audioHash = hash, duration = durationSec))
        db.libraryDao().insertTrack(
            TestFixtures.track(path = path, audioContentId = id, mediaStoreId = hash.hashCode().toLong(), present = present)
        )
        return id
    }

    private fun source(max: Int = 3) = RoomAnalysisWorkSource(repo, version, cfg, max)

    @Test fun returnsUnanalysedRecordings_withDurations_inIdOrder_andHonoursTheLimit() = runBlocking {
        val a = content("a", 200.0); val b = content("b", null); val c = content("c", 1500.0)
        val all = source().nextPending(10)
        assertEquals(listOf(a, b, c), all.map { it.audioContentId })
        assertEquals(listOf<Double?>(200.0, null, 1500.0), all.map { it.durationSec })
        assertEquals(listOf(a, b), source().nextPending(2).map { it.audioContentId })
    }

    @Test fun recordingsWithoutAPresentTrack_areNotPending() = runBlocking {
        content("gone", 100.0, present = false)
        assertTrue(source().nextPending(10).isEmpty())
    }

    @Test fun recordingsWhoseAnalyzeErrorReachedTheCap_areExcluded_butBelowTheCapAreNot() = runBlocking {
        content("a", 100.0, path = "content://a"); val b = content("b", 100.0, path = "content://b")
        repo.insertIngestError(TestFixtures.ingestError(path = "content://a", stage = "analyze", attempts = 3))
        repo.insertIngestError(TestFixtures.ingestError(path = "content://b", stage = "analyze", attempts = 2))
        assertEquals(listOf(b), source().nextPending(10).map { it.audioContentId })
    }

    @Test fun resolvedErrors_andOtherStages_doNotExclude() = runBlocking {
        val a = content("a", 100.0, path = "content://a"); val b = content("b", 100.0, path = "content://b")
        repo.insertIngestError(TestFixtures.ingestError(path = "content://a", stage = "analyze", attempts = 5, resolvedAt = 9L))
        repo.insertIngestError(TestFixtures.ingestError(path = "content://b", stage = "scan", attempts = 5))
        assertEquals(listOf(a, b), source().nextPending(10).map { it.audioContentId })
    }

    @Test fun aFailingRecording_becomesExcludedAfterExactlyMaxAttempts() = runBlocking {
        val id = content("f", 100.0, path = "content://f")
        val s = source(max = 3)
        repeat(3) {
            assertEquals("before failure #${it + 1}", listOf(id), s.nextPending(10).map { r -> r.audioContentId })
            s.recordFailure(id, "content://f", "boom")
        }
        assertTrue(s.nextPending(10).isEmpty())
        assertEquals(3, repo.getIngestErrorByPath("content://f")!!.attempts)
    }

    @Test fun aLaterRealFailure_overwritesAnInterruptionRow_soTheCountsDoNotMix() = runBlocking {
        val id = content("m", 100.0, path = "content://m")
        repo.insertIngestError(TestFixtures.ingestError(path = "content://m", stage = ANALYZE_INTERRUPTED_STAGE, attempts = 2))
        source().recordFailure(id, "content://m", "boom")
        val row = repo.getIngestErrorByPath("content://m")!!
        assertEquals(ANALYZE_STAGE, row.stage); assertEquals(1, row.attempts)
    }

    @Test fun anAnalysedRecording_isNoLongerPending() = runBlocking {
        val id = content("ok", 100.0)
        db.analysisDao().insertAudioFeatures(TestFixtures.audioFeatures(audioContentId = id, extractorVersion = version, configHash = cfg))
        db.fingerprintDao().insertFingerprint(TestFixtures.fingerprint(audioContentId = id, analyzerVersion = version, configHash = cfg))
        assertTrue(source().nextPending(10).isEmpty())
    }
}

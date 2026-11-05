package com.amit152116.riffle.data.db.query

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.DupDecision
import com.amit152116.riffle.data.db.IntelligenceTestData
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.cluster.IntelligenceRepository
import com.amit152116.riffle.data.db.dedup.DedupRepository
import com.amit152116.riffle.data.db.dedup.GroupContent
import com.amit152116.riffle.data.db.playback.entities.BlacklistedTrack
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShuffleRepositoryTest {
    private lateinit var db: RiffleDatabase
    private lateinit var intelligence: IntelligenceRepository
    private lateinit var shuffle: ShuffleRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RiffleDatabase::class.java).build()
        intelligence = IntelligenceRepository(db.analysisDao(), db.clusterDao())
        shuffle = ShuffleRepository(
            TrackFilterRepository(db.trackFilterDao(), db.clusterDao()),
            intelligence, db.clusterDao(), DedupRepository(db.dedupDao())
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun ready(blobs: Int = 4, per: Int = 10): List<Long> =
        IntelligenceTestData.insertBlobs(db, blobs, per).also { intelligence.rebuildIfStale() }

    @Test
    fun returnsUniqueRecordingsUpToTheRequestedLength() = runBlocking {
        ready()
        val steps = shuffle.smartShuffle(ShuffleRequest(length = 15, randomSeed = 1))
        assertEquals(15, steps.size)
        assertEquals(15, steps.map { it.audioContentId }.toSet().size)
        assertTrue(steps.all { db.libraryDao().getTrackById(it.trackId) != null })
    }

    @Test
    fun theSecondTrack_comesFromTheStoredNeighbours() = runBlocking {
        val ids = ready()
        val seed = ids.first()
        val steps = shuffle.smartShuffle(ShuffleRequest(seedAudioContentId = seed, length = 2, randomSeed = 3))
        val neighbours = db.clusterDao().getPresentNeighbors(seed, 20).map { it.neighborContentId }
        assertTrue(steps[1].audioContentId in neighbours)
    }

    @Test
    fun seedOutsideTheFilter_throws() {
        val ids = runBlocking { ready() }
        val seedFromOtherBlob = ids.last()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                // blob 0 has bpm ~80-81; the seed is from blob 3 (bpm ~155)
                shuffle.smartShuffle(ShuffleRequest(filter = TrackFilter(bpmMax = 100.0), seedAudioContentId = seedFromOtherBlob))
            }
        }
    }

    @Test
    fun emptyFilterResult_returnsEmpty() = runBlocking {
        ready()
        assertTrue(shuffle.smartShuffle(ShuffleRequest(filter = TrackFilter(bpmMin = 10_000.0))).isEmpty())
    }

    @Test
    fun genreFilterRestrictsThePool_evenWhenStoredNeighboursLieOutsideIt() = runBlocking {
        for (b in 0 until 4) repeat(10) { i ->
            IntelligenceTestData.insertRecording(
                db, "g$b-$i", b, jitter = (i % 10) / 10.0, genre = if (b % 2 == 0) "Hindi" else "Rock"
            )
        }
        intelligence.rebuildIfStale()

        val steps = shuffle.smartShuffle(ShuffleRequest(filter = TrackFilter(genre = "hindi"), length = 100, randomSeed = 2))

        assertEquals(20, steps.size)
        assertTrue(steps.all { db.libraryDao().getTrackById(it.trackId)!!.tagGenre == "Hindi" })
    }

    @Test
    fun approvedDuplicateGroup_neverPlaysBothMembers() = runBlocking {
        val ids = ready()
        val (a, b) = ids[0] to ids[1]
        val runId = db.fingerprintDao().insertMatchRun(TestFixtures.matchRun())
        val gid = db.dedupDao().insertDupGroup(TestFixtures.dupGroup(runId, decision = DupDecision.APPROVED))
        db.dedupDao().insertGroupContent(GroupContent(gid, a))
        db.dedupDao().insertGroupContent(GroupContent(gid, b))

        for (seed in 1L..5L) {
            val played = shuffle.smartShuffle(ShuffleRequest(length = 40, randomSeed = seed)).map { it.audioContentId }
            assertFalse("seed $seed played both", a in played && b in played)
        }
    }

    @Test
    fun blacklistedTracksNeverAppear() = runBlocking {
        val ids = ready()
        val track = db.libraryDao().getTrackByPath("/h0-0-0.mp3")!!
        db.playbackDao().insertBlacklisted(BlacklistedTrack(track.id, 1L))

        val steps = shuffle.smartShuffle(ShuffleRequest(length = 100, randomSeed = 1))

        assertEquals(ids.size - 1, steps.size)
        assertTrue(steps.none { it.trackId == track.id })
    }

    @Test
    fun lengthLargerThanThePool_returnsEachRecordingOnce() = runBlocking {
        ready()
        val steps = shuffle.smartShuffle(ShuffleRequest(length = 500, randomSeed = 4))
        assertEquals(40, steps.size)
        assertEquals(40, steps.map { it.audioContentId }.toSet().size)
    }

    @Test
    fun poolOfOne_returnsThatRecording() = runBlocking {
        val ids = ready()
        val steps = shuffle.smartShuffle(ShuffleRequest(filter = TrackFilter(audioContentIds = setOf(ids[5])), length = 10))
        assertEquals(listOf(ids[5]), steps.map { it.audioContentId })
    }

    @Test
    fun withoutAnyAnalysis_returnsEmpty() = runBlocking {
        assertTrue(shuffle.smartShuffle(ShuffleRequest()).isEmpty())
    }
}

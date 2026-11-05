package com.amit152116.riffle.data.db.query

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.AbsentReason
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.cluster.ClusterAssignment
import com.amit152116.riffle.data.db.cluster.ClusterCentroid
import com.amit152116.riffle.data.db.playback.entities.BlacklistedTrack
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackFilterDaoTest {
    private lateinit var db: RiffleDatabase
    private lateinit var repo: TrackFilterRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        repo = TrackFilterRepository(db.trackFilterDao(), db.clusterDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** One recording with one Track per entry of [genres]; returns the AudioContent id. */
    private suspend fun recording(
        hash: String,
        bpm: Double? = 120.0,
        keyName: String? = "C",
        scale: String? = "major",
        energy: Double? = 0.5,
        genres: List<String?> = listOf("Rock"),
        present: List<Boolean> = genres.map { true },
        artist: String? = "Artist $hash",
        title: String? = hash,
        mfcc: ByteArray? = ByteArray(104),
        duration: Double? = 180.0,
        loudness: Double? = -10.0
    ): Long {
        val id = db.libraryDao().insertAudioContent(TestFixtures.audioContent(audioHash = hash, duration = duration))
        db.analysisDao().insertAudioFeatures(
            TestFixtures.audioFeatures(
                audioContentId = id, bpm = bpm, keyName = keyName, scale = scale, energy = energy, mfccMean = mfcc,
                loudnessLufs = loudness
            )
        )
        genres.forEachIndexed { i, g ->
            db.libraryDao().insertTrack(
                TestFixtures.track(
                    path = "/$hash-$i.mp3", audioContentId = id, mediaStoreId = null, tagGenre = g,
                    tagArtist = artist, tagTitle = title, present = present[i],
                    absentReason = if (present[i]) null else AbsentReason.MISSING
                )
            )
        }
        return id
    }

    private suspend fun trackId(path: String) = db.libraryDao().getTrackByPath(path)!!.id
    private suspend fun titles(filter: TrackFilter) = repo.query(filter).map { it.track.tagTitle }

    @Test
    fun oneRowPerRecording_usingTheLowestPresentTrackId() = runBlocking {
        recording("x", genres = listOf("Rock", "Jazz"))
        val rows = repo.query(TrackFilter())
        assertEquals(1, rows.size)
        assertEquals("/x-0.mp3", rows.single().track.path)
    }

    @Test
    fun absentTracks_andFullyAbsentRecordings_areExcluded() = runBlocking {
        recording("a", genres = listOf("Rock", "Rock"), present = listOf(false, true))
        recording("gone", genres = listOf("Rock"), present = listOf(false))
        val rows = repo.query(TrackFilter())
        assertEquals(listOf("/a-1.mp3"), rows.map { it.track.path })
    }

    @Test
    fun blacklistedTracksAreExcluded() = runBlocking {
        recording("two", genres = listOf("Rock", "Rock"))
        recording("one", genres = listOf("Rock"))
        db.playbackDao().insertBlacklisted(BlacklistedTrack(trackId("/two-0.mp3"), 1L))
        db.playbackDao().insertBlacklisted(BlacklistedTrack(trackId("/one-0.mp3"), 1L))

        val rows = repo.query(TrackFilter())

        // "two": the lowest-id copy is blacklisted, so the other copy represents it; "one": every copy is blacklisted.
        assertEquals(listOf("/two-1.mp3"), rows.map { it.track.path })
    }

    @Test
    fun recordingsWithoutEligibleFeatures_areExcluded() = runBlocking {
        recording("ok")
        recording("noMfcc", mfcc = null)
        recording("noEnergy", energy = null)
        assertEquals(listOf("ok"), titles(TrackFilter()))
    }

    @Test
    fun shortAndSilentRecordings_areNotMusic_andAreExcluded() = runBlocking {
        recording("song")
        recording("ringtone", duration = 3.0)
        recording("voiceNote", duration = 12.0)
        recording("silence", loudness = -70.0)
        recording("unknownLength", duration = null)
        assertEquals(setOf("song", "unknownLength"), titles(TrackFilter()).toSet())
    }

    @Test
    fun genreFilter_isCaseInsensitiveSubstring_onTheRepresentativeTrack() = runBlocking {
        recording("lowestIsRock", genres = listOf("Rock", "Hindi")) // representative = Rock
        recording("hindiPop", genres = listOf("Hindi Pop"))
        assertEquals(listOf("hindiPop"), titles(TrackFilter(genre = "hindi")))
    }

    @Test
    fun genreFilter_escapesLikeWildcards() = runBlocking {
        recording("rnb", genres = listOf("R&B/Soul"))
        recording("hits", genres = listOf("100%_Hits"))
        recording("dnb", genres = listOf("Drum_n_Bass"))
        recording("rock", genres = listOf("Rock"))
        assertEquals(listOf("hits"), titles(TrackFilter(genre = "100%_")))
        assertEquals(listOf("hits"), titles(TrackFilter(genre = "%")))
        assertEquals(setOf("hits", "dnb"), titles(TrackFilter(genre = "_")).toSet())
        assertEquals(listOf("rnb"), titles(TrackFilter(genre = "R&B/")))
    }

    @Test
    fun bpmRange_isMinInclusive_maxExclusive() = runBlocking {
        recording("a", bpm = 100.0)
        recording("b", bpm = 120.0)
        recording("c", bpm = 130.0)
        assertEquals(setOf("a", "b"), titles(TrackFilter(bpmMin = 100.0, bpmMax = 130.0)).toSet())
    }

    @Test
    fun keyScaleAndEnergyFilters() = runBlocking {
        recording("aMinor", keyName = "A", scale = "minor", energy = 0.3)
        recording("aMajor", keyName = "A", scale = "major", energy = 0.9)
        assertEquals(listOf("aMinor"), titles(TrackFilter(keyName = "A", scale = "minor")))
        assertEquals(listOf("aMajor"), titles(TrackFilter(energyMin = 0.5)))
        assertEquals(listOf("aMinor"), titles(TrackFilter(energyMax = 0.5)))
    }

    @Test
    fun bpmSort_putsNullAndZeroLast_inBothDirections() = runBlocking {
        recording("slow", bpm = 90.0)
        recording("fast", bpm = 150.0)
        recording("zero", bpm = 0.0)
        recording("nullBpm", bpm = null)
        assertEquals(listOf("slow", "fast"), titles(TrackFilter(sort = TrackSort.BPM)).take(2))
        assertEquals(listOf("fast", "slow"), titles(TrackFilter(sort = TrackSort.BPM, direction = SortDirection.DESC)).take(2))
        assertEquals(setOf("zero", "nullBpm"), titles(TrackFilter(sort = TrackSort.BPM, direction = SortDirection.DESC)).takeLast(2).toSet())
    }

    @Test
    fun clusterFilter_usesTheLatestRunOnly() = runBlocking {
        val a = recording("a")
        val b = recording("b")
        val dao = db.clusterDao()
        val run1 = dao.insertClusterRun(TestFixtures.clusterRun())
        dao.insertClusterAssignment(ClusterAssignment(run1, a, 3, 0.0))
        val run2 = dao.insertClusterRun(TestFixtures.clusterRun())
        dao.insertClusterCentroid(ClusterCentroid(runId = run2, clusterId = 3, label = "x", centroid = null, nTracks = 1))
        dao.insertClusterAssignment(ClusterAssignment(run2, b, 3, 0.0))

        val rows = repo.query(TrackFilter(clusterId = 3))
        assertEquals(listOf("b"), rows.map { it.track.tagTitle })
        assertEquals(3, rows.single().clusterId)
    }

    @Test
    fun clusterFilter_withNoRuns_returnsNothing() = runBlocking {
        recording("a")
        assertTrue(repo.query(TrackFilter(clusterId = 1)).isEmpty())
    }

    @Test
    fun limitAndOffset_page_theSortedResult() = runBlocking {
        listOf("a", "b", "c").forEach { recording(it, artist = it) }
        assertEquals(listOf("b", "c"), titles(TrackFilter(sort = TrackSort.ARTIST, limit = 2, offset = 1)))
    }

    @Test
    fun explicitRecordingIds_restrictTheResult() = runBlocking {
        val a = recording("a")
        recording("b")
        assertEquals(listOf("a"), titles(TrackFilter(audioContentIds = setOf(a))))
        assertTrue(titles(TrackFilter(audioContentIds = emptySet())).isEmpty())
    }

    @Test
    fun results_carryTheRecordingsFeatures() = runBlocking {
        recording("a", bpm = 128.0, keyName = "F#", scale = "minor", energy = 0.7)
        val row = repo.query(TrackFilter()).single()
        assertEquals(128.0, row.bpm!!, 0.0)
        assertEquals("F#", row.keyName)
        assertEquals("minor", row.scale)
        assertEquals(0.7, row.energy!!, 0.0)
    }
}

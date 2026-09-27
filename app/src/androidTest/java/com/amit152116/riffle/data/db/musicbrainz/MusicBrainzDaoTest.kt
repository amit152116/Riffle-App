package com.amit152116.riffle.data.db.musicbrainz

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicBrainzDaoTest {
    private lateinit var db: RiffleDatabase
    private lateinit var dao: MusicBrainzDao
    private lateinit var libraryDao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        dao = db.musicBrainzDao()
        libraryDao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertMatchWithArtists_roundTrips() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val matchId = dao.insertMusicBrainzMatch(TestFixtures.musicBrainzMatch(audioContentId = contentId))

        dao.insertMbArtist(MbArtist(mbid = "artist-1", name = "Artist One"))
        dao.insertMbArtist(MbArtist(mbid = "artist-2", name = "Artist Two"))
        dao.insertMbRecordingArtist(MbRecordingArtist(matchId = matchId, position = 0, artistMbid = "artist-1"))
        dao.insertMbRecordingArtist(MbRecordingArtist(matchId = matchId, position = 1, artistMbid = "artist-2"))

        val artists = dao.getArtistsForMatch(matchId)

        assertEquals(2, artists.size)
        assertEquals("Artist One", artists[0].name)
        assertEquals("Artist Two", artists[1].name)
    }

    @Test
    fun upsertMusicBrainzMatch_reTag_preservesRowIdAndArtistLinks() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val firstMatchId = dao.upsertMusicBrainzMatch(
            TestFixtures.musicBrainzMatch(audioContentId = contentId, recordingMbid = "recording-mbid-1")
        )
        dao.insertMbArtist(MbArtist(mbid = "artist-1", name = "Artist One"))
        dao.insertMbRecordingArtist(MbRecordingArtist(matchId = firstMatchId, position = 0, artistMbid = "artist-1"))

        // Re-tag the same audio content (e.g. re-running match with a better
        // AcoustID result). A REPLACE-based insert would delete the old row
        // (id firstMatchId) and insert a new one under a new id, either
        // throwing a foreign key violation on the still-attached
        // MbRecordingArtist row (FK enforcement is on) or orphaning it.
        val secondMatchId = dao.upsertMusicBrainzMatch(
            TestFixtures.musicBrainzMatch(audioContentId = contentId, recordingMbid = "recording-mbid-2")
        )

        assertEquals(firstMatchId, secondMatchId)
        val updated = dao.getMatchByAudioContentId(contentId)
        assertEquals("recording-mbid-2", updated!!.recordingMbid)
        // The artist link from before the re-tag must survive untouched.
        val artists = dao.getArtistsForMatch(firstMatchId)
        assertEquals(1, artists.size)
        assertEquals("Artist One", artists[0].name)
    }

    @Test
    fun upsertMusicBrainzMatchWithArtists_reTag_replacesArtistList() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        dao.insertMbArtist(MbArtist(mbid = "artist-1", name = "Artist One"))
        dao.insertMbArtist(MbArtist(mbid = "artist-2", name = "Artist Two"))

        val matchId = dao.upsertMusicBrainzMatchWithArtists(
            TestFixtures.musicBrainzMatch(audioContentId = contentId),
            listOf("artist-1")
        )
        val reTaggedId = dao.upsertMusicBrainzMatchWithArtists(
            TestFixtures.musicBrainzMatch(audioContentId = contentId),
            listOf("artist-2")
        )

        assertEquals(matchId, reTaggedId)
        val artists = dao.getArtistsForMatch(matchId)
        assertEquals(listOf("Artist Two"), artists.map { it.name })
    }

    @Test
    fun acoustidCache_sharedByTwoAudioContents() = runBlocking {
        val contentA = libraryDao.insertAudioContent(TestFixtures.audioContent(audioHash = "a"))
        val contentB = libraryDao.insertAudioContent(TestFixtures.audioContent(audioHash = "b"))

        dao.insertAcoustidCache(AcoustidCache(lookupKey = "fp-key-1", responseJson = "{}", fetchedAt = 1_000L))
        dao.insertAcoustidCacheContent(AcoustidCacheContent(lookupKey = "fp-key-1", audioContentId = contentA))
        dao.insertAcoustidCacheContent(AcoustidCacheContent(lookupKey = "fp-key-1", audioContentId = contentB))

        val linkedContentIds = dao.getAudioContentIdsForLookupKey("fp-key-1")

        assertEquals(listOf(contentA, contentB), linkedContentIds.sorted())
    }
}

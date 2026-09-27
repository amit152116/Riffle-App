package com.amit152116.riffle.data.db.playback

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
class PlaybackDaoTest {
    private lateinit var db: RiffleDatabase
    private lateinit var dao: PlaybackDao
    private lateinit var libraryDao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        dao = db.playbackDao()
        libraryDao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertPlayHistory_roundTrips() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackId = libraryDao.insertTrack(TestFixtures.track(audioContentId = contentId))

        dao.insertPlayHistory(PlayHistory(trackId = trackId, playedAt = 1_000L, durationPlayedMs = 180_000L))
        dao.insertPlayHistory(PlayHistory(trackId = trackId, playedAt = 2_000L, durationPlayedMs = 180_000L))

        val count = dao.getPlayCount(trackId)
        assertEquals(2, count)
    }

    @Test
    fun favoriteTrack_addAndCheck() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackId = libraryDao.insertTrack(TestFixtures.track(audioContentId = contentId))

        dao.insertFavorite(FavoriteTrack(trackId = trackId, addedAt = 1_000L))

        assertNotNull(dao.getFavorite(trackId))
    }

    @Test
    fun playlistTrack_allowsDuplicateTrack() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackId = libraryDao.insertTrack(TestFixtures.track(audioContentId = contentId))
        val playlistId = dao.insertPlaylist(TestFixtures.playlist())

        dao.insertPlaylistTrack(PlaylistTrack(playlistId = playlistId, trackId = trackId, position = 0))
        dao.insertPlaylistTrack(PlaylistTrack(playlistId = playlistId, trackId = trackId, position = 1))

        val entries = dao.getPlaylistTracks(playlistId)
        assertEquals(2, entries.size)
    }
}

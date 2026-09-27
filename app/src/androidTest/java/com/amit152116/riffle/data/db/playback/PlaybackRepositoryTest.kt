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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackRepositoryTest {
    private lateinit var db: RiffleDatabase
    private lateinit var repository: PlaybackRepository
    private lateinit var libraryDao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        repository = PlaybackRepository(db.playbackDao())
        libraryDao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun removeFromPlaylist_renumbersTrailingPositionsContiguously() = runBlocking {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackA = libraryDao.insertTrack(TestFixtures.track(path = "/a.mp3", audioContentId = contentId))
        val trackB = libraryDao.insertTrack(TestFixtures.track(path = "/b.mp3", audioContentId = contentId))
        val trackC = libraryDao.insertTrack(TestFixtures.track(path = "/c.mp3", audioContentId = contentId))
        val playlistId = repository.insertPlaylist(TestFixtures.playlist())

        repository.addToPlaylist(playlistId, trackA)
        repository.addToPlaylist(playlistId, trackB)
        repository.addToPlaylist(playlistId, trackC)

        // Remove the middle track (position 1).
        repository.removeFromPlaylist(playlistId, trackB)

        val remaining = repository.getPlaylistTracks(playlistId)
        assertEquals(2, remaining.size)
        assertEquals(trackA, remaining[0].trackId)
        assertEquals(0, remaining[0].position)
        assertEquals(trackC, remaining[1].trackId)
        assertEquals(1, remaining[1].position)
    }
}

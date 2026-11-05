package com.amit152116.riffle.data.db.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.InteractionType
import com.amit152116.riffle.data.db.PlaybackActionReason
import com.amit152116.riffle.data.db.PlaybackActionType
import com.amit152116.riffle.data.db.PlaybackEndReason
import com.amit152116.riffle.data.db.PlaybackSource
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.library.LibraryDao
import com.amit152116.riffle.data.db.playback.entities.PlaybackAction
import com.amit152116.riffle.data.db.playback.entities.PlaybackEvent
import com.amit152116.riffle.data.db.playback.entities.PlaylistTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private suspend fun insertTrack(): Pair<Long, Long> {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        val trackId = libraryDao.insertTrack(TestFixtures.track(audioContentId = contentId))
        return contentId to trackId
    }

    @Test
    fun insertPlaybackEvent_roundTripsAndCountsByTrack() = runBlocking {
        val (contentId, trackId) = insertTrack()

        dao.insertPlaybackEvent(
            PlaybackEvent(
                trackId = trackId,
                audioContentId = contentId,
                startedAt = 1_000L,
                startPositionMs = 0L,
                durationMsSnapshot = 180_000L,
                source = PlaybackSource.MANUAL,
                tzOffsetMinutes = 330
            )
        )
        dao.insertPlaybackEvent(
            PlaybackEvent(
                trackId = trackId,
                audioContentId = contentId,
                startedAt = 2_000L,
                startPositionMs = 0L,
                durationMsSnapshot = 180_000L,
                source = PlaybackSource.SHUFFLE,
                tzOffsetMinutes = 330
            )
        )

        assertEquals(2, dao.getPlayCount(trackId))
    }

    @Test
    fun playbackEvent_survivesHardTrackDeletion_withTrackIdSetNull() = runBlocking {
        val (contentId, trackId) = insertTrack()
        val eventId = dao.insertPlaybackEvent(
            PlaybackEvent(
                trackId = trackId,
                audioContentId = contentId,
                startedAt = 1_000L,
                startPositionMs = 0L,
                durationMsSnapshot = 180_000L,
                source = PlaybackSource.MANUAL,
                tzOffsetMinutes = 330
            )
        )

        // No app code hard-deletes a Track today (only soft-delete via
        // `present`), but the FK contract must hold if one is ever added:
        // deleting the row directly should null the event's trackId, not
        // block the delete or drop the event.
        db.openHelper.writableDatabase.execSQL("DELETE FROM Track WHERE id = $trackId")

        val survived = dao.getPlaybackEventById(eventId)
        assertNotNull(survived)
        assertNull(survived!!.trackId)
    }

    @Test
    fun checkpointAndClose_doNotReopenAClosedEvent() = runBlocking {
        val (contentId, trackId) = insertTrack()
        val eventId = dao.insertPlaybackEvent(
            PlaybackEvent(
                trackId = trackId,
                audioContentId = contentId,
                startedAt = 1_000L,
                startPositionMs = 0L,
                durationMsSnapshot = 180_000L,
                source = PlaybackSource.MANUAL,
                tzOffsetMinutes = 330
            )
        )

        dao.checkpointPlaybackEvent(eventId, at = 1_500L, positionMs = 30_000L)
        dao.closePlaybackEvent(eventId, endedAt = 2_000L, endPositionMs = 60_000L, endReason = PlaybackEndReason.USER_SKIPPED)
        // A checkpoint after close must not resurrect the row.
        dao.checkpointPlaybackEvent(eventId, at = 2_500L, positionMs = 70_000L)

        assertEquals(0, dao.getUnclosedPlaybackEvents().size)
    }

    @Test
    fun unclosedPlaybackEvents_areFoundForReconciliation() = runBlocking {
        val (contentId, trackId) = insertTrack()
        dao.insertPlaybackEvent(
            PlaybackEvent(
                trackId = trackId,
                audioContentId = contentId,
                startedAt = 1_000L,
                startPositionMs = 0L,
                durationMsSnapshot = 180_000L,
                source = PlaybackSource.MANUAL,
                tzOffsetMinutes = 330
            )
        )

        assertEquals(1, dao.getUnclosedPlaybackEvents().size)
    }

    @Test
    fun playbackAction_countsByType() = runBlocking {
        val (contentId, trackId) = insertTrack()
        val eventId = dao.insertPlaybackEvent(
            PlaybackEvent(
                trackId = trackId,
                audioContentId = contentId,
                startedAt = 1_000L,
                startPositionMs = 0L,
                durationMsSnapshot = 180_000L,
                source = PlaybackSource.MANUAL,
                tzOffsetMinutes = 330
            )
        )

        dao.insertPlaybackAction(
            PlaybackAction(
                eventId = eventId,
                type = PlaybackActionType.SEEK,
                atMs = 1_100L,
                positionMs = 10_000L,
                toPositionMs = 40_000L,
                reason = PlaybackActionReason.USER
            )
        )
        dao.insertPlaybackAction(
            PlaybackAction(
                eventId = eventId,
                type = PlaybackActionType.PAUSE,
                atMs = 1_200L,
                positionMs = 40_000L,
                reason = PlaybackActionReason.AUDIO_FOCUS_TRANSIENT
            )
        )
        dao.insertPlaybackAction(
            PlaybackAction(
                eventId = eventId,
                type = PlaybackActionType.SEEK,
                atMs = 1_300L,
                positionMs = 40_000L,
                toPositionMs = 50_000L,
                reason = PlaybackActionReason.USER
            )
        )

        assertEquals(2, dao.countActionsByType(eventId, PlaybackActionType.SEEK))
        assertEquals(1, dao.countActionsByType(eventId, PlaybackActionType.PAUSE))
    }

    @Test
    fun setFavorite_writesCacheAndHistory_clearFavorite_appendsSecondHistoryRow() = runBlocking {
        val (_, trackId) = insertTrack()

        dao.setFavorite(trackId, at = 1_000L)
        assertNotNull(dao.getFavorite(trackId))

        dao.clearFavorite(trackId, at = 2_000L)
        assertNull(dao.getFavorite(trackId))

        // History survives the unfavorite - this is exactly the cycle a
        // single overwritten FavoriteTrack row would have lost.
        val history = dao.getInteractionHistory(trackId)
        assertEquals(2, history.size)
        assertEquals(InteractionType.UNFAVORITED, history[0].type)
        assertEquals(InteractionType.FAVORITED, history[1].type)
    }

    @Test
    fun playlistTrack_allowsDuplicateTrack() = runBlocking {
        val (contentId, trackId) = insertTrack()
        val playlistId = dao.insertPlaylist(TestFixtures.playlist())

        dao.insertPlaylistTrack(
            PlaylistTrack(
                playlistId = playlistId,
                trackId = trackId,
                position = 0
            )
        )
        dao.insertPlaylistTrack(
            PlaylistTrack(
                playlistId = playlistId,
                trackId = trackId,
                position = 1
            )
        )

        val entries = dao.getPlaylistTracks(playlistId)
        assertEquals(2, entries.size)
    }

    @Test
    fun setBlacklisted_thenClearBlacklisted_roundTripsAndAppendsHistory() = runBlocking {
        val (_, trackId) = insertTrack()

        dao.setBlacklisted(trackId, at = 1_000L)
        assertEquals(1, dao.getInteractionHistory(trackId).size)
        assertEquals(InteractionType.HIDDEN, dao.getInteractionHistory(trackId)[0].type)

        dao.clearBlacklisted(trackId, at = 2_000L)
        // Un-blacklisting is the whole point of the manage screen - it must
        // actually clear the cache row, not just append history.
        val history = dao.getInteractionHistory(trackId)
        assertEquals(2, history.size)
        assertEquals(InteractionType.UNHIDDEN, history[0].type)
    }

    @Test
    fun deletePlaylist_withTracks_removesEntriesFirst() = runBlocking {
        val (_, trackId) = insertTrack()
        val playlistId = dao.insertPlaylist(TestFixtures.playlist())
        dao.addToPlaylist(playlistId, trackId)

        // Playlist -> PlaylistTrack has no cascading FK (see PlaylistTrack's
        // kdoc) - if deletePlaylist() didn't clear entries first this throws
        // a foreign key constraint failure instead of returning cleanly.
        dao.deletePlaylist(playlistId)

        assertEquals(0, dao.getPlaylistTracks(playlistId).size)
    }

    @Test
    fun playlistTracksWithTrackFlow_keysDuplicateEntriesByPlaylistTrackId() = runBlocking {
        val (_, trackId) = insertTrack()
        val playlistId = dao.insertPlaylist(TestFixtures.playlist())

        val firstId = dao.addToPlaylist(playlistId, trackId)
        val secondId = dao.addToPlaylist(playlistId, trackId)

        val rows = dao.getPlaylistTracksWithTrackFlow(playlistId).first()
        assertEquals(2, rows.size)
        // Same underlying track twice - only playlistTrackId can distinguish
        // the rows (both share the same Track.id), which is exactly why the
        // UI must key its list by this field, not by track id.
        assertEquals(setOf(firstId, secondId), rows.map { it.playlistTrackId }.toSet())
    }
}

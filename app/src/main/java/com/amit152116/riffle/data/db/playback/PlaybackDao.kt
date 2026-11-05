package com.amit152116.riffle.data.db.playback

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.amit152116.riffle.data.db.InteractionType
import com.amit152116.riffle.data.db.PlaybackActionType
import com.amit152116.riffle.data.db.PlaybackEndReason
import com.amit152116.riffle.data.db.playback.entities.BlacklistedTrack
import com.amit152116.riffle.data.db.playback.entities.FavoriteTrack
import com.amit152116.riffle.data.db.playback.entities.PlaybackAction
import com.amit152116.riffle.data.db.playback.entities.PlaybackEvent
import com.amit152116.riffle.data.db.playback.entities.Playlist
import com.amit152116.riffle.data.db.playback.entities.PlaylistTrack
import com.amit152116.riffle.data.db.playback.entities.PlaylistTrackWithTrack
import com.amit152116.riffle.data.db.playback.entities.TrackInteraction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackDao {
    @Insert
    suspend fun insertPlaybackEvent(event: PlaybackEvent): Long

    // Periodic position checkpoint while playing. Guarded by "endedAt IS NULL"
    // rather than a full @Update so a checkpoint racing a close can't reopen
    // an already-closed event.
    @Query(
        "UPDATE PlaybackEvent SET lastCheckpointAt = :at, lastPositionMs = :positionMs " +
            "WHERE id = :id AND endedAt IS NULL"
    )
    suspend fun checkpointPlaybackEvent(id: Long, at: Long, positionMs: Long)

    @Query(
        "UPDATE PlaybackEvent SET endedAt = :endedAt, endPositionMs = :endPositionMs, endReason = :endReason " +
            "WHERE id = :id AND endedAt IS NULL"
    )
    suspend fun closePlaybackEvent(id: Long, endedAt: Long, endPositionMs: Long, endReason: PlaybackEndReason)

    // Rows a killed process left open (endedAt still null). Call once at
    // startup and close each with lastCheckpointAt/lastPositionMs as the end.
    @Query("SELECT * FROM PlaybackEvent WHERE endedAt IS NULL")
    suspend fun getUnclosedPlaybackEvents(): List<PlaybackEvent>

    @Query("SELECT COUNT(*) FROM PlaybackEvent WHERE trackId = :trackId")
    suspend fun getPlayCount(trackId: Long): Int

    @Query("SELECT * FROM PlaybackEvent WHERE id = :id")
    suspend fun getPlaybackEventById(id: Long): PlaybackEvent?

    @Insert
    suspend fun insertPlaybackAction(action: PlaybackAction): Long

    @Query("SELECT COUNT(*) FROM PlaybackAction WHERE eventId = :eventId AND type = :type")
    suspend fun countActionsByType(eventId: Long, type: PlaybackActionType): Int

    @Insert
    suspend fun insertTrackInteraction(interaction: TrackInteraction): Long

    @Query("SELECT * FROM TrackInteraction WHERE trackId = :trackId ORDER BY timestamp DESC")
    suspend fun getInteractionHistory(trackId: Long): List<TrackInteraction>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favoriteTrack: FavoriteTrack)

    @Query("DELETE FROM FavoriteTrack WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: Long)

    /** Sets the current-state cache and appends the historical signal in one transaction. */
    @Transaction
    suspend fun setFavorite(trackId: Long, at: Long) {
        insertFavorite(FavoriteTrack(trackId = trackId, addedAt = at))
        insertTrackInteraction(
            TrackInteraction(
                trackId = trackId,
                timestamp = at,
                type = InteractionType.FAVORITED
            )
        )
    }

    /** Clears the current-state cache and appends the historical signal in one transaction. */
    @Transaction
    suspend fun clearFavorite(trackId: Long, at: Long) {
        removeFavorite(trackId)
        insertTrackInteraction(
            TrackInteraction(
                trackId = trackId,
                timestamp = at,
                type = InteractionType.UNFAVORITED
            )
        )
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlacklisted(blacklistedTrack: BlacklistedTrack)

    @Query("DELETE FROM BlacklistedTrack WHERE trackId = :trackId")
    suspend fun removeBlacklisted(trackId: Long)

    /** Sets the blacklist cache and appends the historical signal in one transaction. */
    @Transaction
    suspend fun setBlacklisted(trackId: Long, at: Long) {
        insertBlacklisted(BlacklistedTrack(trackId = trackId, addedAt = at))
        insertTrackInteraction(TrackInteraction(trackId = trackId, timestamp = at, type = InteractionType.HIDDEN))
    }

    /** Clears the blacklist cache and appends the historical signal in one transaction. */
    @Transaction
    suspend fun clearBlacklisted(trackId: Long, at: Long) {
        removeBlacklisted(trackId)
        insertTrackInteraction(TrackInteraction(trackId = trackId, timestamp = at, type = InteractionType.UNHIDDEN))
    }

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM Playlist WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("DELETE FROM PlaylistTrack WHERE playlistId = :playlistId")
    suspend fun deleteAllPlaylistTracksForPlaylist(playlistId: Long)

    /** Playlist has no cascading FK (see PlaylistTrack's kdoc), so entries must go first. */
    @Transaction
    suspend fun deletePlaylist(playlistId: Long) {
        deleteAllPlaylistTracksForPlaylist(playlistId)
        deletePlaylistById(playlistId)
    }

    @Query("SELECT * FROM Playlist ORDER BY createdAt DESC")
    fun getPlaylistsFlow(): Flow<List<Playlist>>

    @Query(
        "SELECT pt.id AS playlistTrackId, t.* FROM PlaylistTrack pt " +
            "INNER JOIN Track t ON t.id = pt.trackId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC"
    )
    fun getPlaylistTracksWithTrackFlow(playlistId: Long): Flow<List<PlaylistTrackWithTrack>>

    @Insert
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrack): Long

    @Update
    suspend fun updatePlaylistTrack(playlistTrack: PlaylistTrack)

    @Delete
    suspend fun deletePlaylistTrack(playlistTrack: PlaylistTrack)

    @Query("DELETE FROM PlaylistTrack WHERE id = :playlistTrackId")
    suspend fun deletePlaylistTrackById(playlistTrackId: Long)

    @Query("SELECT * FROM FavoriteTrack WHERE trackId = :trackId")
    suspend fun getFavorite(trackId: Long): FavoriteTrack?

    @Query("SELECT * FROM PlaylistTrack WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistTracks(playlistId: Long): List<PlaylistTrack>

    @Query("SELECT * FROM PlaylistTrack WHERE id = :playlistTrackId")
    suspend fun getPlaylistTrackById(playlistTrackId: Long): PlaylistTrack?

    /** Appends the track at the end of the playlist's current order. */
    @Transaction
    suspend fun addToPlaylist(playlistId: Long, trackId: Long): Long {
        val nextPosition = getPlaylistTracks(playlistId).size
        return insertPlaylistTrack(
            PlaylistTrack(
                playlistId = playlistId,
                trackId = trackId,
                position = nextPosition
            )
        )
    }

    /**
     * Removes one specific playlist entry by its own row id (not by trackId,
     * since the same track can appear more than once in a playlist) and
     * renumbers every remaining entry's position contiguously.
     */
    @Transaction
    suspend fun removeFromPlaylist(playlistTrackId: Long) {
        val toRemove = getPlaylistTrackById(playlistTrackId) ?: return
        deletePlaylistTrackById(playlistTrackId)
        getPlaylistTracks(toRemove.playlistId).forEachIndexed { index, entry ->
            if (entry.position != index) {
                updatePlaylistTrack(entry.copy(position = index))
            }
        }
    }
}

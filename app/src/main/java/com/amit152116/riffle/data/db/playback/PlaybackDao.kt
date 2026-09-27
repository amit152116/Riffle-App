package com.amit152116.riffle.data.db.playback

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface PlaybackDao {
    @Insert
    suspend fun insertPlayHistory(playHistory: PlayHistory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favoriteTrack: FavoriteTrack)

    @Query("DELETE FROM FavoriteTrack WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlacklisted(blacklistedTrack: BlacklistedTrack)

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Insert
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrack): Long

    @Update
    suspend fun updatePlaylistTrack(playlistTrack: PlaylistTrack)

    @Delete
    suspend fun deletePlaylistTrack(playlistTrack: PlaylistTrack)

    @Query("DELETE FROM PlaylistTrack WHERE id = :playlistTrackId")
    suspend fun deletePlaylistTrackById(playlistTrackId: Long)

    @Query("SELECT COUNT(*) FROM PlayHistory WHERE trackId = :trackId")
    suspend fun getPlayCount(trackId: Long): Int

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
        return insertPlaylistTrack(PlaylistTrack(playlistId = playlistId, trackId = trackId, position = nextPosition))
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

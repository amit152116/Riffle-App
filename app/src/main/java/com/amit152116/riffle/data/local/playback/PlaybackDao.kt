package com.amit152116.riffle.data.local.playback

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT COUNT(*) FROM PlayHistory WHERE trackId = :trackId")
    suspend fun getPlayCount(trackId: Long): Int

    @Query("SELECT * FROM FavoriteTrack WHERE trackId = :trackId")
    suspend fun getFavorite(trackId: Long): FavoriteTrack?

    @Query("SELECT * FROM PlaylistTrack WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistTracks(playlistId: Long): List<PlaylistTrack>
}

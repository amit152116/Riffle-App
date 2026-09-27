package com.amit152116.riffle.data.local.playback

class PlaybackRepository(private val dao: PlaybackDao) {
    suspend fun insertPlayHistory(playHistory: PlayHistory) = dao.insertPlayHistory(playHistory)
    suspend fun addFavorite(trackId: Long, addedAt: Long) =
        dao.insertFavorite(FavoriteTrack(trackId = trackId, addedAt = addedAt))
    suspend fun removeFavorite(trackId: Long) = dao.removeFavorite(trackId)
    suspend fun addBlacklisted(trackId: Long, addedAt: Long) =
        dao.insertBlacklisted(BlacklistedTrack(trackId = trackId, addedAt = addedAt))
    suspend fun insertPlaylist(playlist: Playlist) = dao.insertPlaylist(playlist)
    suspend fun getPlaylistTracks(playlistId: Long) = dao.getPlaylistTracks(playlistId)

    /** Appends the track at the end of the playlist's current order. */
    suspend fun addToPlaylist(playlistId: Long, trackId: Long): Long {
        val nextPosition = dao.getPlaylistTracks(playlistId).size
        return dao.insertPlaylistTrack(PlaylistTrack(playlistId = playlistId, trackId = trackId, position = nextPosition))
    }

    /**
     * Removes the first matching entry and renumbers every remaining entry's
     * position contiguously, so no gap is left where the removed entry was.
     */
    suspend fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        val entries = dao.getPlaylistTracks(playlistId)
        val toRemove = entries.firstOrNull { it.trackId == trackId } ?: return
        dao.deletePlaylistTrack(toRemove)

        val remaining = entries.filterNot { it.id == toRemove.id }.sortedBy { it.position }
        remaining.forEachIndexed { index, entry ->
            if (entry.position != index) {
                dao.updatePlaylistTrack(entry.copy(position = index))
            }
        }
    }
}

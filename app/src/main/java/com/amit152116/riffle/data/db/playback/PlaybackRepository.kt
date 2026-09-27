package com.amit152116.riffle.data.db.playback

class PlaybackRepository(private val dao: PlaybackDao) {
    suspend fun insertPlayHistory(playHistory: PlayHistory) = dao.insertPlayHistory(playHistory)
    suspend fun addFavorite(trackId: Long, addedAt: Long) =
        dao.insertFavorite(FavoriteTrack(trackId = trackId, addedAt = addedAt))
    suspend fun removeFavorite(trackId: Long) = dao.removeFavorite(trackId)
    suspend fun addBlacklisted(trackId: Long, addedAt: Long) =
        dao.insertBlacklisted(BlacklistedTrack(trackId = trackId, addedAt = addedAt))
    suspend fun insertPlaylist(playlist: Playlist) = dao.insertPlaylist(playlist)
    suspend fun getPlaylistTracks(playlistId: Long) = dao.getPlaylistTracks(playlistId)

    /** Appends the track at the end of the playlist's current order. Atomic (see PlaybackDao). */
    suspend fun addToPlaylist(playlistId: Long, trackId: Long): Long = dao.addToPlaylist(playlistId, trackId)

    /**
     * Removes one specific playlist entry by its own row id - not by trackId,
     * since the same track can appear more than once in a playlist - and
     * renumbers every remaining entry's position contiguously. Atomic (see PlaybackDao).
     */
    suspend fun removeFromPlaylist(playlistTrackId: Long) = dao.removeFromPlaylist(playlistTrackId)
}

package com.amit152116.riffle.data.db.playback

import com.amit152116.riffle.data.db.PlaybackEndReason
import com.amit152116.riffle.data.db.playback.entities.PlaybackAction
import com.amit152116.riffle.data.db.playback.entities.PlaybackEvent
import com.amit152116.riffle.data.db.playback.entities.Playlist

class PlaybackRepository(private val dao: PlaybackDao) {
    suspend fun insertPlaybackEvent(event: PlaybackEvent) = dao.insertPlaybackEvent(event)
    suspend fun checkpointPlaybackEvent(id: Long, at: Long, positionMs: Long) =
        dao.checkpointPlaybackEvent(id, at, positionMs)
    suspend fun closePlaybackEvent(id: Long, endedAt: Long, endPositionMs: Long, endReason: PlaybackEndReason) =
        dao.closePlaybackEvent(id, endedAt, endPositionMs, endReason)
    suspend fun getUnclosedPlaybackEvents() = dao.getUnclosedPlaybackEvents()
    suspend fun insertPlaybackAction(action: PlaybackAction) = dao.insertPlaybackAction(action)

    /** Sets the current-state favorite cache and appends the historical signal. */
    suspend fun addFavorite(trackId: Long, addedAt: Long) = dao.setFavorite(trackId, addedAt)

    /** Clears the current-state favorite cache and appends the historical signal. */
    suspend fun removeFavorite(trackId: Long, at: Long) = dao.clearFavorite(trackId, at)
    suspend fun getFavorite(trackId: Long) = dao.getFavorite(trackId)
    suspend fun getInteractionHistory(trackId: Long) = dao.getInteractionHistory(trackId)
    suspend fun addBlacklisted(trackId: Long, addedAt: Long) = dao.setBlacklisted(trackId, addedAt)
    suspend fun removeBlacklisted(trackId: Long, at: Long) = dao.clearBlacklisted(trackId, at)
    suspend fun insertPlaylist(playlist: Playlist) = dao.insertPlaylist(playlist)
    fun getPlaylistsFlow() = dao.getPlaylistsFlow()
    suspend fun getPlaylistTracks(playlistId: Long) = dao.getPlaylistTracks(playlistId)
    fun getPlaylistTracksWithTrackFlow(playlistId: Long) = dao.getPlaylistTracksWithTrackFlow(playlistId)
    suspend fun deletePlaylist(playlistId: Long) = dao.deletePlaylist(playlistId)

    /** Appends the track at the end of the playlist's current order. Atomic (see PlaybackDao). */
    suspend fun addToPlaylist(playlistId: Long, trackId: Long): Long = dao.addToPlaylist(playlistId, trackId)

    /**
     * Removes one specific playlist entry by its own row id - not by trackId,
     * since the same track can appear more than once in a playlist - and
     * renumbers every remaining entry's position contiguously. Atomic (see PlaybackDao).
     */
    suspend fun removeFromPlaylist(playlistTrackId: Long) = dao.removeFromPlaylist(playlistTrackId)
}

package com.amit152116.riffle.ui.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.data.db.playback.entities.Playlist
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.data.model.toTrackUiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** playlistTrackId (PlaylistTrack.id, not the track's own id - see its kdoc) paired with the track. */
data class PlaylistTrackUiModel(val playlistTrackId: Long, val track: TrackUiModel)

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val repository = container.playbackRepository

    val playlists: StateFlow<List<Playlist>> = repository.getPlaylistsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun tracksFor(playlistId: Long): Flow<List<PlaylistTrackUiModel>> =
        repository.getPlaylistTracksWithTrackFlow(playlistId).map { rows ->
            rows.mapNotNull { row -> row.track.toTrackUiModel()?.let { PlaylistTrackUiModel(row.playlistTrackId, it) } }
        }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = repository.insertPlaylist(Playlist(name = trimmed, createdAt = System.currentTimeMillis()))
            onCreated(id)
        }
    }

    fun addTracksToPlaylist(playlistId: Long, tracks: List<TrackUiModel>) {
        viewModelScope.launch {
            for (track in tracks) {
                val roomId = container.resolveTrackId(track.id) ?: continue
                repository.addToPlaylist(playlistId, roomId)
            }
        }
    }

    fun removeFromPlaylist(playlistTrackId: Long) {
        viewModelScope.launch { repository.removeFromPlaylist(playlistTrackId) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }
}

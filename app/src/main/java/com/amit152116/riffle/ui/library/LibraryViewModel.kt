package com.amit152116.riffle.ui.library

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amit152116.riffle.data.LocalAudioRepository
import com.amit152116.riffle.data.model.AlbumUiModel
import com.amit152116.riffle.data.model.ArtistUiModel
import com.amit152116.riffle.data.model.GridStyle
import com.amit152116.riffle.data.model.TrackUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalAudioRepository(application)
    private val prefs = application.getSharedPreferences("riffle_library_prefs", Context.MODE_PRIVATE)

    private val _minimumDurationSeconds = MutableStateFlow(
        prefs.getInt(MINIMUM_DURATION_SECONDS_KEY, DEFAULT_MINIMUM_DURATION_SECONDS)
    )
    val minimumDurationSeconds: StateFlow<Int> = _minimumDurationSeconds.asStateFlow()

    private val _albumGridStyle = MutableStateFlow(
        readGridStyle(ALBUM_GRID_STYLE_KEY, GridStyle.GRID)
    )
    val albumGridStyle: StateFlow<GridStyle> = _albumGridStyle.asStateFlow()

    private val _artistGridStyle = MutableStateFlow(
        readGridStyle(ARTIST_GRID_STYLE_KEY, GridStyle.CIRCULAR)
    )
    val artistGridStyle: StateFlow<GridStyle> = _artistGridStyle.asStateFlow()

    private fun readGridStyle(key: String, default: GridStyle): GridStyle {
        val ordinal = prefs.getInt(key, default.ordinal)
        return GridStyle.entries.getOrElse(ordinal) { default }
    }

    private val _songs = MutableStateFlow<List<TrackUiModel>>(emptyList())
    val songs: StateFlow<List<TrackUiModel>> = _songs.asStateFlow()
    
    private val _albums = MutableStateFlow<List<AlbumUiModel>>(emptyList())
    val albums = _albums.asStateFlow()
    
    private val _artists = MutableStateFlow<List<ArtistUiModel>>(emptyList())
    val artists = _artists.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val loadedSongs = repository.getLocalSongs(_minimumDurationSeconds.value)
                _songs.value = loadedSongs
                
                val albumGroups = loadedSongs.groupBy { it.album }
                _albums.value = albumGroups.map { (albumName, albumTracks) ->
                    val firstTrack = albumTracks.first()
                    AlbumUiModel(
                        name = albumName,
                        artist = firstTrack.artist,
                        trackCount = albumTracks.size,
                        artworkUri = firstTrack.artworkUri
                    )
                }.sortedBy { it.name }
                
                val artistGroups = loadedSongs.groupBy { it.artist }
                _artists.value = artistGroups.map { (artistName, artistTracks) ->
                    ArtistUiModel(
                        name = artistName,
                        trackCount = artistTracks.size,
                        artworkUri = artistTracks.firstOrNull { it.artworkUri != null }?.artworkUri
                    )
                }.sortedBy { it.name }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setMinimumDurationSeconds(seconds: Int) {
        val value = seconds.coerceIn(0, MAXIMUM_DURATION_SECONDS)
        _minimumDurationSeconds.value = value
        prefs.edit().putInt(MINIMUM_DURATION_SECONDS_KEY, value).apply()
        loadSongs()
    }

    fun setAlbumGridStyle(style: GridStyle) {
        _albumGridStyle.value = style
        prefs.edit().putInt(ALBUM_GRID_STYLE_KEY, style.ordinal).apply()
    }

    fun setArtistGridStyle(style: GridStyle) {
        _artistGridStyle.value = style
        prefs.edit().putInt(ARTIST_GRID_STYLE_KEY, style.ordinal).apply()
    }

    companion object {
        private const val MINIMUM_DURATION_SECONDS_KEY = "minimum_duration_seconds"
        private const val DEFAULT_MINIMUM_DURATION_SECONDS = 30
        private const val MAXIMUM_DURATION_SECONDS = 60
        private const val ALBUM_GRID_STYLE_KEY = "album_grid_style"
        private const val ARTIST_GRID_STYLE_KEY = "artist_grid_style"
    }
}

package com.amit152116.riffle.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.data.model.AlbumUiModel
import com.amit152116.riffle.data.model.ArtistUiModel
import com.amit152116.riffle.data.model.GenreUiModel
import com.amit152116.riffle.data.model.GridStyle
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.data.model.toTrackUiModel
import com.amit152116.riffle.data.db.BrowseEntityType
import com.amit152116.riffle.data.db.library.ScanResult
import com.amit152116.riffle.data.db.playback.entities.BrowseEvent
import com.amit152116.riffle.work.LibraryIngestPipeline
import com.amit152116.riffle.log.RiffleLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val libraryRepository = container.libraryRepository
    private val prefs = container.preferences

    private val libraryScanner = container.libraryScanner
    private val log = RiffleLog(this)

    /** See AppContainer.resolveTrackId kdoc for why this resolution is needed at all. */
    fun blacklistTrack(track: TrackUiModel) {
        viewModelScope.launch {
            val roomId = container.resolveTrackId(track.id) ?: return@launch
            container.playbackRepository.addBlacklisted(roomId, System.currentTimeMillis())
        }
    }

    // Only ARTIST/ALBUM/HOME_SECTION are wired - GENRE/CLUSTER/TRACK have no
    // dedicated browse screen to call from yet (no Artist/Album table exists
    // either, see BrowseEvent's kdoc - entityKey is the route argument name).
    fun logBrowseEvent(type: BrowseEntityType, key: String) {
        viewModelScope.launch {
            container.telemetryRepository.insertBrowseEvent(
                BrowseEvent(timestamp = System.currentTimeMillis(), entityType = type, entityKey = key)
            )
        }
    }


    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastScanResult = MutableStateFlow<ScanResult?>(null)
    val lastScanResult: StateFlow<ScanResult?> = _lastScanResult.asStateFlow()

    /**
     * Fast metadata pass (isScanning tracks this one, tied to this
     * ViewModel/Compose lifecycle - it only takes seconds) plus the hash and
     * analysis passes, enqueued as chained WorkManager jobs (LibraryIngestPipeline)
     * rather than a viewModelScope. Launch since they can take minutes and must
     * survive the app backgrounding or this ViewModel clearing, not just avoid
     * blocking isScanning. Call on launch, on permission grant, and from the
     * manual scan button (see LibraryScanner's own doc: trigger-agnostic by design).
     */
    fun scanLibrary() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                _lastScanResult.value = libraryScanner.scan()
            } catch (e: Exception) {
                log.e("Library metadata scan failed", e)
            } finally {
                _isScanning.value = false
            }
        }
        LibraryIngestPipeline.enqueue(getApplication())
    }

    private val _minimumDurationSeconds = MutableStateFlow(prefs.minimumDurationSeconds)
    val minimumDurationSeconds: StateFlow<Int> = _minimumDurationSeconds.asStateFlow()

    private val _albumGridStyle = MutableStateFlow(prefs.albumGridStyle)
    val albumGridStyle: StateFlow<GridStyle> = _albumGridStyle.asStateFlow()

    private val _artistGridStyle = MutableStateFlow(prefs.artistGridStyle)
    val artistGridStyle: StateFlow<GridStyle> = _artistGridStyle.asStateFlow()

    private val _songs = MutableStateFlow<List<TrackUiModel>>(emptyList())
    val songs: StateFlow<List<TrackUiModel>> = _songs.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumUiModel>>(emptyList())
    val albums: StateFlow<List<AlbumUiModel>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<ArtistUiModel>>(emptyList())
    val artists: StateFlow<List<ArtistUiModel>> = _artists.asStateFlow()

    private val _genres = MutableStateFlow<List<GenreUiModel>>(emptyList())
    val genres: StateFlow<List<GenreUiModel>> = _genres.asStateFlow()

    private val _recentTracks = MutableStateFlow<List<TrackUiModel>>(emptyList())
    val recentTracks: StateFlow<List<TrackUiModel>> = _recentTracks.asStateFlow()

    private val _mostPlayedTracks = MutableStateFlow<List<TrackUiModel>>(emptyList())
    val mostPlayedTracks: StateFlow<List<TrackUiModel>> = _mostPlayedTracks.asStateFlow()

    // One representative track per artist/album, ordered by that artist's/
    // album's own total play count (summed across all their tracks) - not
    // derived from mostPlayedTracks, which only ranks individual tracks and
    // would undercount an artist whose plays are spread thin across many.
    private val _topArtistTracks = MutableStateFlow<List<TrackUiModel>>(emptyList())
    val topArtistTracks: StateFlow<List<TrackUiModel>> = _topArtistTracks.asStateFlow()

    private val _topAlbumTracks = MutableStateFlow<List<TrackUiModel>>(emptyList())
    val topAlbumTracks: StateFlow<List<TrackUiModel>> = _topAlbumTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Room's Flow re-emits the whole table on every write - the background
        // hash pass updates Track one row at a time, so distinctUntilChanged()
        // keeps a full hash pass from re-running groupBy on every single row.
        combine(libraryRepository.getPresentTracksFlow(), _minimumDurationSeconds) { tracks, minSeconds ->
            val minMs = minSeconds * 1_000L
            tracks.filter { (it.durationMs ?: 0L) >= minMs }.mapNotNull { it.toTrackUiModel() }
        }
            .distinctUntilChanged()
            .onEach { loadedSongs ->
                _songs.value = loadedSongs

                _albums.value = loadedSongs.groupBy { it.album }.map { (albumName, albumTracks) ->
                    val firstTrack = albumTracks.first()
                    AlbumUiModel(
                        name = albumName,
                        artist = firstTrack.artist,
                        trackCount = albumTracks.size,
                        artworkUri = firstTrack.artworkUri
                    )
                }.sortedBy { it.name }

                _artists.value = loadedSongs.groupBy { it.artist }.map { (artistName, artistTracks) ->
                    ArtistUiModel(
                        name = artistName,
                        trackCount = artistTracks.size,
                        artworkUri = artistTracks.firstOrNull { it.artworkUri != null }?.artworkUri
                    )
                }.sortedBy { it.name }

                _genres.value = loadedSongs.filter { it.genre.isNotBlank() }
                    .groupBy { it.genre }
                    .map { (genreName, genreTracks) -> GenreUiModel(name = genreName, trackCount = genreTracks.size) }
                    .sortedBy { it.name }

                _isLoading.value = false
            }
            .launchIn(viewModelScope)

        // Home's "Recent artists/albums" mean recently added to the device (MediaStore
        // DATE_ADDED), not recently played - matches RetroMusicPlayer's own Recent
        // Artists/Albums (LastAddedSongsRepository, DATE_ADDED + cutoff). Recently
        // played lives separately under History (getHistoryTracksFlow), unaffected by
        // this cutoff. Without it, a fresh install's one-time bulk scan would make
        // every track look "recently added" forever.
        libraryRepository.getLastAddedTracksFlow()
            .onEach { tracks ->
                val cutoffEpochSeconds = System.currentTimeMillis() / 1000 - RECENT_CUTOFF_SECONDS
                _recentTracks.value = tracks
                    .filter { (it.dateAdded ?: 0L) > cutoffEpochSeconds }
                    .mapNotNull { it.toTrackUiModel() }
            }
            .launchIn(viewModelScope)

        libraryRepository.getMostPlayedTracksFlow()
            .onEach { tracks ->
                _mostPlayedTracks.value = tracks.mapNotNull { it.toTrackUiModel() }
            }
            .launchIn(viewModelScope)

        combine(_songs, libraryRepository.getTopArtistsByPlayCountFlow()) { songs, ranked ->
            val byArtist = songs.groupBy { it.artist }
            ranked.mapNotNull { row ->
                byArtist[row.artist]?.let { tracks -> tracks.firstOrNull { it.artworkUri != null } ?: tracks.first() }
            }
        }
            .distinctUntilChanged()
            .onEach { _topArtistTracks.value = it }
            .launchIn(viewModelScope)

        combine(_songs, libraryRepository.getTopAlbumsByPlayCountFlow()) { songs, ranked ->
            val byAlbum = songs.groupBy { it.album }
            ranked.mapNotNull { row ->
                byAlbum[row.album]?.let { tracks -> tracks.firstOrNull { it.artworkUri != null } ?: tracks.first() }
            }
        }
            .distinctUntilChanged()
            .onEach { _topAlbumTracks.value = it }
            .launchIn(viewModelScope)
    }

    fun setMinimumDurationSeconds(seconds: Int) {
        val value = seconds.coerceIn(0, MAXIMUM_DURATION_SECONDS)
        _minimumDurationSeconds.value = value
        prefs.minimumDurationSeconds = value
        // No explicit reload: the combine() in init re-filters automatically.
    }

    fun setAlbumGridStyle(style: GridStyle) {
        _albumGridStyle.value = style
        prefs.albumGridStyle = style
    }

    fun setArtistGridStyle(style: GridStyle) {
        _artistGridStyle.value = style
        prefs.artistGridStyle = style
    }

    companion object {
        private const val MAXIMUM_DURATION_SECONDS = 60

        // Matches RetroMusicPlayer's default "this_month" last-added cutoff.
        private const val RECENT_CUTOFF_SECONDS = 30L * 24 * 3600
    }
}

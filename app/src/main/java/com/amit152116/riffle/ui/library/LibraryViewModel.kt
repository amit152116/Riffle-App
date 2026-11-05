package com.amit152116.riffle.ui.library

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.data.db.BrowseEntityType
import com.amit152116.riffle.data.db.library.LibraryRepository
import com.amit152116.riffle.data.db.library.LibraryScanner
import com.amit152116.riffle.data.db.library.ScanResult
import com.amit152116.riffle.data.db.playback.PlaybackRepository
import com.amit152116.riffle.data.db.playback.TelemetryRepository
import com.amit152116.riffle.data.db.playback.entities.BrowseEvent
import com.amit152116.riffle.data.model.AlbumSongSortOrder
import com.amit152116.riffle.data.model.AlbumSortOrder
import com.amit152116.riffle.data.model.AlbumUiModel
import com.amit152116.riffle.data.model.ArtistSortOrder
import com.amit152116.riffle.data.model.ArtistUiModel
import com.amit152116.riffle.data.model.FolderSortOrder
import com.amit152116.riffle.data.model.GenreUiModel
import com.amit152116.riffle.data.model.GridStyle
import com.amit152116.riffle.data.model.SongSortOrder
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.data.model.toTrackUiModel
import com.amit152116.riffle.data.prefs.PreferencesManager
import com.amit152116.riffle.log.RiffleLog
import com.amit152116.riffle.work.LibraryIngestPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(
    private val context: Context,// For ContentResolver renameFolder
    private val appContainer: AppContainer
) : ViewModel() {
    private val log = RiffleLog(this)
    private val libraryRepository: LibraryRepository = appContainer.libraryRepository
    private val playbackRepository: PlaybackRepository = appContainer.playbackRepository
    private val telemetryRepository: TelemetryRepository = appContainer.telemetryRepository
    private val prefs: PreferencesManager = appContainer.preferences
    private val libraryScanner: LibraryScanner = appContainer.libraryScanner


    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastScanResult = MutableStateFlow<ScanResult?>(null)
    val lastScanResult: StateFlow<ScanResult?> = _lastScanResult.asStateFlow()

    private val _minimumDurationSeconds = MutableStateFlow(prefs.minimumDurationSeconds)
    val minimumDurationSeconds: StateFlow<Int> = _minimumDurationSeconds.asStateFlow()

    private val _albumGridStyle = MutableStateFlow(prefs.albumGridStyle)
    val albumGridStyle: StateFlow<GridStyle> = _albumGridStyle.asStateFlow()

    private val _artistGridStyle = MutableStateFlow(prefs.artistGridStyle)
    val artistGridStyle: StateFlow<GridStyle> = _artistGridStyle.asStateFlow()

    private val _folderGridStyle = MutableStateFlow(prefs.folderGridStyle)
    val folderGridStyle: StateFlow<GridStyle> = _folderGridStyle.asStateFlow()

    private val _albumGridSize = MutableStateFlow(prefs.albumGridSize)
    val albumGridSize: StateFlow<Int> = _albumGridSize.asStateFlow()

    private val _artistGridSize = MutableStateFlow(prefs.artistGridSize)
    val artistGridSize: StateFlow<Int> = _artistGridSize.asStateFlow()

    private val _folderGridSize = MutableStateFlow(prefs.folderGridSize)
    val folderGridSize: StateFlow<Int> = _folderGridSize.asStateFlow()

    private val _songSortOrder = MutableStateFlow(prefs.songSortOrder)
    val songSortOrder: StateFlow<SongSortOrder> = _songSortOrder.asStateFlow()

    private val _albumSortOrder = MutableStateFlow(prefs.albumSortOrder)
    val albumSortOrder: StateFlow<AlbumSortOrder> = _albumSortOrder.asStateFlow()

    private val _albumSongSortOrder = MutableStateFlow(prefs.albumSongSortOrder)
    val albumSongSortOrder: StateFlow<AlbumSongSortOrder> = _albumSongSortOrder.asStateFlow()

    private val _artistSortOrder = MutableStateFlow(prefs.artistSortOrder)
    val artistSortOrder: StateFlow<ArtistSortOrder> = _artistSortOrder.asStateFlow()

    private val _folderSortOrder = MutableStateFlow(prefs.folderSortOrder)
    val folderSortOrder: StateFlow<FolderSortOrder> = _folderSortOrder.asStateFlow()

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
        combine(
            libraryRepository.getPresentTracksFlow(),
            _minimumDurationSeconds,
            _songSortOrder
        ) { tracks, minSeconds, sortOrder ->
            val minMs = minSeconds * 1_000L
            tracks.filter { (it.durationMs ?: 0L) >= minMs }.mapNotNull { it.toTrackUiModel() }
                .let { list ->
                    when (sortOrder) {
                        SongSortOrder.TITLE_A_Z -> list.sortedBy { it.title.lowercase() }
                        SongSortOrder.TITLE_Z_A -> list.sortedByDescending { it.title.lowercase() }
                        SongSortOrder.ARTIST -> list.sortedBy { it.artist.lowercase() }
                        SongSortOrder.ALBUM -> list.sortedBy { it.album.lowercase() }
                        SongSortOrder.DURATION_DESC -> list.sortedByDescending { it.durationMs }
                        SongSortOrder.DURATION_ASC -> list.sortedBy { it.durationMs }
                        SongSortOrder.DATE_ADDED_DESC -> list.sortedByDescending { it.dateAdded }
                        SongSortOrder.DATE_ADDED_ASC -> list.sortedBy { it.dateAdded }
                    }
                }
        }
            .distinctUntilChanged()
            .onEach { loadedSongs ->
                _songs.value = loadedSongs
                _genres.value = loadedSongs.filter { it.genre.isNotBlank() }
                    .groupBy { it.genre }
                    .map { (genreName, genreTracks) ->
                        GenreUiModel(
                            name = genreName,
                            trackCount = genreTracks.size
                        )
                    }
                    .sortedBy { it.name }
                _isLoading.value = false
            }
            .launchIn(viewModelScope)

        _songs.combine(_albumSortOrder) { loadedSongs, sortOrder ->
            loadedSongs.groupBy { it.album }.map { (albumName, albumTracks) ->
                val firstTrack = albumTracks.first()
                AlbumUiModel(
                    name = albumName,
                    artist = firstTrack.artist,
                    trackCount = albumTracks.size,
                    artworkUri = firstTrack.artworkUri
                )
            }.let { list ->
                when (sortOrder) {
                    AlbumSortOrder.ALBUM_A_Z -> list.sortedBy { it.name.lowercase() }
                    AlbumSortOrder.ALBUM_Z_A -> list.sortedByDescending { it.name.lowercase() }
                    AlbumSortOrder.ARTIST -> list.sortedBy { it.artist.lowercase() }
                    AlbumSortOrder.NUM_SONGS_DESC -> list.sortedByDescending { it.trackCount }
                }
            }
        }.onEach { _albums.value = it }.launchIn(viewModelScope)

        _songs.combine(_artistSortOrder) { loadedSongs, sortOrder ->
            loadedSongs.groupBy { it.artist }.map { (artistName, artistTracks) ->
                ArtistUiModel(
                    name = artistName,
                    trackCount = artistTracks.size,
                    artworkUri = artistTracks.firstOrNull { it.artworkUri != null }?.artworkUri,
                    albumCount = artistTracks.map { it.album }.distinct().size
                )
            }.let { list ->
                when (sortOrder) {
                    ArtistSortOrder.ARTIST_A_Z -> list.sortedBy { it.name.lowercase() }
                    ArtistSortOrder.ARTIST_Z_A -> list.sortedByDescending { it.name.lowercase() }
                    ArtistSortOrder.NUM_SONGS_DESC -> list.sortedByDescending { it.trackCount }
                }
            }
        }.onEach { _artists.value = it }.launchIn(viewModelScope)

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

        combine(_songs, libraryRepository.getTopArtistsByPlayCountFlow()) { songsList, ranked ->
            val byArtist = songsList.groupBy { it.artist }
            ranked.mapNotNull { row ->
                byArtist[row.artist]?.let { tracks ->
                    tracks.firstOrNull { it.artworkUri != null } ?: tracks.first()
                }
            }
        }
            .distinctUntilChanged()
            .onEach { _topArtistTracks.value = it }
            .launchIn(viewModelScope)

        combine(_songs, libraryRepository.getTopAlbumsByPlayCountFlow()) { songsList, ranked ->
            val byAlbum = songsList.groupBy { it.album }
            ranked.mapNotNull { row ->
                byAlbum[row.album]?.let { tracks ->
                    tracks.firstOrNull { it.artworkUri != null } ?: tracks.first()
                }
            }
        }
            .distinctUntilChanged()
            .onEach { _topAlbumTracks.value = it }
            .launchIn(viewModelScope)
    }

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
        LibraryIngestPipeline.enqueue(context)
    }

    fun blacklistTrack(track: TrackUiModel) {
        viewModelScope.launch {
            val roomId = libraryRepository.getTrackByMediaStoreId(track.id)?.id ?: return@launch
            playbackRepository.addBlacklisted(roomId, System.currentTimeMillis())
        }
    }

    // Only ARTIST/ALBUM/HOME_SECTION are wired - GENRE/CLUSTER/TRACK have no
    // dedicated browse screen to call from yet (no Artist/Album table exists
    // either, see BrowseEvent's kdoc - entityKey is the route argument name).
    fun logBrowseEvent(type: BrowseEntityType, key: String) {
        viewModelScope.launch {
            telemetryRepository.insertBrowseEvent(
                BrowseEvent(
                    timestamp = System.currentTimeMillis(),
                    entityType = type,
                    entityKey = key
                )
            )
        }
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

    fun setFolderGridStyle(style: GridStyle) {
        _folderGridStyle.value = style
        prefs.folderGridStyle = style
    }

    fun setAlbumGridSize(size: Int) {
        _albumGridSize.value = size
        prefs.albumGridSize = size
    }

    fun setArtistGridSize(size: Int) {
        _artistGridSize.value = size
        prefs.artistGridSize = size
    }

    fun setFolderGridSize(size: Int) {
        _folderGridSize.value = size
        prefs.folderGridSize = size
    }

    fun setSongSortOrder(sortOrder: SongSortOrder) {
        _songSortOrder.value = sortOrder
        prefs.songSortOrder = sortOrder
    }

    fun setAlbumSortOrder(sortOrder: AlbumSortOrder) {
        _albumSortOrder.value = sortOrder
        prefs.albumSortOrder = sortOrder
    }

    fun setAlbumSongSortOrder(sortOrder: AlbumSongSortOrder) {
        _albumSongSortOrder.value = sortOrder
        prefs.albumSongSortOrder = sortOrder
    }

    fun setArtistSortOrder(sortOrder: ArtistSortOrder) {
        _artistSortOrder.value = sortOrder
        prefs.artistSortOrder = sortOrder
    }

    fun setFolderSortOrder(sortOrder: FolderSortOrder) {
        _folderSortOrder.value = sortOrder
        prefs.folderSortOrder = sortOrder
    }

    fun renameFolder(oldRelativePath: String, newName: String) {
        viewModelScope.launch {
            try {
                // Ensure parent paths end with a slash, e.g. "Music/OldFolder/"
                val parentPath = oldRelativePath.trimEnd('/')
                    .substringBeforeLast('/', missingDelimiterValue = "").let {
                        if (it.isNotEmpty()) "$it/" else ""
                    }
                val newRelativePath = "$parentPath$newName/"

                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, newRelativePath)
                }

                // We use LIKE to catch tracks within the folder exactly.
                val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("$oldRelativePath%")

                val updatedCount = context.contentResolver.update(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    values,
                    selection,
                    selectionArgs
                )

                // If MediaStore update failed or did nothing (e.g. because of Scoped Storage limits on older APIs)
                // we try to rename the actual directory using standard java.io.File API.
                if (updatedCount == 0) {
                    val externalDir = Environment.getExternalStorageDirectory()
                    val oldDir = File(externalDir, oldRelativePath)
                    val newDir = File(externalDir, newRelativePath)
                    if (oldDir.exists() && oldDir.isDirectory) {
                        oldDir.renameTo(newDir)
                    }
                }

                // A full scan is needed to update the paths in our own Room DB
                scanLibrary()
            } catch (e: Exception) {
                log.e("Failed to rename folder from $oldRelativePath to $newName", e)
            }
        }
    }

    companion object {
        private const val MAXIMUM_DURATION_SECONDS = 60

        // Matches RetroMusicPlayer's default "this_month" last-added cutoff.
        private const val RECENT_CUTOFF_SECONDS = 30L * 24 * 3600
    }
}

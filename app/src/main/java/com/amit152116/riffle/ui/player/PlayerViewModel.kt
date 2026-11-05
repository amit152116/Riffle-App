package com.amit152116.riffle.ui.player

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.data.color.ArtworkColorExtractor
import com.amit152116.riffle.data.db.PLAYBACK_SOURCE_EXTRA_KEY
import com.amit152116.riffle.data.db.PlaybackSource
import com.amit152116.riffle.data.db.QueueAction
import com.amit152116.riffle.data.db.playback.entities.QueueEvent
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.data.model.toTrackUiModel
import com.amit152116.riffle.log.RiffleLog
import com.amit152116.riffle.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun TrackUiModel.toMediaItem(source: PlaybackSource = PlaybackSource.MANUAL): MediaItem =
    MediaItem.Builder()
        .setUri(mediaUri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .setExtras(Bundle().apply { putString(PLAYBACK_SOURCE_EXTRA_KEY, source.name) })
                .build()
        )
        .build()

private const val SAVE_STATE_INTERVAL_MS = 3000L

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: Player? = null

    private var pendingPlayRequest: Triple<TrackUiModel, List<TrackUiModel>, PlaybackSource>? = null
    private var sleepTimerJob: Job? = null

    private val _currentPlaylist = MutableStateFlow<List<TrackUiModel>>(emptyList())
    val currentPlaylist = _currentPlaylist.asStateFlow()
    
    private val container = AppContainer.getInstance(application)
    private val log = RiffleLog(this)
    private val prefs = container.preferences
    
    private val _currentTrack = MutableStateFlow<TrackUiModel?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite = _isFavorite.asStateFlow()
    private var favoriteRefreshJob: Job? = null

    private val _currentColors = MutableStateFlow(ArtworkColorExtractor.extract(null))
    val currentColors = _currentColors.asStateFlow()
    private var colorExtractionJob: Job? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_ALL)
    val repeatMode = _repeatMode.asStateFlow()
    
    private val _isCrossfadeEnabled = MutableStateFlow(prefs.crossfadeEnabled)
    val isCrossfadeEnabled = _isCrossfadeEnabled.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(prefs.crossfadeSeconds)
    val crossfadeSeconds = _crossfadeSeconds.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs = _sleepTimerRemainingMs.asStateFlow()

    private val _sleepTimerEndOfTrack = MutableStateFlow(false)
    val sleepTimerEndOfTrack = _sleepTimerEndOfTrack.asStateFlow()

    init {
        val sessionToken = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                player = controllerFuture?.get()
                setupPlayer()
            },
            ContextCompat.getMainExecutor(application)
        )
        
        viewModelScope.launch {
            var lastSaveElapsedMs = 0L
            while (isActive) {
                val p = player
                if (p != null) {
                    // Keep the UI in sync while paused too. This is important after
                    // restoring a queue: Media3 prepares the item without starting it.
                    if (p.duration > 0L) {
                        _currentPosition.value = p.currentPosition.coerceIn(0L, p.duration)
                        _duration.value = p.duration

                        if (_sleepTimerEndOfTrack.value &&
                            _isPlaying.value &&
                            p.currentPosition >= p.duration - 250L
                        ) {
                            p.pause()
                            cancelSleepTimer()
                        }
                    }

                    val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
                    if (_currentTrack.value != null &&
                        nowElapsedMs - lastSaveElapsedMs >= SAVE_STATE_INTERVAL_MS
                    ) {
                        saveState()
                        lastSaveElapsedMs = nowElapsedMs
                    }

                    // Crossfade & Smooth Fade volume ramping
                    val enabled = _isPlaying.value && _isCrossfadeEnabled.value
                    val durSec = _crossfadeSeconds.value
                    val fadeMs = (durSec * 1000f).toLong()
                    val totalDur = p.duration

                    if (enabled && fadeMs > 0 && totalDur > fadeMs * 2) {
                        val pos = p.currentPosition
                        val rem = totalDur - pos
                        val targetVol = when {
                            pos < fadeMs -> (pos.toFloat() / fadeMs).coerceIn(0.05f, 1f)
                            rem < fadeMs -> (rem.toFloat() / fadeMs).coerceIn(0.05f, 1f)
                            else -> 1f
                        }
                        p.volume = targetVol
                    } else {
                        p.volume = 1f
                    }
                }
                delay(100)
            }
        }
    }

    private fun setupPlayer() {
        val p = player ?: return
        p.repeatMode = _repeatMode.value
        p.shuffleModeEnabled = _isShuffleEnabled.value

        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffleEnabled.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    val trackId = mediaItem.mediaId.toLongOrNull()
                    val track = _currentPlaylist.value.find { it.id == trackId }
                    if (track != null) {
                        setCurrentTrack(track)
                    }
                }
            }
        })

        pendingPlayRequest?.let { (track, playlist, source) ->
            playTrack(track, playlist, source)
            pendingPlayRequest = null
        } ?: run {
            restoreSavedState()
        }
    }

    fun playTrack(track: TrackUiModel, playlist: List<TrackUiModel> = listOf(track), source: PlaybackSource = PlaybackSource.MANUAL) {
        val p = player
        if (p == null) {
            pendingPlayRequest = Triple(track, playlist, source)
            return
        }

        if (playlist != _currentPlaylist.value) {
            _currentPlaylist.value = playlist
            p.setMediaItems(playlist.map { it.toMediaItem(source) })
        }

        val index = playlist.indexOf(track)
        if (index >= 0) {
            p.seekToDefaultPosition(index)
        } else {
            p.seekToDefaultPosition(0)
        }

        p.prepare()
        p.play()
        setCurrentTrack(track)
        saveState()
    }

    private fun setCurrentTrack(track: TrackUiModel) {
        _currentTrack.value = track

        favoriteRefreshJob?.cancel()
        favoriteRefreshJob = viewModelScope.launch {
            val roomId = container.resolveTrackId(track.id)
            _isFavorite.value = roomId != null && container.playbackRepository.getFavorite(roomId) != null
        }

        colorExtractionJob?.cancel()
        colorExtractionJob = viewModelScope.launch {
            val colors = withContext(Dispatchers.IO) {
                val bitmap = track.artworkUri?.let { artworkUri ->
                    runCatching {
                        getApplication<Application>().imageLoader
                            .execute(
                                ImageRequest.Builder(getApplication<Application>())
                                    .data(artworkUri)
                                    .allowHardware(false)
                                    .build()
                            )
                            .drawable
                            ?.toBitmap()
                    }.getOrNull()
                }

                withContext(Dispatchers.Default) {
                    ArtworkColorExtractor.extract(bitmap)
                }
            }
            _currentColors.value = colors
        }
    }

    private fun saveState() {
        val track = _currentTrack.value
        val queue = _currentPlaylist.value
        if (track != null && queue.isNotEmpty()) {
            prefs.setSavedQueueState(
                queueIds = queue.map { it.id },
                trackId = track.id,
                positionMs = player?.currentPosition ?: 0L
            )
        }
    }

    private fun restoreSavedState() {
        if (_currentPlaylist.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val saved = prefs.getSavedQueueState() ?: return@launch

                val allSongs = container.libraryRepository.getPresentTracks(minDurationMs = 30_000L).mapNotNull { it.toTrackUiModel() }
                val songMap = allSongs.associateBy { it.id }

                val restoredQueue = saved.queueIds.mapNotNull { songMap[it] }
                if (restoredQueue.isEmpty()) return@launch

                val restoredTrack = songMap[saved.trackId] ?: restoredQueue.first()
                val restoredTrackIndex = restoredQueue.indexOf(restoredTrack).coerceAtLeast(0)

                _currentPlaylist.value = restoredQueue
                setCurrentTrack(restoredTrack)

                val p = player ?: return@launch
                p.setMediaItems(restoredQueue.map { it.toMediaItem() }, restoredTrackIndex, saved.positionMs)
                p.prepare()
                _currentPosition.value = saved.positionMs
            } catch (e: Exception) {
                log.e("Failed to restore saved playback state", e)
            }
        }
    }
    
    fun toggleFavorite() {
        val track = _currentTrack.value ?: return
        viewModelScope.launch {
            val roomId = container.resolveTrackId(track.id) ?: return@launch
            val now = System.currentTimeMillis()
            if (_isFavorite.value) {
                container.playbackRepository.removeFavorite(roomId, now)
                _isFavorite.value = false
            } else {
                container.playbackRepository.addFavorite(roomId, now)
                _isFavorite.value = true
            }
        }
    }

    fun blacklistTrack(track: TrackUiModel) {
        viewModelScope.launch {
            val roomId = container.resolveTrackId(track.id) ?: return@launch
            container.playbackRepository.addBlacklisted(roomId, System.currentTimeMillis())
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _isCrossfadeEnabled.value = enabled
        prefs.crossfadeEnabled = enabled
        if (!enabled) player?.volume = 1f
    }

    fun setCrossfadeSeconds(seconds: Float) {
        _crossfadeSeconds.value = seconds
        prefs.crossfadeSeconds = seconds
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerEndOfTrack.value = false

        if (minutes <= 0) {
            prefs.clearSleepTimerEndAt(endOfTrack = false)
            _sleepTimerRemainingMs.value = null
            return
        }

        prefs.setSleepTimerEndAt(System.currentTimeMillis() + minutes * 60_000L, endOfTrack = false)

        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes * 60_000L
            while (remaining > 0L && isActive) {
                _sleepTimerRemainingMs.value = remaining
                delay(1_000L)
                remaining -= 1_000L
            }
            if (isActive) {
                player?.pause()
                _sleepTimerRemainingMs.value = null
            }
        }
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        prefs.clearSleepTimerEndAt(endOfTrack = true)
        _sleepTimerRemainingMs.value = null
        _sleepTimerEndOfTrack.value = true
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        prefs.clearSleepTimerEndAt(endOfTrack = false)
        _sleepTimerRemainingMs.value = null
        _sleepTimerEndOfTrack.value = false
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
        saveState()
    }

    fun toggleShuffle() {
        val p = player ?: return
        val newState = !p.shuffleModeEnabled
        p.shuffleModeEnabled = newState
        _isShuffleEnabled.value = newState
    }

    fun cycleRepeatMode() {
        val p = player ?: return
        val nextMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
        p.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun skipNext() {
        val p = player ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val p = player ?: return
        if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
        }
    }

    private fun logQueueEvent(track: TrackUiModel, action: QueueAction, fromPosition: Int? = null, toPosition: Int? = null) {
        viewModelScope.launch {
            val roomId = container.resolveTrackId(track.id) ?: return@launch
            container.telemetryRepository.insertQueueEvent(
                QueueEvent(
                    trackId = roomId, queuedAt = System.currentTimeMillis(), action = action,
                    fromPosition = fromPosition, toPosition = toPosition, origin = PlaybackSource.MANUAL
                )
            )
        }
    }

    fun playNext(track: TrackUiModel) {
        val p = player ?: return
        val currentMediaItemIndex = p.currentMediaItemIndex
        val nextIndex = if (currentMediaItemIndex >= 0) currentMediaItemIndex + 1 else 0

        p.addMediaItem(nextIndex, track.toMediaItem())

        val updatedList = _currentPlaylist.value.toMutableList()
        if (nextIndex <= updatedList.size) {
            updatedList.add(nextIndex, track)
        } else {
            updatedList.add(track)
        }
        _currentPlaylist.value = updatedList
        logQueueEvent(track, QueueAction.ADD, toPosition = nextIndex)
    }

    fun playNext(tracks: List<TrackUiModel>) {
        tracks.asReversed().forEach(::playNext)
    }

    fun addToQueue(track: TrackUiModel) {
        val p = player ?: return
        p.addMediaItem(track.toMediaItem())
        _currentPlaylist.value = _currentPlaylist.value + track
        logQueueEvent(track, QueueAction.ADD, toPosition = _currentPlaylist.value.lastIndex)
    }

    fun addToQueue(tracks: List<TrackUiModel>) {
        tracks.forEach(::addToQueue)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val p = player ?: return
        if (fromIndex in 0 until _currentPlaylist.value.size && toIndex in 0 until _currentPlaylist.value.size) {
            val movedTrack = _currentPlaylist.value[fromIndex]
            p.moveMediaItem(fromIndex, toIndex)
            val updatedList = _currentPlaylist.value.toMutableList()
            val item = updatedList.removeAt(fromIndex)
            updatedList.add(toIndex, item)
            _currentPlaylist.value = updatedList
            logQueueEvent(movedTrack, QueueAction.MOVE, fromPosition = fromIndex, toPosition = toIndex)
        }
    }

    fun removeFromQueue(index: Int) {
        val p = player ?: return
        if (index in 0 until _currentPlaylist.value.size) {
            val removedTrack = _currentPlaylist.value[index]
            p.removeMediaItem(index)
            val updatedList = _currentPlaylist.value.toMutableList()
            updatedList.removeAt(index)
            _currentPlaylist.value = updatedList
            logQueueEvent(removedTrack, QueueAction.REMOVE, fromPosition = index)
        }
    }
    
    fun seekTo(position: Float) {
        val p = player ?: return
        val positionMs = (position * p.duration).toLong()
        p.seekTo(positionMs)
        _currentPosition.value = positionMs
        saveState()
    }

    fun seekBy(offsetMs: Long) {
        val p = player ?: return
        val newPositionMs = (p.currentPosition + offsetMs).coerceIn(0L, p.duration)
        p.seekTo(newPositionMs)
        _currentPosition.value = newPositionMs
        saveState()
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        saveState()
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}

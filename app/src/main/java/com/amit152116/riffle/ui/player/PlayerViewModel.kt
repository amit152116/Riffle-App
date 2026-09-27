package com.amit152116.riffle.ui.player

import android.content.Context
import android.content.SharedPreferences
import android.app.Application
import android.content.ComponentName
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.amit152116.riffle.data.LocalAudioRepository
import com.amit152116.riffle.data.color.ArtworkColorExtractor
import com.amit152116.riffle.data.model.TrackUiModel
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

private fun TrackUiModel.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(mediaUri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .build()
        )
        .build()

private const val SAVE_STATE_INTERVAL_MS = 3000L

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: Player? = null

    private var pendingPlayRequest: Pair<TrackUiModel, List<TrackUiModel>>? = null
    private var sleepTimerJob: Job? = null

    private val _currentPlaylist = MutableStateFlow<List<TrackUiModel>>(emptyList())
    val currentPlaylist = _currentPlaylist.asStateFlow()
    
    private val prefs: SharedPreferences = application.getSharedPreferences("riffle_player_prefs", Context.MODE_PRIVATE)
    
    private val _currentTrack = MutableStateFlow<TrackUiModel?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _currentColors = MutableStateFlow(ArtworkColorExtractor.extract(null))
    val currentColors = _currentColors.asStateFlow()
    private var colorExtractionJob: Job? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_ALL)
    val repeatMode = _repeatMode.asStateFlow()
    
    private val _isCrossfadeEnabled = MutableStateFlow(prefs.getBoolean("crossfade_enabled", false))
    val isCrossfadeEnabled = _isCrossfadeEnabled.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(prefs.getFloat("crossfade_seconds", 3f))
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

        pendingPlayRequest?.let { (track, playlist) ->
            playTrack(track, playlist)
            pendingPlayRequest = null
        } ?: run {
            restoreSavedState()
        }
    }
    
    fun playTrack(track: TrackUiModel, playlist: List<TrackUiModel> = listOf(track)) {
        val p = player
        if (p == null) {
            pendingPlayRequest = Pair(track, playlist)
            return
        }

        if (playlist != _currentPlaylist.value) {
            _currentPlaylist.value = playlist
            p.setMediaItems(playlist.map { it.toMediaItem() })
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
            val queueIdsStr = queue.joinToString(",") { it.id.toString() }
            prefs.edit()
                .putString("saved_queue_ids", queueIdsStr)
                .putLong("last_track_id", track.id)
                .putLong("last_position_ms", player?.currentPosition ?: 0L)
                .apply()
        }
    }

    private fun restoreSavedState() {
        if (_currentPlaylist.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val savedQueueIdsStr = prefs.getString("saved_queue_ids", null) ?: return@launch
                val savedTrackId = prefs.getLong("last_track_id", -1L)
                val savedPositionMs = prefs.getLong("last_position_ms", 0L)

                if (savedQueueIdsStr.isEmpty() || savedTrackId == -1L) return@launch

                val queueIds = savedQueueIdsStr.split(",").mapNotNull { it.toLongOrNull() }
                if (queueIds.isEmpty()) return@launch

                val allSongs = LocalAudioRepository(getApplication()).getLocalSongs()
                val songMap = allSongs.associateBy { it.id }

                val restoredQueue = queueIds.mapNotNull { songMap[it] }
                if (restoredQueue.isEmpty()) return@launch

                val restoredTrack = songMap[savedTrackId] ?: restoredQueue.first()
                val restoredTrackIndex = restoredQueue.indexOf(restoredTrack).coerceAtLeast(0)

                _currentPlaylist.value = restoredQueue
                setCurrentTrack(restoredTrack)

                val p = player ?: return@launch
                p.setMediaItems(restoredQueue.map { it.toMediaItem() }, restoredTrackIndex, savedPositionMs)
                p.prepare()
                _currentPosition.value = savedPositionMs
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun setCrossfadeEnabled(enabled: Boolean) {
        _isCrossfadeEnabled.value = enabled
        prefs.edit().putBoolean("crossfade_enabled", enabled).apply()
        if (!enabled) player?.volume = 1f
    }

    fun setCrossfadeSeconds(seconds: Float) {
        _crossfadeSeconds.value = seconds
        prefs.edit().putFloat("crossfade_seconds", seconds).apply()
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerEndOfTrack.value = false

        if (minutes <= 0) {
            prefs.edit()
                .remove("sleep_timer_end_at")
                .putBoolean("sleep_timer_end_of_track", false)
                .apply()
            _sleepTimerRemainingMs.value = null
            return
        }

        prefs.edit()
            .putLong("sleep_timer_end_at", System.currentTimeMillis() + minutes * 60_000L)
            .putBoolean("sleep_timer_end_of_track", false)
            .apply()

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
        prefs.edit()
            .remove("sleep_timer_end_at")
            .putBoolean("sleep_timer_end_of_track", true)
            .apply()
        _sleepTimerRemainingMs.value = null
        _sleepTimerEndOfTrack.value = true
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        prefs.edit()
            .remove("sleep_timer_end_at")
            .putBoolean("sleep_timer_end_of_track", false)
            .apply()
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
    }

    fun playNext(tracks: List<TrackUiModel>) {
        tracks.asReversed().forEach(::playNext)
    }

    fun addToQueue(track: TrackUiModel) {
        val p = player ?: return
        p.addMediaItem(track.toMediaItem())
        _currentPlaylist.value = _currentPlaylist.value + track
    }

    fun addToQueue(tracks: List<TrackUiModel>) {
        tracks.forEach(::addToQueue)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val p = player ?: return
        if (fromIndex in 0 until _currentPlaylist.value.size && toIndex in 0 until _currentPlaylist.value.size) {
            p.moveMediaItem(fromIndex, toIndex)
            val updatedList = _currentPlaylist.value.toMutableList()
            val item = updatedList.removeAt(fromIndex)
            updatedList.add(toIndex, item)
            _currentPlaylist.value = updatedList
        }
    }

    fun removeFromQueue(index: Int) {
        val p = player ?: return
        if (index in 0 until _currentPlaylist.value.size) {
            p.removeMediaItem(index)
            val updatedList = _currentPlaylist.value.toMutableList()
            updatedList.removeAt(index)
            _currentPlaylist.value = updatedList
        }
    }
    
    fun seekTo(position: Float) {
        val p = player ?: return
        val positionMs = (position * p.duration).toLong()
        p.seekTo(positionMs)
        _currentPosition.value = positionMs
        saveState()
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        saveState()
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}

@file:OptIn(UnstableApi::class)

package com.amit152116.riffle.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.amit152116.riffle.R
import com.amit152116.riffle.data.prefs.PreferencesManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

import androidx.media3.common.util.UnstableApi
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TimeZone
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.data.db.PLAYBACK_SOURCE_EXTRA_KEY
import com.amit152116.riffle.data.db.PlaybackActionReason
import com.amit152116.riffle.data.db.PlaybackActionType
import com.amit152116.riffle.data.db.PlaybackEndReason
import com.amit152116.riffle.data.db.PlaybackSource
import com.amit152116.riffle.data.db.playback.entities.PlaybackAction
import com.amit152116.riffle.data.db.playback.entities.PlaybackEvent
import com.amit152116.riffle.log.RiffleLog
import androidx.media3.common.PlaybackException

private const val CHECKPOINT_INTERVAL_MS = 15_000L

class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appContainer by lazy { AppContainer.getInstance(this) }
    private val log = RiffleLog(this)

    // Every open/close of a PlaybackEvent goes through transition() under this
    // lock. Without it, two Player.Listener callbacks firing close together
    // (e.g. restoreSavedState()'s queue restore immediately followed by a
    // user tap) can each read currentEventId before the other's insert has
    // set it, silently dropping a close and/or scrambling which event id is
    // "current". Confirmed on-device: two tracks started 18ms apart both sat
    // with endedAt=NULL - see PlaybackEvent's own kdoc for why that matters.
    private val eventMutex = Mutex()
    @Volatile private var currentEventId: Long? = null

    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { PreferencesManager.getInstance(this) }
    private val sleepTimerCheck = object : Runnable {
        override fun run() {
            val player = mediaLibrarySession?.player
            val endAt = prefs.getSleepTimerEndAt()
            val endOfTrack = prefs.getSleepTimerEndOfTrack()
            val timedOut = endAt > 0L && System.currentTimeMillis() >= endAt
            val currentSongEnded = endOfTrack &&
                player != null &&
                player.isPlaying &&
                player.duration > 0L &&
                player.currentPosition >= player.duration - 250L

            if (timedOut || currentSongEnded) {
                player?.pause()
                prefs.clearSleepTimerEndAt(endOfTrack = false)
            }
            sleepTimerHandler.postDelayed(this, 1_000L)
        }
    }

    // Periodic position checkpoint while playing - without this, an event
    // orphaned by a killed process (or the bugs the mutex above fixes) has
    // no last-known position to reconcile with beyond where it started.
    // checkpointPlaybackEvent() itself is guarded by "WHERE endedAt IS NULL"
    // (see PlaybackDao), so checkpointing an id that's already been closed
    // by a concurrent transition is a harmless no-op.
    private val checkpointHandler = Handler(Looper.getMainLooper())
    private val checkpointTick = object : Runnable {
        override fun run() {
            val player = mediaLibrarySession?.player
            val eventId = currentEventId
            if (eventId != null && player != null && player.isPlaying) {
                val position = player.currentPosition
                serviceScope.launch {
                    appContainer.playbackRepository.checkpointPlaybackEvent(eventId, System.currentTimeMillis(), position)
                }
            }
            checkpointHandler.postDelayed(this, CHECKPOINT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // First thing, before the player/listener exist to open anything new:
        // close out whatever a previous process instance left open. See
        // PlaybackEvent's kdoc - "reconciled on next app launch" was
        // documented but never actually wired until now.
        reconcileUnclosedEvents()

        // Let ExoPlayer own the complete audio-focus lifecycle. With focus handling
        // enabled, it requests focus only when playback starts, abandons it when
        // playback stops, pauses for permanent/transient losses when appropriate,
        // ducks for transient may-duck losses (for example, navigation prompts),
        // and resumes after focus is regained when playback was active before the
        // interruption.
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            // The second argument is important: without it, ExoPlayer does not
            // request audio focus and competing audio can play over Riffle.
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            // Prevent unexpectedly loud playback when an output device disconnects.
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateCustomLayout(shuffleModeEnabled)
            }

            // The sole place a PlaybackEvent opens for "a different item started" -
            // always closes whatever was open first, unconditionally, unlike the
            // reason-gated close this used to have (PLAYLIST_CHANGED, fired by
            // PlayerViewModel.playTrack()'s setMediaItems() on every track tap,
            // was never in the close list, so tapping a track never closed the
            // previous event). Opens the new item's event only if playWhenReady
            // is already true - setMediaItems() during a cold-start queue
            // restore (PlayerViewModel.restoreSavedState(), which calls
            // prepare() but never play()) fires this with playWhenReady still
            // false, and without this guard it logged a play that never
            // actually started. If the user does press play afterward,
            // onPlayWhenReadyChanged below opens it then.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val closeReason = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> PlaybackEndReason.TRACK_ENDED
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> PlaybackEndReason.USER_SKIPPED
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> PlaybackEndReason.USER_SKIPPED
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> PlaybackEndReason.TRACK_ENDED
                    else -> PlaybackEndReason.UNKNOWN
                }
                // player.currentPosition already reflects the NEW item by the time
                // this fires - best available approximation for both the outgoing
                // close position and the incoming start position, same limitation
                // this code had before. Precise per-segment position is what
                // checkpointing is for, not this instant.
                val itemToOpen = mediaItem?.takeIf { player.playWhenReady }
                transition(itemToOpen, player.currentPosition, player.duration, closeReason, player.currentPosition)
            }

            // Pause/focus-loss/becoming-noisy no longer CLOSE the event - a
            // pause is a mid-play interruption, not the end of the "playback
            // attempt" PlaybackEvent models (see its kdoc), and closing here
            // meant every pause/resume cycle logged as a fresh play + a
            // USER_SKIPPED close, double-counting getMostPlayedTracksFlow's
            // COUNT(*) and making PlaybackAction rows (which are meant to span
            // pauses within one event) impossible to attach correctly. Instead:
            // log a PAUSE/RESUME action against whatever event is already open,
            // and leave it open. Only onMediaItemTransition/STATE_ENDED/
            // onPlayerError close an event now.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val eventId = currentEventId
                if (!playWhenReady) {
                    // END_OF_MEDIA_ITEM isn't a real pause - STATE_ENDED (below)
                    // is about to close the event anyway.
                    if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && eventId != null) {
                        val actionReason = when (reason) {
                            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> PlaybackActionReason.USER
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> PlaybackActionReason.BECOMING_NOISY
                            // Media3 doesn't distinguish permanent vs. transient
                            // loss in this callback - both report AUDIO_FOCUS_LOSS.
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> PlaybackActionReason.UNKNOWN
                            else -> PlaybackActionReason.UNKNOWN
                        }
                        logAction(eventId, PlaybackActionType.PAUSE, player.currentPosition, actionReason)
                    }
                } else if (player.playbackState == Player.STATE_READY) {
                    val currentItem = player.currentMediaItem
                    if (currentItem != null) {
                        if (eventId != null) {
                            val actionReason = when (reason) {
                                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> PlaybackActionReason.USER
                                Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> PlaybackActionReason.REMOTE
                                else -> PlaybackActionReason.UNKNOWN
                            }
                            logAction(eventId, PlaybackActionType.RESUME, player.currentPosition, actionReason)
                        } else {
                            // Nothing open - either the cold-start-restore item
                            // above skipped opening one, or reconciliation left
                            // none. Either way this is genuinely a fresh play.
                            openIfNoneOpen(currentItem, player.currentPosition, player.duration)
                        }
                    }
                }
            }

            // Logs user seeks within the currently playing item. A seek that
            // also changes the item (skip-to-next/previous) is a transition,
            // handled entirely by onMediaItemTransition - excluded here by the
            // mediaItemIndex check so it isn't double-logged as a seek too.
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val eventId = currentEventId
                if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                    eventId != null &&
                    oldPosition.mediaItemIndex == newPosition.mediaItemIndex
                ) {
                    logSeek(eventId, oldPosition.positionMs, newPosition.positionMs)
                }
            }

            // Needed alongside onPlayWhenReadyChanged, not instead of it: a
            // fresh play() call sets playWhenReady=true while playback is
            // still STATE_BUFFERING (prepare() hasn't finished), so that
            // callback's own STATE_READY check is false and it opens nothing.
            // This catches the READY that arrives afterward. openIfNoneOpen's
            // currentEventId==null guard (under the same mutex) makes calling
            // it from both places safe - whichever fires second is a no-op.
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (player.playWhenReady) {
                            player.currentMediaItem?.let {
                                openIfNoneOpen(it, player.currentPosition, player.duration)
                            }
                        }
                    }
                    Player.STATE_ENDED -> {
                        transition(newItem = null, startPositionMs = 0L, durationMs = 0L, closeReason = PlaybackEndReason.TRACK_ENDED, positionAtClose = player.currentPosition)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                transition(newItem = null, startPositionMs = 0L, durationMs = 0L, closeReason = PlaybackEndReason.ERROR, positionAtClose = player.currentPosition)
            }
        })

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                    .add(Player.COMMAND_SET_SHUFFLE_MODE)
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .build()
                return MediaSession.ConnectionResult.accept(
                    connectionResult.availableSessionCommands,
                    availablePlayerCommands
                )
            }

            override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
                super.onPostConnect(session, controller)
                updateCustomLayout(session.player.shuffleModeEnabled)
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootItem = MediaItem.Builder()
                    .setMediaId("root")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setIsPlayable(false)
                            .setIsBrowsable(true)
                            .setTitle("Riffle Library")
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback).build()
        updateCustomLayout(player.shuffleModeEnabled)
        sleepTimerHandler.post(sleepTimerCheck)
        checkpointHandler.post(checkpointTick)
    }

    private fun updateCustomLayout(shuffleEnabled: Boolean) {
        val shuffleButton = CommandButton.Builder()
            .setDisplayName("Shuffle")
            .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
            .setIconResId(if (shuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle)
            .build()

        mediaLibrarySession?.setCustomLayout(ImmutableList.of(shuffleButton))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        sleepTimerHandler.removeCallbacks(sleepTimerCheck)
        checkpointHandler.removeCallbacks(checkpointTick)
        mediaLibrarySession?.player?.release()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        // Whatever's still open (currentEventId) is left for the next
        // process's reconcileUnclosedEvents() to close - see its kdoc.
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Closes whatever event is currently open (if any) and opens a new one
     * for [newItem] (if any), as a single atomic step under [eventMutex] -
     * see the property's kdoc for why that matters. Called for every
     * transition that definitely ends whatever was open: item change, pause,
     * natural end, or error.
     */
    private fun transition(
        newItem: MediaItem?,
        startPositionMs: Long,
        durationMs: Long,
        closeReason: PlaybackEndReason,
        positionAtClose: Long
    ) {
        serviceScope.launch {
            eventMutex.withLock {
                val openId = currentEventId
                currentEventId = null
                if (openId != null) {
                    appContainer.playbackRepository.closePlaybackEvent(
                        id = openId,
                        endedAt = System.currentTimeMillis(),
                        endPositionMs = positionAtClose,
                        endReason = closeReason
                    )
                }
                if (newItem != null) {
                    currentEventId = openEvent(newItem, startPositionMs, durationMs)
                }
            }
        }
    }

    /** Opens a new event only if none is currently open - the resume-after-pause path. */
    private fun openIfNoneOpen(item: MediaItem, startPositionMs: Long, durationMs: Long) {
        serviceScope.launch {
            eventMutex.withLock {
                if (currentEventId == null) {
                    currentEventId = openEvent(item, startPositionMs, durationMs)
                }
            }
        }
    }

    private fun logAction(eventId: Long, type: PlaybackActionType, positionMs: Long, reason: PlaybackActionReason) {
        serviceScope.launch {
            appContainer.playbackRepository.insertPlaybackAction(
                PlaybackAction(eventId = eventId, type = type, atMs = System.currentTimeMillis(), positionMs = positionMs, reason = reason)
            )
        }
    }

    private fun logSeek(eventId: Long, fromPositionMs: Long, toPositionMs: Long) {
        serviceScope.launch {
            appContainer.playbackRepository.insertPlaybackAction(
                PlaybackAction(
                    eventId = eventId, type = PlaybackActionType.SEEK, atMs = System.currentTimeMillis(),
                    positionMs = fromPositionMs, toPositionMs = toPositionMs, reason = PlaybackActionReason.USER
                )
            )
        }
    }

    /** Must be called while holding [eventMutex]. */
    private suspend fun openEvent(mediaItem: MediaItem, startPositionMs: Long, durationMs: Long): Long? {
        val mediaStoreId = mediaItem.mediaId.toLongOrNull() ?: return null
        val trackId = appContainer.resolveTrackId(mediaStoreId) ?: return null
        val track = appContainer.libraryRepository.getTrackById(trackId) ?: return null

        // Absent entirely (not just unparseable) means a controller outside
        // our own UI set this MediaItem directly - see PLAYBACK_SOURCE_EXTRA_KEY's
        // kdoc. That's PlaybackSource.EXTERNAL, never MANUAL: MANUAL is a
        // specific claim ("tapped in Riffle's own UI") this case can't back up.
        val sourceName = mediaItem.mediaMetadata.extras?.getString(PLAYBACK_SOURCE_EXTRA_KEY)
        val source = sourceName?.let { runCatching { PlaybackSource.valueOf(it) }.getOrNull() } ?: PlaybackSource.EXTERNAL

        val event = PlaybackEvent(
            trackId = trackId,
            audioContentId = track.audioContentId,
            startedAt = System.currentTimeMillis(),
            startPositionMs = startPositionMs,
            durationMsSnapshot = durationMs.takeIf { it > 0 },
            source = source,
            tzOffsetMinutes = TimeZone.getDefault().rawOffset / 60000
        )
        return appContainer.playbackRepository.insertPlaybackEvent(event)
    }

    /**
     * Closes every row this process's predecessor left with endedAt=NULL -
     * a killed process, or (before the mutex fix above existed) a lost
     * close. Runs once, at the very start of onCreate, before the player
     * exists to open anything new.
     */
    private fun reconcileUnclosedEvents() {
        serviceScope.launch {
            eventMutex.withLock {
                val unclosed = appContainer.playbackRepository.getUnclosedPlaybackEvents()
                for (event in unclosed) {
                    appContainer.playbackRepository.closePlaybackEvent(
                        id = event.id,
                        endedAt = event.lastCheckpointAt ?: event.startedAt,
                        endPositionMs = event.lastPositionMs,
                        endReason = PlaybackEndReason.UNCLOSED
                    )
                }
                if (unclosed.isNotEmpty()) {
                    log.w("Reconciled ${unclosed.size} unclosed playback event(s) from a previous run")
                }
            }
        }
    }
}

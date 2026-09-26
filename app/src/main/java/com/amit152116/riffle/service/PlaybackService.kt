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
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

import androidx.media3.common.util.UnstableApi
import android.os.Handler
import android.os.Looper

class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private val sleepTimerPrefs by lazy {
        getSharedPreferences("riffle_player_prefs", MODE_PRIVATE)
    }
    private val sleepTimerCheck = object : Runnable {
        override fun run() {
            val player = mediaLibrarySession?.player
            val endAt = sleepTimerPrefs.getLong("sleep_timer_end_at", 0L)
            val endOfTrack = sleepTimerPrefs.getBoolean("sleep_timer_end_of_track", false)
            val timedOut = endAt > 0L && System.currentTimeMillis() >= endAt
            val currentSongEnded = endOfTrack &&
                player != null &&
                player.isPlaying &&
                player.duration > 0L &&
                player.currentPosition >= player.duration - 250L

            if (timedOut || currentSongEnded) {
                player?.pause()
                sleepTimerPrefs.edit()
                    .remove("sleep_timer_end_at")
                    .putBoolean("sleep_timer_end_of_track", false)
                    .apply()
            }
            sleepTimerHandler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()

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
        mediaLibrarySession?.player?.release()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        super.onDestroy()
    }
}

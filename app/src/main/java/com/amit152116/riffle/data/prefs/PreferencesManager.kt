package com.amit152116.riffle.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.amit152116.riffle.data.model.AlbumSortOrder
import com.amit152116.riffle.data.model.AlbumSongSortOrder
import com.amit152116.riffle.data.model.ArtistSortOrder
import com.amit152116.riffle.data.model.FolderSortOrder
import com.amit152116.riffle.data.model.GridStyle
import com.amit152116.riffle.data.model.SongSortOrder

/**
 * Sole gateway to every SharedPreferences read/write in the app. No other
 * class should call Context.getSharedPreferences() directly - add a typed
 * accessor here instead, so every key that exists is visible in one file
 * rather than scattered as string literals across ViewModels/Service.
 *
 * Two underlying files are kept (library vs player) to match what's already
 * on disk for existing installs - merging them would need a migration this
 * class doesn't do. Both are reachable from anywhere via [getInstance], which
 * is why this takes an application Context: PlaybackService (a Service, not
 * a ViewModel) needs the same sleep-timer keys PlayerViewModel writes.
 */
class PreferencesManager private constructor(context: Context) {
    private val libraryPrefs = context.getSharedPreferences(LIBRARY_PREFS_FILE, Context.MODE_PRIVATE)
    private val playerPrefs = context.getSharedPreferences(PLAYER_PREFS_FILE, Context.MODE_PRIVATE)

    // --- Library: scan/browse settings ---

    var minimumDurationSeconds: Int
        get() = libraryPrefs.getInt(KEY_MINIMUM_DURATION_SECONDS, DEFAULT_MINIMUM_DURATION_SECONDS)
        set(value) = libraryPrefs.edit { putInt(KEY_MINIMUM_DURATION_SECONDS, value) }

    var albumGridStyle: GridStyle
        get() = readGridStyle(libraryPrefs, KEY_ALBUM_GRID_STYLE, GridStyle.GRID)
        set(value) = libraryPrefs.edit { putInt(KEY_ALBUM_GRID_STYLE, value.ordinal) }

    var artistGridStyle: GridStyle
        get() = readGridStyle(libraryPrefs, KEY_ARTIST_GRID_STYLE, GridStyle.CIRCULAR)
        set(value) = libraryPrefs.edit { putInt(KEY_ARTIST_GRID_STYLE, value.ordinal) }

    var folderGridStyle: GridStyle
        get() = readGridStyle(libraryPrefs, KEY_FOLDER_GRID_STYLE, GridStyle.GRID)
        set(value) = libraryPrefs.edit { putInt(KEY_FOLDER_GRID_STYLE, value.ordinal) }

    var albumGridSize: Int
        get() = libraryPrefs.getInt(KEY_ALBUM_GRID_SIZE, 2)
        set(value) = libraryPrefs.edit { putInt(KEY_ALBUM_GRID_SIZE, value) }

    var artistGridSize: Int
        get() = libraryPrefs.getInt(KEY_ARTIST_GRID_SIZE, 2)
        set(value) = libraryPrefs.edit { putInt(KEY_ARTIST_GRID_SIZE, value) }

    var folderGridSize: Int
        get() = libraryPrefs.getInt(KEY_FOLDER_GRID_SIZE, 2)
        set(value) = libraryPrefs.edit { putInt(KEY_FOLDER_GRID_SIZE, value) }

    var songSortOrder: SongSortOrder
        get() = readEnum(libraryPrefs, KEY_SONG_SORT_ORDER, SongSortOrder.TITLE_A_Z)
        set(value) = libraryPrefs.edit { putInt(KEY_SONG_SORT_ORDER, value.ordinal) }

    var albumSortOrder: AlbumSortOrder
        get() = readEnum(libraryPrefs, KEY_ALBUM_SORT_ORDER, AlbumSortOrder.ALBUM_A_Z)
        set(value) = libraryPrefs.edit { putInt(KEY_ALBUM_SORT_ORDER, value.ordinal) }

    var albumSongSortOrder: AlbumSongSortOrder
        get() = readEnum(libraryPrefs, KEY_ALBUM_SONG_SORT_ORDER, AlbumSongSortOrder.TITLE_A_Z)
        set(value) = libraryPrefs.edit { putInt(KEY_ALBUM_SONG_SORT_ORDER, value.ordinal) }

    var artistSortOrder: ArtistSortOrder
        get() = readEnum(libraryPrefs, KEY_ARTIST_SORT_ORDER, ArtistSortOrder.ARTIST_A_Z)
        set(value) = libraryPrefs.edit { putInt(KEY_ARTIST_SORT_ORDER, value.ordinal) }

    var folderSortOrder: FolderSortOrder
        get() = readEnum(libraryPrefs, KEY_FOLDER_SORT_ORDER, FolderSortOrder.FOLDER_A_Z)
        set(value) = libraryPrefs.edit { putInt(KEY_FOLDER_SORT_ORDER, value.ordinal) }

    private fun readGridStyle(prefs: SharedPreferences, key: String, default: GridStyle): GridStyle {
        val ordinal = prefs.getInt(key, default.ordinal)
        return GridStyle.entries.getOrElse(ordinal) { default }
    }

    private inline fun <reified T : Enum<T>> readEnum(prefs: SharedPreferences, key: String, default: T): T {
        val ordinal = prefs.getInt(key, default.ordinal)
        return enumValues<T>().getOrElse(ordinal) { default }
    }

    // --- Player: crossfade, sleep timer, saved queue/position ---

    var crossfadeEnabled: Boolean
        get() = playerPrefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
        set(value) = playerPrefs.edit { putBoolean(KEY_CROSSFADE_ENABLED, value) }

    var crossfadeSeconds: Float
        get() = playerPrefs.getFloat(KEY_CROSSFADE_SECONDS, DEFAULT_CROSSFADE_SECONDS)
        set(value) = playerPrefs.edit { putFloat(KEY_CROSSFADE_SECONDS, value) }

    data class SavedQueueState(val queueIds: List<Long>, val trackId: Long, val positionMs: Long)

    /** One atomic write - matches the caller's "save queue + track + position together" intent. */
    fun setSavedQueueState(queueIds: List<Long>, trackId: Long, positionMs: Long) {
        playerPrefs.edit {
            putString(KEY_SAVED_QUEUE_IDS, queueIds.joinToString(","))
            putLong(KEY_LAST_TRACK_ID, trackId)
            putLong(KEY_LAST_POSITION_MS, positionMs)
        }
    }

    /** Null when nothing was ever saved, or what's saved can't be restored. */
    fun getSavedQueueState(): SavedQueueState? {
        val idsStr = playerPrefs.getString(KEY_SAVED_QUEUE_IDS, null)
        if (idsStr.isNullOrEmpty()) return null
        val trackId = playerPrefs.getLong(KEY_LAST_TRACK_ID, -1L)
        if (trackId == -1L) return null
        val ids = idsStr.split(",").mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return null
        val positionMs = playerPrefs.getLong(KEY_LAST_POSITION_MS, 0L)
        return SavedQueueState(ids, trackId, positionMs)
    }

    fun setSleepTimerEndAt(endAtMs: Long, endOfTrack: Boolean = false) {
        playerPrefs.edit {
            putLong(KEY_SLEEP_TIMER_END_AT, endAtMs)
            putBoolean(KEY_SLEEP_TIMER_END_OF_TRACK, endOfTrack)
        }
    }

    /** Clears the fixed end time; endOfTrack controls whether that mode is left armed. */
    fun clearSleepTimerEndAt(endOfTrack: Boolean) {
        playerPrefs.edit {
            remove(KEY_SLEEP_TIMER_END_AT)
            putBoolean(KEY_SLEEP_TIMER_END_OF_TRACK, endOfTrack)
        }
    }

    fun getSleepTimerEndAt(): Long = playerPrefs.getLong(KEY_SLEEP_TIMER_END_AT, 0L)

    fun getSleepTimerEndOfTrack(): Boolean = playerPrefs.getBoolean(KEY_SLEEP_TIMER_END_OF_TRACK, false)

    companion object {
        private const val LIBRARY_PREFS_FILE = "riffle_library_prefs"
        private const val PLAYER_PREFS_FILE = "riffle_player_prefs"

        private const val KEY_MINIMUM_DURATION_SECONDS = "minimum_duration_seconds"
        private const val DEFAULT_MINIMUM_DURATION_SECONDS = 30
        private const val KEY_ALBUM_GRID_STYLE = "album_grid_style"
        private const val KEY_ARTIST_GRID_STYLE = "artist_grid_style"
        private const val KEY_FOLDER_GRID_STYLE = "folder_grid_style"
        private const val KEY_ALBUM_GRID_SIZE = "album_grid_size"
        private const val KEY_ARTIST_GRID_SIZE = "artist_grid_size"
        private const val KEY_FOLDER_GRID_SIZE = "folder_grid_size"
        private const val KEY_SONG_SORT_ORDER = "song_sort_order"
        private const val KEY_ALBUM_SORT_ORDER = "album_sort_order"
        private const val KEY_ALBUM_SONG_SORT_ORDER = "album_song_sort_order"
        private const val KEY_ARTIST_SORT_ORDER = "artist_sort_order"
        private const val KEY_FOLDER_SORT_ORDER = "folder_sort_order"

        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_SECONDS = "crossfade_seconds"
        private const val DEFAULT_CROSSFADE_SECONDS = 3f
        private const val KEY_SAVED_QUEUE_IDS = "saved_queue_ids"
        private const val KEY_LAST_TRACK_ID = "last_track_id"
        private const val KEY_LAST_POSITION_MS = "last_position_ms"
        private const val KEY_SLEEP_TIMER_END_AT = "sleep_timer_end_at"
        private const val KEY_SLEEP_TIMER_END_OF_TRACK = "sleep_timer_end_of_track"

        @Volatile private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager =
            instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
    }
}

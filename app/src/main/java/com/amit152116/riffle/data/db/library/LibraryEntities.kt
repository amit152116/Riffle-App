package com.amit152116.riffle.data.db.library

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.AbsentReason
import com.amit152116.riffle.data.db.HashMethod
import com.amit152116.riffle.data.db.RunStatus

@Entity(indices = [Index("audioHash", "hashMethod", unique = true)])
data class AudioContent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioHash: String,
    val hashMethod: HashMethod,
    val duration: Double?,
    val codec: String?,
    val sampleRate: Int?,
    val channels: Int?,
    val firstSeenAt: Long?
)

@Entity
data class ScanRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long?,
    val completedAt: Long?,
    val roots: String?,
    val status: RunStatus
)

@Entity(
    foreignKeys = [
        ForeignKey(AudioContent::class, ["id"], ["audioContentId"]),
        ForeignKey(ScanRun::class, ["id"], ["lastSeenScanId"])
    ],
    indices = [
        Index("path", unique = true),
        Index("audioContentId"),
        Index("lastSeenScanId"),
        Index("present"),
        Index("mediaStoreId")
    ]
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val size: Long?,
    val mtime: Double?,
    val audioContentId: Long?,
    // MediaStore.Audio.Media._ID. Every playback/queue/search/browse capture site works off
    // TrackUiModel, which is keyed by this id, not Room's Track.id - resolve through this
    // column rather than assuming the two id spaces line up.
    val mediaStoreId: Long?,
    // MediaStore.Audio.Media.ALBUM_ID, for the legacy albumart Uri AsyncArtwork already
    // loads via Coil's plain ContentResolver fetch (content://.../audio/albumart/{id}).
    val albumId: Long?,
    // MediaStore's own DURATION, in ms. Written on the fast metadata pass, before any
    // audio hashing happens - lets the UI list query run (and filter by min duration)
    // without joining AudioContent, whose duration only exists after hashing completes.
    val durationMs: Long?,
    val bitrate: Int?,
    val tagTitle: String?,
    val tagArtist: String?,
    val tagAlbum: String?,
    val tagGenre: String?,
    val tagCompleteness: Int,
    val lastSeenScanId: Long?,
    val present: Boolean = true,
    val absentReason: AbsentReason?,
    val scannedAt: Long?,
    // MediaStore.Audio.Media.DATE_ADDED, epoch seconds - the device's real "added to
    // library" timestamp, unlike Room's own autoincrement id (which only reflects scan/
    // insertion order and is the same for every track from one bulk initial scan).
    val dateAdded: Long? = null,
    // MediaStore.Audio.Media.DISPLAY_NAME - the real on-disk file name (e.g. "Song.mp3").
    // `path` is a content:// Uri whose last segment is just MediaStore's numeric _ID, not
    // a usable file name.
    val displayName: String? = null,
    // MediaStore.Audio.Media.RELATIVE_PATH (e.g. "Music/" or "Download/Playlists/") - the
    // scoped-storage-era stand-in for a folder location, since apps targeting API 29+ can't
    // read another app's absolute filesystem path off MediaStore.
    val relativePath: String? = null
)

@Entity
data class IngestError(
    @PrimaryKey val path: String,
    val dev: Long?,
    val inode: Long?,
    val size: Long?,
    val mtime: Double?,
    val stage: String,
    val message: String?,
    val attempts: Int = 1,
    val lastAttemptAt: Long?,
    val resolvedAt: Long?
)

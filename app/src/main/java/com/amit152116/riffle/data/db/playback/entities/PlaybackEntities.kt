package com.amit152116.riffle.data.db.playback.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.OriginKind
import com.amit152116.riffle.data.db.PlaybackActionReason
import com.amit152116.riffle.data.db.PlaybackActionType
import com.amit152116.riffle.data.db.PlaybackEndReason
import com.amit152116.riffle.data.db.PlaybackSource
import com.amit152116.riffle.data.db.library.Track

// One row per playback attempt, append-only. trackId is nullable + SET_NULL so
// a library prune never deletes history. audioContentId is ALSO nullable: the
// library scanner splits into a fast metadata pass (writes the Track row
// immediately) and a background hash pass (sets Track.audioContentId once
// hashing completes) - a track can be played before it's hashed, and that
// play must still be logged, not silently dropped for lack of a non-null
// content id. Backfill audioContentId from Track once hashing catches up if a
// content-keyed join is needed later.
// endedAt/endReason stay null while playing; reconciled on next app launch for
// rows a process death left open (see PlaybackDao).
@Entity(
    foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("trackId"), Index("audioContentId"), Index("startedAt")]
)
data class PlaybackEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long?,
    val audioContentId: Long?,
    val startedAt: Long,
    val endedAt: Long? = null,
    val lastCheckpointAt: Long? = null,
    val startPositionMs: Long,
    val lastPositionMs: Long = startPositionMs,
    val endPositionMs: Long? = null,
    // AudioContent.duration is nullable and can be re-measured later; snapshotting
    // it here is a raw fact about what was playing, not a derived aggregate.
    val durationMsSnapshot: Long?,
    val source: PlaybackSource,
    val endReason: PlaybackEndReason? = null,
    val tzOffsetMinutes: Int,
    val outputRoute: String? = null,
    val startVolume: Float? = null,
    // Set when source is SHUFFLE/SMART_SHUFFLE/RECOMMENDATION - which candidate
    // row caused this play. See OriginKind kdoc: not a DB-enforced FK.
    val originKind: OriginKind? = null,
    val originId: Long? = null
)

// Pause/resume/seek, merged into one table (the doc's three separate tables -
// seeks/pauses/interruptions - are all "thing happened at position X for
// reason Y"). Counts (pauseCount, seekCount, ...) are derived via
// COUNT(*)/type at query time, never stored here.
@Entity(
    foreignKeys = [ForeignKey(PlaybackEvent::class, ["id"], ["eventId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("eventId")]
)
data class PlaybackAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val type: PlaybackActionType,
    val atMs: Long,
    val positionMs: Long,
    // Seek target only; null for PAUSE/RESUME. Needed for a future
    // favorite-segment feature (from -> to), which can't be recovered later.
    val toPositionMs: Long? = null,
    val reason: PlaybackActionReason
)

@Entity(foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"])])
data class FavoriteTrack(
    @PrimaryKey val trackId: Long,
    val addedAt: Long)

@Entity(foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"])])
data class BlacklistedTrack(
    @PrimaryKey val trackId: Long,
    val addedAt: Long)

@Entity
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    foreignKeys = [
        ForeignKey(Playlist::class, ["id"], ["playlistId"]),
        ForeignKey(Track::class, ["id"], ["trackId"])
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)

// Query projection, not a table. playlistTrackId is PlaylistTrack.id, kept
// separate from Track.id because the same track can appear more than once in
// a playlist (see PlaylistTrack's kdoc) - the UI must key list items and
// route removeFromPlaylist() by this id, never by the embedded track's id.
data class PlaylistTrackWithTrack(
    val playlistTrackId: Long,
    @Embedded val track: Track
)

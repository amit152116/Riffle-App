package com.amit152116.riffle.data.db.playback.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.ShuffleMode
import com.amit152116.riffle.data.db.library.Track

// One row per generated shuffle run. configHash follows the same versioning
// pattern as AudioFeatures/Fingerprint - when the shuffle algorithm changes,
// old decisions stay attributable to the config that produced them.
@Entity(
    foreignKeys = [ForeignKey(Track::class, ["id"], ["sourceTrackId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("sourceTrackId"), Index("startedAt")]
)
data class ShuffleSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val mode: ShuffleMode,
    val sourceTrackId: Long? = null,
    val configHash: String
)

// One row per candidate the session considered. played/skipped are NOT stored
// here - join PlaybackEvent where originKind=SHUFFLE_DECISION and
// originId=this row's id, and derive from that instead.
@Entity(
    foreignKeys = [
        ForeignKey(ShuffleSession::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("sessionId"), Index("trackId")]
)
data class ShuffleDecision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val trackId: Long?,
    val position: Int,
    val score: Double,
    val reason: String
)

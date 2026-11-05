package com.amit152116.riffle.data.db.playback.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.OriginKind
import com.amit152116.riffle.data.db.PlaybackSource
import com.amit152116.riffle.data.db.QueueAction
import com.amit152116.riffle.data.db.library.Track

// One row per queue mutation (PlayerViewModel.addToQueue/removeFromQueue/
// moveQueueItem). fromPosition/toPosition only apply to MOVE; removedBeforePlay
// and playedFromQueue are NOT stored here - both derive from joining
// PlaybackEvent (trackId + timestamp after queuedAt), same append-only-facts
// rule as PlaybackEvent itself.
@Entity(
    foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("trackId"), Index("queuedAt")]
)
data class QueueEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long?,
    val queuedAt: Long,
    val action: QueueAction,
    val fromPosition: Int? = null,
    val toPosition: Int? = null,
    val origin: PlaybackSource,
    // Set when origin is SHUFFLE/SMART_SHUFFLE/RECOMMENDATION. See OriginKind kdoc.
    val originKind: OriginKind? = null,
    val originId: Long? = null
)

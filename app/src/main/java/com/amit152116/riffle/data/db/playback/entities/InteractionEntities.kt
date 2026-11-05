package com.amit152116.riffle.data.db.playback.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.InteractionType
import com.amit152116.riffle.data.db.library.Track

// Append-only history of explicit user signals. FavoriteTrack stays as the
// current-state cache the UI reads for a cheap "is favorite" lookup; this is
// the log behind it, since overwriting FavoriteTrack alone would lose a
// favorited -> unfavorited -> favorited cycle. Write both on toggle.
@Entity(
    foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("trackId"), Index("timestamp")]
)
data class TrackInteraction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long?,
    val timestamp: Long,
    val type: InteractionType
)

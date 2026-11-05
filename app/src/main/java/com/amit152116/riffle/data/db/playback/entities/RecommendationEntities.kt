package com.amit152116.riffle.data.db.playback.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.RecommendationActionType
import com.amit152116.riffle.data.db.RecommendationSurface
import com.amit152116.riffle.data.db.library.Track

// Same shape as ShuffleSession/ShuffleDecision (one generated batch + its
// candidates) but kept as its own pair rather than merged: surface/algorithm
// mean something different here than mode/sourceTrackId do for shuffle, and
// merging would just add nullable columns each side doesn't use.
@Entity(
    foreignKeys = [ForeignKey(Track::class, ["id"], ["sourceTrackId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("sourceTrackId"), Index("generatedAt")]
)
data class RecommendationSlate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val generatedAt: Long,
    val surface: RecommendationSurface,
    val sourceTrackId: Long? = null,
    val algorithm: String,
    val algorithmVersion: String,
    val configHash: String
)

// One row per candidate actually rendered to the user. shown is implicit
// (the row exists); played is NOT stored here - join PlaybackEvent where
// originKind=RECOMMENDATION_IMPRESSION and originId=this row's id.
@Entity(
    foreignKeys = [
        ForeignKey(RecommendationSlate::class, ["id"], ["slateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("slateId"), Index("trackId")]
)
data class RecommendationImpression(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slateId: Long,
    val trackId: Long?,
    val position: Int,
    val score: Double? = null,
    val reason: String? = null
)

// Append-only clicked/dismissed/queued signal on an impression - never
// mutate a "clickedAt" column on the impression, since a user can click then
// later dismiss the same recommendation and both are meaningful signals.
@Entity(
    foreignKeys = [ForeignKey(RecommendationImpression::class, ["id"], ["impressionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("impressionId")]
)
data class RecommendationAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val impressionId: Long,
    val type: RecommendationActionType,
    val at: Long
)

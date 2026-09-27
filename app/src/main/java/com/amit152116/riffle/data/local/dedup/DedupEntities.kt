package com.amit152116.riffle.data.local.dedup

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.local.DupDecision
import com.amit152116.riffle.data.local.QuarantineState
import com.amit152116.riffle.data.local.fingerprint.MatchRun
import com.amit152116.riffle.data.local.library.AudioContent
import com.amit152116.riffle.data.local.library.Track

@Entity(foreignKeys = [ForeignKey(MatchRun::class, ["id"], ["runId"])], indices = [Index("runId")])
data class DupGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val tier: Int,
    val formedByChain: Boolean = false,
    val decision: DupDecision = DupDecision.PROPOSED,
    val decidedAt: Long?
)

@Entity(
    primaryKeys = ["groupId", "audioContentId"],
    foreignKeys = [
        ForeignKey(DupGroup::class, ["id"], ["groupId"]),
        ForeignKey(AudioContent::class, ["id"], ["audioContentId"])
    ],
    indices = [Index("audioContentId")]
)
data class GroupContent(val groupId: Long, val audioContentId: Long)

@Entity(
    primaryKeys = ["groupId", "trackId"],
    foreignKeys = [
        ForeignKey(DupGroup::class, ["id"], ["groupId"]),
        ForeignKey(Track::class, ["id"], ["trackId"])
    ],
    indices = [Index("trackId")]
)
data class GroupMember(
    val groupId: Long,
    val trackId: Long,
    val isKeeper: Boolean = false,
    val rankScore: String?
)

@Entity(
    foreignKeys = [
        ForeignKey(MatchRun::class, ["id"], ["runId"]),
        ForeignKey(Track::class, ["id"], ["trackId"]),
        ForeignKey(DupGroup::class, ["id"], ["groupId"])
    ],
    indices = [Index("runId"), Index("trackId"), Index("groupId")]
)
data class QuarantineLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val trackId: Long,
    val groupId: Long,
    val srcPath: String,
    val dstPath: String,
    val srcHash: String?,
    val dstHash: String?,
    val movedAt: Long?,
    val state: QuarantineState
)

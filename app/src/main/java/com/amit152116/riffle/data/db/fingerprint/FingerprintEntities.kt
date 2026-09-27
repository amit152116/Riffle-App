package com.amit152116.riffle.data.db.fingerprint

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.FingerprintPurpose
import com.amit152116.riffle.data.db.HashMethod
import com.amit152116.riffle.data.db.RunStatus
import com.amit152116.riffle.data.db.library.AudioContent
import com.amit152116.riffle.data.db.library.Track

@Entity(
    foreignKeys = [ForeignKey(AudioContent::class, ["id"], ["audioContentId"])],
    indices = [
        Index("audioContentId"),
        Index("audioContentId", "analyzer", "analyzerVersion", "configHash", "purpose", "algorithm", unique = true)
    ]
)
data class Fingerprint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioContentId: Long,
    val analyzer: String,
    val analyzerVersion: String,
    val configHash: String,
    val purpose: FingerprintPurpose,
    val algorithm: Int?,
    val fpRaw: ByteArray,
    val fpLength: Int,
    val computedAt: Long?
)

@Entity
data class MatchRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long?,
    val fingerprintConfig: String?,
    val matchConfig: String?,
    val softwareVersion: String?,
    val status: RunStatus
)

@Entity(
    primaryKeys = ["runId", "trackId"],
    foreignKeys = [
        ForeignKey(MatchRun::class, ["id"], ["runId"]),
        ForeignKey(Track::class, ["id"], ["trackId"])
    ],
    indices = [Index("trackId")]
)
data class RunTrack(
    val runId: Long,
    val trackId: Long,
    val path: String,
    val size: Long?,
    val mtime: Double?,
    val audioHash: String?,
    val hashMethod: HashMethod?
)

@Entity(
    primaryKeys = ["runId", "aContentId", "bContentId"],
    foreignKeys = [
        ForeignKey(MatchRun::class, ["id"], ["runId"]),
        ForeignKey(AudioContent::class, ["id"], ["aContentId"]),
        ForeignKey(AudioContent::class, ["id"], ["bContentId"])
    ],
    indices = [Index("aContentId"), Index("bContentId")]
)
data class AudioPair(
    val runId: Long,
    val aContentId: Long,
    val bContentId: Long,
    val bestOffset: Int?,
    val peakVotes: Int?,
    val peakVoteRatio: Double?,
    val matchedSpanItems: Int?,
    val matchedSpanSeconds: Double?,
    val coverageA: Double?,
    val coverageB: Double?,
    val meanBitError: Double?,
    val segmentCount: Int?,
    val tier: Int?,
    val verifiedDirect: Boolean = false
)

package com.amit152116.riffle.data.local.cluster

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.local.library.AudioContent
import com.amit152116.riffle.data.local.library.Track

@Entity
data class ClusterRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nClusters: Int,
    val nTracks: Int,
    val scalerParams: ByteArray?,
    val createdAt: Long?
)

@Entity(
    foreignKeys = [ForeignKey(ClusterRun::class, ["id"], ["runId"])],
    indices = [Index("runId", "clusterId", unique = true)]
)
data class ClusterCentroid(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val clusterId: Int,
    val label: String?,
    val centroid: ByteArray?,
    val nTracks: Int?
)

@Entity(
    primaryKeys = ["runId", "audioContentId"],
    foreignKeys = [
        ForeignKey(ClusterRun::class, ["id"], ["runId"]),
        ForeignKey(AudioContent::class, ["id"], ["audioContentId"])
    ],
    indices = [Index("runId", "clusterId"), Index("audioContentId")]
)
data class ClusterAssignment(
    val runId: Long,
    val audioContentId: Long,
    val clusterId: Int,
    val distanceToCentroid: Double?
)

@Entity(
    primaryKeys = ["trackId", "neighborId"],
    foreignKeys = [
        ForeignKey(Track::class, ["id"], ["trackId"]),
        ForeignKey(Track::class, ["id"], ["neighborId"])
    ],
    indices = [Index("trackId", "combinedScore"), Index("neighborId")]
)
data class TrackSimilarity(
    val trackId: Long,
    val neighborId: Long,
    val mfccNorm: Double,
    val bpmNorm: Double?,
    val keyNorm: Double?,
    val energyNorm: Double?,
    val combinedScore: Double,
    val configHash: String?
)

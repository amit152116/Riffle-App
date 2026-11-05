package com.amit152116.riffle.data.db.cluster

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.library.AudioContent
import com.amit152116.riffle.intelligence.model.ClusterMode
import com.amit152116.riffle.intelligence.model.EnergyBand
import com.amit152116.riffle.intelligence.model.TempoBand
import com.amit152116.riffle.intelligence.model.TimbreBand

/**
 * Run rows are kept as history; only the child rows of superseded runs are pruned.
 * `nTracks` = eligible recordings clustered, `nClusters` = the selected k.
 * `scalerParams` = mean[19] then std[19], float64 little-endian.
 * `nextClusterId` is a monotonic counter: cluster ids are never reused across runs.
 */
@Entity
data class ClusterRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nClusters: Int,
    val nTracks: Int,
    val scalerParams: ByteArray?,
    val createdAt: Long?,
    val configHash: String? = null,
    val inputHash: String? = null,
    val silhouette: Double? = null,
    val kScores: String? = null,
    val durationMs: Long? = null,
    @ColumnInfo(defaultValue = "0") val nextClusterId: Int = 0
)

/**
 * `clusterId` is a stable identity (not a 0..k-1 index); `label` is the generated display name.
 * `centroid` is the RAW (unscaled) member mean, 19 x float64 little-endian, so a later run can
 * re-scale it with its own scaler. `matchDistance` is the distance at which an old id was
 * inherited (null for a fresh id).
 */
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
    val nTracks: Int?,
    val tempoBand: TempoBand? = null,
    val energyBand: EnergyBand? = null,
    val mode: ClusterMode? = null,
    val timbre: TimbreBand? = null,
    val matchDistance: Double? = null
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

/**
 * Directed top-K neighbour edge between RECORDINGS (AudioContent). The pair score is symmetric but
 * A may list B without B listing A. Each `*Distance` is a 0-1 normalised distance (not a feature
 * value); lower = more similar, and `combinedScore` runs the same way.
 */
@Entity(
    primaryKeys = ["audioContentId", "neighborContentId"],
    foreignKeys = [
        ForeignKey(AudioContent::class, ["id"], ["audioContentId"]),
        ForeignKey(AudioContent::class, ["id"], ["neighborContentId"])
    ],
    indices = [Index("audioContentId", "combinedScore"), Index("neighborContentId")]
)
data class ContentSimilarity(
    val audioContentId: Long,
    val neighborContentId: Long,
    val mfccDistance: Double,
    val bpmDistance: Double?,
    val keyDistance: Double?,
    val energyDistance: Double?,
    val combinedScore: Double
)

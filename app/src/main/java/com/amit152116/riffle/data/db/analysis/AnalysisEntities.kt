package com.amit152116.riffle.data.db.analysis

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.library.AudioContent
import com.amit152116.riffle.data.db.library.Track

@Entity(
    foreignKeys = [ForeignKey(AudioContent::class, ["id"], ["audioContentId"])],
    indices = [Index("audioContentId", unique = true), Index("bpm"), Index("keyName")]
)
data class AudioFeatures(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioContentId: Long,
    val bpm: Double?,
    val bpmConfidence: Double?,
    val keyName: String?,
    val scale: String?,
    val keyStrength: Double?,
    val loudnessLufs: Double?,
    val danceability: Double?,
    val energy: Double?,
    val spectralCentroid: Double?,
    val onsetRate: Double?,
    val dynamicComplexity: Double?,
    val dissonance: Double?,
    val zcr: Double?,
    val mfccMean: ByteArray?,
    val extractorVersion: String?,
    val configHash: String?,
    val analyzedAt: Long?
)

@Entity(
    foreignKeys = [
        ForeignKey(AudioContent::class, ["id"], ["audioContentId"]),
        ForeignKey(Track::class, ["id"], ["trackId"])
    ],
    indices = [Index("audioContentId", unique = true), Index("trackId")]
)
data class QualityFlag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioContentId: Long,
    val trackId: Long,
    val peakDb: Double?,
    val meanDb: Double?,
    val clipping: Boolean = false,
    val lowVolume: Boolean = false,
    val silenceSections: Int = 0,
    val analyzedAt: Long?
)

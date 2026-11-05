package com.amit152116.riffle.data.db.analysis

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioFeatures(audioFeatures: AudioFeatures): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQualityFlag(qualityFlag: QualityFlag): Long

    @Query("SELECT * FROM AudioFeatures WHERE audioContentId = :audioContentId")
    suspend fun getAudioFeaturesByAudioContentId(audioContentId: Long): AudioFeatures?

    @Query("SELECT * FROM QualityFlag WHERE audioContentId = :audioContentId")
    suspend fun getQualityFlagByAudioContentId(audioContentId: Long): QualityFlag?

    /**
     * Recordings the engine can use: MFCC + energy present, at least one present Track, and actual music -
     * not shorter than [minDurationSec] (unknown duration is kept) and louder than [silenceLufs]
     * (unknown loudness is kept). Ringtones, voice notes and silent files otherwise become similarity
     * hubs, get shuffled, and make k-means spend clusters on outliers.
     */
    @Query(
        """
        SELECT af.* FROM AudioFeatures af
        JOIN AudioContent ac ON ac.id = af.audioContentId
        WHERE af.mfccMean IS NOT NULL AND af.energy IS NOT NULL
          AND (ac.duration IS NULL OR ac.duration >= :minDurationSec)
          AND (af.loudnessLufs IS NULL OR af.loudnessLufs > :silenceLufs)
          AND EXISTS (SELECT 1 FROM Track t WHERE t.audioContentId = af.audioContentId AND t.present = 1)
        ORDER BY af.audioContentId
        """
    )
    suspend fun getEligibleFeatures(minDurationSec: Double, silenceLufs: Double): List<AudioFeatures>
}

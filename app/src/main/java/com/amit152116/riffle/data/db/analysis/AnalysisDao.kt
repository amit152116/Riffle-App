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
}

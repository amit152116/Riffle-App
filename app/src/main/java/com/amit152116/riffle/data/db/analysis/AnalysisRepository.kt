package com.amit152116.riffle.data.db.analysis

class AnalysisRepository(private val dao: AnalysisDao) {
    suspend fun insertAudioFeatures(audioFeatures: AudioFeatures) = dao.insertAudioFeatures(audioFeatures)
    suspend fun insertQualityFlag(qualityFlag: QualityFlag) = dao.insertQualityFlag(qualityFlag)
    suspend fun getAudioFeaturesByAudioContentId(audioContentId: Long) =
        dao.getAudioFeaturesByAudioContentId(audioContentId)
    suspend fun getQualityFlagByAudioContentId(audioContentId: Long) =
        dao.getQualityFlagByAudioContentId(audioContentId)
}

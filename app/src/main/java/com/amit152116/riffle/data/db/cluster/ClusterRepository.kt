package com.amit152116.riffle.data.db.cluster

class ClusterRepository(private val dao: ClusterDao) {
    suspend fun insertClusterRun(clusterRun: ClusterRun) = dao.insertClusterRun(clusterRun)
    suspend fun insertClusterCentroid(clusterCentroid: ClusterCentroid) = dao.insertClusterCentroid(clusterCentroid)
    suspend fun insertClusterAssignment(clusterAssignment: ClusterAssignment) =
        dao.insertClusterAssignment(clusterAssignment)
    suspend fun getClusterAssignment(runId: Long, audioContentId: Long) =
        dao.getClusterAssignment(runId, audioContentId)
    suspend fun getContentSimilarity(audioContentId: Long, neighborContentId: Long) =
        dao.getContentSimilarity(audioContentId, neighborContentId)

    /**
     * Returns false without inserting when both ids are the same recording - a recording is never
     * meaningfully "similar to itself".
     */
    suspend fun insertContentSimilarity(row: ContentSimilarity): Boolean {
        if (row.audioContentId == row.neighborContentId) return false
        dao.insertContentSimilarity(row)
        return true
    }
}

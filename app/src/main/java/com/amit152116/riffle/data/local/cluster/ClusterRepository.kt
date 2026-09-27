package com.amit152116.riffle.data.local.cluster

class ClusterRepository(private val dao: ClusterDao) {
    suspend fun insertClusterRun(clusterRun: ClusterRun) = dao.insertClusterRun(clusterRun)
    suspend fun insertClusterCentroid(clusterCentroid: ClusterCentroid) = dao.insertClusterCentroid(clusterCentroid)
    suspend fun insertClusterAssignment(clusterAssignment: ClusterAssignment) =
        dao.insertClusterAssignment(clusterAssignment)
    suspend fun getClusterAssignment(runId: Long, audioContentId: Long) =
        dao.getClusterAssignment(runId, audioContentId)
    suspend fun getTrackSimilarity(trackId: Long, neighborId: Long) = dao.getTrackSimilarity(trackId, neighborId)

    /**
     * Returns false without inserting when trackId == neighborId - a track
     * is never meaningfully "similar to itself".
     */
    suspend fun insertTrackSimilarity(trackSimilarity: TrackSimilarity): Boolean {
        if (trackSimilarity.trackId == trackSimilarity.neighborId) return false
        dao.insertTrackSimilarity(trackSimilarity)
        return true
    }
}

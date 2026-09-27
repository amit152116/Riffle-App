package com.amit152116.riffle.data.db.cluster

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ClusterDao {
    @Insert
    suspend fun insertClusterRun(clusterRun: ClusterRun): Long

    @Insert
    suspend fun insertClusterCentroid(clusterCentroid: ClusterCentroid): Long

    @Insert
    suspend fun insertClusterAssignment(clusterAssignment: ClusterAssignment)

    @Insert
    suspend fun insertTrackSimilarity(trackSimilarity: TrackSimilarity)

    @Query("SELECT * FROM ClusterAssignment WHERE runId = :runId AND audioContentId = :audioContentId")
    suspend fun getClusterAssignment(runId: Long, audioContentId: Long): ClusterAssignment?

    @Query("SELECT * FROM TrackSimilarity WHERE trackId = :trackId AND neighborId = :neighborId")
    suspend fun getTrackSimilarity(trackId: Long, neighborId: Long): TrackSimilarity?
}

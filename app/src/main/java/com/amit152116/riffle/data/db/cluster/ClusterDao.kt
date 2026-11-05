package com.amit152116.riffle.data.db.cluster

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ClusterDao {
    @Insert
    suspend fun insertClusterRun(clusterRun: ClusterRun): Long

    @Insert
    suspend fun insertClusterCentroid(clusterCentroid: ClusterCentroid): Long

    @Insert
    suspend fun insertClusterCentroids(centroids: List<ClusterCentroid>)

    @Insert
    suspend fun insertClusterAssignment(clusterAssignment: ClusterAssignment)

    @Insert
    suspend fun insertClusterAssignments(assignments: List<ClusterAssignment>)

    @Insert
    suspend fun insertContentSimilarity(row: ContentSimilarity)

    @Insert
    suspend fun insertContentSimilarities(rows: List<ContentSimilarity>)

    @Query("SELECT * FROM ClusterAssignment WHERE runId = :runId AND audioContentId = :audioContentId")
    suspend fun getClusterAssignment(runId: Long, audioContentId: Long): ClusterAssignment?

    @Query("SELECT * FROM ClusterAssignment WHERE runId = :runId")
    suspend fun getAssignments(runId: Long): List<ClusterAssignment>

    @Query("SELECT * FROM ClusterCentroid WHERE runId = :runId ORDER BY clusterId")
    suspend fun getCentroids(runId: Long): List<ClusterCentroid>

    @Query("SELECT * FROM ClusterRun ORDER BY id DESC LIMIT 1")
    suspend fun getLatestRun(): ClusterRun?

    @Query("SELECT COUNT(*) FROM ClusterRun")
    suspend fun countRuns(): Int

    @Query("SELECT * FROM ContentSimilarity WHERE audioContentId = :audioContentId AND neighborContentId = :neighborContentId")
    suspend fun getContentSimilarity(audioContentId: Long, neighborContentId: Long): ContentSimilarity?

    @Query("SELECT COUNT(*) FROM ContentSimilarity")
    suspend fun countContentSimilarities(): Int

    /** Neighbours best-first, skipping recordings that no longer have any present Track. */
    @Query(
        """
        SELECT cs.* FROM ContentSimilarity cs
        WHERE cs.audioContentId = :audioContentId
          AND EXISTS (SELECT 1 FROM Track t WHERE t.audioContentId = cs.neighborContentId AND t.present = 1)
        ORDER BY cs.combinedScore ASC, cs.neighborContentId ASC
        LIMIT :limit
        """
    )
    suspend fun getPresentNeighbors(audioContentId: Long, limit: Int): List<ContentSimilarity>

    @Query("DELETE FROM ContentSimilarity")
    suspend fun clearContentSimilarity()

    // FKs have no cascade: children must go before their run row would (run rows are kept anyway).
    @Query("DELETE FROM ClusterAssignment WHERE runId = :runId")
    suspend fun deleteAssignmentsForRun(runId: Long)

    @Query("DELETE FROM ClusterCentroid WHERE runId = :runId")
    suspend fun deleteCentroidsForRun(runId: Long)

    /**
     * One transaction: the old similarity graph and the superseded run's children are removed, the
     * new run (history row kept) and all its children are inserted. Any failure rolls the whole
     * thing back to the previous data.
     */
    @Transaction
    suspend fun replaceIntelligence(
        previousRunId: Long?,
        run: ClusterRun,
        centroids: List<ClusterCentroid>,
        assignments: List<ClusterAssignment>,
        similarities: List<ContentSimilarity>
    ): Long {
        clearContentSimilarity()
        if (previousRunId != null) {
            deleteAssignmentsForRun(previousRunId)
            deleteCentroidsForRun(previousRunId)
        }
        val runId = insertClusterRun(run)
        insertClusterCentroids(centroids.map { it.copy(runId = runId) })
        insertClusterAssignments(assignments.map { it.copy(runId = runId) })
        insertContentSimilarities(similarities)
        return runId
    }
}

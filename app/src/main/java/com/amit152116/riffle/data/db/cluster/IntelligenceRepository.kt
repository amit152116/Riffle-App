package com.amit152116.riffle.data.db.cluster

import com.amit152116.riffle.data.db.analysis.AnalysisDao
import com.amit152116.riffle.intelligence.IntelligenceEngine
import com.amit152116.riffle.intelligence.cluster.FeatureVectorizer
import com.amit152116.riffle.intelligence.cluster.PreviousCentroid
import com.amit152116.riffle.intelligence.cluster.PreviousRun
import com.amit152116.riffle.intelligence.model.EligibleRecording
import com.amit152116.riffle.intelligence.model.toLittleEndianBytes
import com.amit152116.riffle.intelligence.model.toLittleEndianDoubles

class IntelligenceResult(val rebuilt: Boolean, val nRecordings: Int, val nClusters: Int, val durationMs: Long)

class IntelligenceRepository(
    private val analysisDao: AnalysisDao,
    private val clusterDao: ClusterDao,
    val engine: IntelligenceEngine = IntelligenceEngine()
) {
    suspend fun loadEligibleRecordings(): List<EligibleRecording> =
        analysisDao.getEligibleFeatures(engine.eligibility.minDurationSeconds, engine.eligibility.silenceLufsFloor)
            .mapNotNull { it.toEligibleRecordingOrNull() }

    /**
     * No-op when the input hash (both configs + the eligible set) matches the latest run. Otherwise
     * recompute similarity and clusters together and replace them in one transaction. Cancellation is
     * never caught here: it propagates out of the suspend calls untouched.
     */
    suspend fun rebuildIfStale(): IntelligenceResult {
        val recordings = loadEligibleRecordings()
        if (recordings.isEmpty()) return IntelligenceResult(false, 0, 0, 0)
        val inputHash = engine.inputHash(recordings)
        val latest = clusterDao.getLatestRun()
        if (latest != null && latest.inputHash == inputHash) {
            return IntelligenceResult(false, recordings.size, latest.nClusters, 0)
        }

        val started = System.nanoTime()
        val previous = latest?.let { loadPrevious(it) }
        val output = engine.build(recordings, previous)
        val durationMs = (System.nanoTime() - started) / 1_000_000
        val c = output.clusters

        val run = ClusterRun(
            nClusters = c.k,
            nTracks = recordings.size,
            scalerParams = (c.scalerMean + c.scalerStd).toLittleEndianBytes(),
            createdAt = System.currentTimeMillis(),
            configHash = engine.configHash(),
            inputHash = inputHash,
            silhouette = c.silhouette,
            kScores = c.kScores.ifEmpty { null },
            durationMs = durationMs,
            nextClusterId = c.nextClusterId
        )
        val centroids = c.clusters.map {
            ClusterCentroid(
                runId = 0, clusterId = it.clusterId, label = it.displayName,
                centroid = it.rawCentroid.toLittleEndianBytes(), nTracks = it.size,
                tempoBand = it.label.tempo, energyBand = it.label.energy, mode = it.label.mode,
                timbre = it.label.timbre, matchDistance = it.matchDistance
            )
        }
        val assignments = c.assignments.map {
            ClusterAssignment(runId = 0, audioContentId = it.audioContentId, clusterId = it.clusterId, distanceToCentroid = it.distance)
        }
        val similarities = output.similarity.neighbors.flatMap { (id, list) ->
            list.map { n ->
                ContentSimilarity(
                    audioContentId = id, neighborContentId = n.audioContentId,
                    mfccDistance = n.breakdown.mfccDistance, bpmDistance = n.breakdown.bpmDistance,
                    keyDistance = n.breakdown.keyDistance, energyDistance = n.breakdown.energyDistance,
                    combinedScore = n.breakdown.combined
                )
            }
        }
        clusterDao.replaceIntelligence(latest?.id, run, centroids, assignments, similarities)
        return IntelligenceResult(true, recordings.size, c.k, durationMs)
    }

    private suspend fun loadPrevious(run: ClusterRun): PreviousRun {
        val centroids = clusterDao.getCentroids(run.id).mapNotNull { c ->
            val bytes = c.centroid ?: return@mapNotNull null
            if (bytes.size != FeatureVectorizer.DIM * 8) return@mapNotNull null
            PreviousCentroid(c.clusterId, bytes.toLittleEndianDoubles())
        }
        return PreviousRun(centroids, run.nextClusterId)
    }
}

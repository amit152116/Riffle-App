package com.amit152116.riffle.data.db.query

import com.amit152116.riffle.data.db.cluster.ClusterDao
import com.amit152116.riffle.data.db.cluster.IntelligenceRepository
import com.amit152116.riffle.data.db.dedup.DedupRepository
import com.amit152116.riffle.intelligence.model.ShuffleConfig
import com.amit152116.riffle.intelligence.shuffle.NeighborSource
import com.amit152116.riffle.intelligence.shuffle.PoolEntry
import com.amit152116.riffle.intelligence.shuffle.ShuffleStep
import com.amit152116.riffle.intelligence.shuffle.SmartShuffler
import com.amit152116.riffle.intelligence.similarity.ScoreBreakdown
import com.amit152116.riffle.intelligence.similarity.ScoredNeighbor
import com.amit152116.riffle.intelligence.similarity.SimilarityCalibration
import kotlin.random.Random

class ShuffleRequest(
    val filter: TrackFilter = TrackFilter(),
    val seedAudioContentId: Long? = null,
    val length: Int = 20,
    val randomSeed: Long? = null
)

/**
 * Room glue for SmartShuffler: the candidate pool comes from the same TrackFilter query Browse uses,
 * neighbours from the stored similarity graph (skipping recordings with no present Track), and
 * exclusions from approved/applied duplicate groups. Steps carry Room Track ids; the UI resolves
 * them to MediaStore ids through Track.mediaStoreId.
 */
class ShuffleRepository(
    private val trackFilterRepository: TrackFilterRepository,
    private val intelligenceRepository: IntelligenceRepository,
    private val clusterDao: ClusterDao,
    private val dedupRepository: DedupRepository,
    private val shuffleConfig: ShuffleConfig = ShuffleConfig()
) {
    suspend fun smartShuffle(request: ShuffleRequest): List<ShuffleStep> {
        val recordings = intelligenceRepository.loadEligibleRecordings()
        if (recordings.isEmpty()) return emptyList()
        val byId = recordings.associateBy { it.audioContentId }
        val similarityConfig = intelligenceRepository.engine.similarityConfig
        // Same calibration the similarity index was built with (fit is deterministic over the eligible set).
        val calibration = SimilarityCalibration.fit(recordings, similarityConfig)

        val filtered = trackFilterRepository.query(request.filter.copy(limit = null, offset = 0))
        val pool = filtered.mapNotNull { ft ->
            ft.track.audioContentId?.let { id ->
                byId[id]?.let { PoolEntry(it, ft.track.id, ft.track.tagArtist) }
            }
        }

        val neighbors = object : NeighborSource {
            override suspend fun neighbors(audioContentId: Long): List<ScoredNeighbor> =
                clusterDao.getPresentNeighbors(audioContentId, similarityConfig.topK).map {
                    ScoredNeighbor(
                        it.neighborContentId,
                        ScoreBreakdown(it.mfccDistance, it.bpmDistance, it.keyDistance, it.energyDistance, it.combinedScore)
                    )
                }
        }
        val rng = request.randomSeed?.let { Random(it) } ?: Random.Default
        return SmartShuffler(similarityConfig, shuffleConfig).shuffle(
            pool, calibration, neighbors, dedupRepository.getDuplicateExclusions(),
            request.seedAudioContentId, request.length, rng
        )
    }
}

package com.amit152116.riffle.intelligence.similarity

import com.amit152116.riffle.intelligence.model.EligibleRecording
import com.amit152116.riffle.intelligence.model.SimilarityConfig

class ScoredNeighbor(val audioContentId: Long, val breakdown: ScoreBreakdown)

/** Directed top-K graph: A may list B without B listing A, though the pair score is symmetric. */
class SimilarityIndex(val calibration: SimilarityCalibration, val neighbors: Map<Long, List<ScoredNeighbor>>) {
    val bpmSpread: Double get() = calibration.bpmSpread
}

class SimilarityIndexBuilder(
    private val config: SimilarityConfig,
    private val kernel: SimilarityKernel = KotlinSimilarityKernel()
) {
    fun build(recordings: List<EligibleRecording>): SimilarityIndex {
        val sorted = recordings.sortedBy { it.audioContentId }
        val calibration = SimilarityCalibration.fit(sorted, config)
        if (sorted.isEmpty()) return SimilarityIndex(calibration, emptyMap())
        val table = RecordingTable(sorted, calibration)
        val topK = kernel.topK(table, config, calibration)
        val scorer = SimilarityScorer(table, config, calibration)
        val neighbors = LinkedHashMap<Long, List<ScoredNeighbor>>(sorted.size * 2)
        for (i in sorted.indices) {
            neighbors[sorted[i].audioContentId] = topK[i].map { j ->
                ScoredNeighbor(sorted[j].audioContentId, scorer.breakdown(i, j))
            }
        }
        return SimilarityIndex(calibration, neighbors)
    }
}

package com.amit152116.riffle.intelligence.similarity

import com.amit152116.riffle.intelligence.model.SimilarityConfig

/**
 * Batch-level seam (one call for the whole matrix, never per pair: a per-pair JNI hop would cost
 * more than it saves) so a NEON implementation can replace the Kotlin one if a device benchmark
 * ever demands it.
 */
interface SimilarityKernel {
    /** For each recording, up to `config.topK` neighbour table indices, best first, excluding itself. */
    fun topK(table: RecordingTable, config: SimilarityConfig, calibration: SimilarityCalibration): Array<IntArray>
}

class KotlinSimilarityKernel : SimilarityKernel {
    override fun topK(table: RecordingTable, config: SimilarityConfig, calibration: SimilarityCalibration): Array<IntArray> {
        val scorer = SimilarityScorer(table, config, calibration)
        val ids = LongArray(table.size) { table.recordings[it].audioContentId }
        return Array(table.size) { i ->
            val top = BoundedTopK(config.topK, ids)
            for (j in 0 until table.size) {
                if (j == i) continue
                top.offer(scorer.combined(i, j), j)
            }
            top.sortedIndices()
        }
    }
}

/** Fixed-capacity buffer kept sorted ascending by (score, audioContentId). */
internal class BoundedTopK(private val capacity: Int, private val ids: LongArray) {
    private val scores = DoubleArray(capacity)
    private val indices = IntArray(capacity)
    private var count = 0

    private fun less(scoreA: Double, idxA: Int, scoreB: Double, idxB: Int): Boolean =
        scoreA < scoreB || (scoreA == scoreB && ids[idxA] < ids[idxB])

    fun offer(score: Double, index: Int) {
        if (capacity == 0) return
        if (count == capacity && !less(score, index, scores[count - 1], indices[count - 1])) return
        var pos = if (count < capacity) count else capacity - 1
        while (pos > 0 && less(score, index, scores[pos - 1], indices[pos - 1])) {
            scores[pos] = scores[pos - 1]
            indices[pos] = indices[pos - 1]
            pos--
        }
        scores[pos] = score
        indices[pos] = index
        if (count < capacity) count++
    }

    fun sortedIndices(): IntArray = indices.copyOf(count)
}

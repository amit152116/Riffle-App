package com.amit152116.riffle.intelligence.shuffle

import com.amit152116.riffle.intelligence.model.ShuffleConfig
import com.amit152116.riffle.intelligence.model.SimilarityConfig
import com.amit152116.riffle.intelligence.similarity.RecordingTable
import com.amit152116.riffle.intelligence.similarity.ScoredNeighbor
import com.amit152116.riffle.intelligence.similarity.SimilarityCalibration
import com.amit152116.riffle.intelligence.similarity.SimilarityScorer
import kotlin.math.exp
import kotlin.random.Random

interface NeighborSource {
    /** Stored neighbours of a recording, best first. May be empty. */
    suspend fun neighbors(audioContentId: Long): List<ScoredNeighbor>
}

/**
 * The requested seed can't start a shuffle: it is not analysed yet, is blacklisted, or is excluded
 * by the active filters. A distinct type (still an IllegalArgumentException) so callers can tell it
 * from a programming error and show a sensible message.
 */
class SeedNotInPoolException(val audioContentId: Long) : IllegalArgumentException(
    "Seed recording $audioContentId is not in the candidate pool " +
        "(not analysed yet, blacklisted, or excluded by the active filters)"
)

class ShuffleStep(
    val trackId: Long,
    val audioContentId: Long,
    val score: Double,
    val breakdown: List<TermScore>
)

object Selection {
    /** Softmax-style weights for ascending scores (lower is better); numerically stable. */
    fun weights(scores: DoubleArray, temperature: Double): DoubleArray {
        require(temperature > 0.0) { "temperature must be positive" }
        val min = scores.min()
        return DoubleArray(scores.size) { exp(-(scores[it] - min) / temperature) }
    }

    /** Index into an ascending-sorted window. T <= 0 is a pure argmin (never divides by zero). */
    fun pick(scores: DoubleArray, temperature: Double, rng: Random): Int {
        if (temperature <= 0.0 || scores.size == 1) return 0
        val w = weights(scores, temperature)
        var r = rng.nextDouble() * w.sum()
        for (i in w.indices) {
            r -= w[i]
            if (r <= 0.0) return i
        }
        return w.lastIndex
    }
}

class SmartShuffler(
    private val similarityConfig: SimilarityConfig = SimilarityConfig(),
    private val config: ShuffleConfig = ShuffleConfig()
) {
    private val terms: List<ScoreTerm> = listOf(SimilarityTerm, ArtistRepeatTerm, BpmJumpTerm, KeyHarmonyTerm)

    private class Scored(val entry: PoolEntry, val total: Double, val parts: List<TermScore>)

    suspend fun shuffle(
        pool: List<PoolEntry>,
        calibration: SimilarityCalibration,
        neighbors: NeighborSource,
        exclusions: Map<Long, Set<Long>>,
        seedAudioContentId: Long?,
        length: Int,
        rng: Random
    ): List<ShuffleStep> {
        if (pool.isEmpty() || length <= 0) return emptyList()
        val sorted = pool.sortedBy { it.audioContentId }
        val byId = sorted.associateBy { it.audioContentId }
        val start = if (seedAudioContentId != null) {
            byId[seedAudioContentId] ?: throw SeedNotInPoolException(seedAudioContentId)
        } else sorted[rng.nextInt(sorted.size)]

        val indexById = HashMap<Long, Int>(sorted.size * 2)
        sorted.forEachIndexed { i, e -> indexById[e.audioContentId] = i }
        val scorer by lazy { SimilarityScorer(RecordingTable(sorted.map { it.recording }, calibration), similarityConfig, calibration) }

        val used = HashSet<Long>()
        val picked = ArrayList<PoolEntry>()
        val steps = ArrayList<ShuffleStep>()
        fun take(entry: PoolEntry) {
            used.add(entry.audioContentId)
            exclusions[entry.audioContentId]?.let { used.addAll(it) }
            picked.add(entry)
        }

        take(start)
        steps.add(ShuffleStep(start.trackId, start.audioContentId, 0.0, emptyList()))

        while (steps.size < length) {
            val current = picked.last()
            val ctx = StepContext(current, picked.takeLast(config.artistWindow).map { it.artist }, calibration.bpmSpread, config)

            var candidates: List<Pair<PoolEntry, Double>> = neighbors.neighbors(current.audioContentId)
                .mapNotNull { n ->
                    byId[n.audioContentId]?.takeIf { it.audioContentId !in used }?.let { it to n.breakdown.combined }
                }
            if (candidates.isEmpty()) {
                // Stored top-K is library-wide, so under a genre/BPM filter it often has nothing left in
                // the pool. Score the current recording against everything still available instead.
                val ci = indexById.getValue(current.audioContentId)
                candidates = sorted.filter { it.audioContentId !in used }
                    .map { it to scorer.combined(ci, indexById.getValue(it.audioContentId)) }
            }
            if (candidates.isEmpty()) break

            val scored = candidates.map { (entry, sim) ->
                val parts = terms.map { it.evaluate(ctx, entry, sim) }
                Scored(entry, parts.sumOf { it.value }, parts)
            }.sortedWith(compareBy<Scored> { it.total }.thenBy { it.entry.audioContentId })

            val window = scored.take(config.window)
            val choice = window[Selection.pick(DoubleArray(window.size) { window[it].total }, config.temperature, rng)]
            take(choice.entry)
            steps.add(ShuffleStep(choice.entry.trackId, choice.entry.audioContentId, choice.total, choice.parts))
        }
        return steps
    }
}

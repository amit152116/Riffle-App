package com.amit152116.riffle.intelligence.cluster

import com.amit152116.riffle.intelligence.math.Stats
import com.amit152116.riffle.intelligence.model.ClusterConfig
import com.amit152116.riffle.intelligence.model.ClusterMode
import com.amit152116.riffle.intelligence.model.EligibleRecording
import com.amit152116.riffle.intelligence.model.EnergyBand
import com.amit152116.riffle.intelligence.model.TempoBand
import com.amit152116.riffle.intelligence.model.TimbreBand

class ClusterLabel(
    val tempo: TempoBand?,
    val energy: EnergyBand,
    val mode: ClusterMode,
    val timbre: TimbreBand?,
    val baseName: String
)

/** Library-relative labels: cut-offs come from this library's own percentiles, not fixed thresholds. */
class ClusterLabeler(private val config: ClusterConfig, library: List<EligibleRecording>) {
    private val bpmCuts = cuts(
        library.filter { it.hasValidBpm }.map { it.bpm!! },
        config.bpmTercileFallbackLow, config.bpmTercileFallbackHigh
    )
    private val energyCuts = cuts(
        library.map { it.energy }, config.energyTercileFallbackLow, config.energyTercileFallbackHigh
    )
    private val libraryCentroidMedian: Double? =
        library.mapNotNull { it.spectralCentroid?.takeIf { v -> v.isFinite() } }
            .takeIf { it.isNotEmpty() }
            ?.let { Stats.upperMedian(it.toDoubleArray()) }

    private fun cuts(values: List<Double>, fallbackLow: Double, fallbackHigh: Double): Pair<Double, Double> {
        if (values.size < config.tercileMinSamples) return fallbackLow to fallbackHigh
        val sorted = values.sorted().toDoubleArray()
        return Stats.percentileSorted(sorted, config.tercileLowPercentile) to
            Stats.percentileSorted(sorted, config.tercileHighPercentile)
    }

    private fun <T> pick(value: Double, cuts: Pair<Double, Double>, low: T, mid: T, high: T): T =
        if (value < cuts.first) low else if (value > cuts.second) high else mid

    fun label(members: List<EligibleRecording>): ClusterLabel {
        require(members.isNotEmpty()) { "cannot label an empty cluster" }

        val bpms = members.filter { it.hasValidBpm }.map { it.bpm!! }
        val tempo = if (bpms.isEmpty()) null
        else pick(Stats.upperMedian(bpms.toDoubleArray()), bpmCuts, TempoBand.SLOW, TempoBand.MID, TempoBand.UPBEAT)

        val energy = pick(
            Stats.upperMedian(members.map { it.energy }.toDoubleArray()), energyCuts,
            EnergyBand.CALM, EnergyBand.MODERATE, EnergyBand.ENERGETIC
        )

        val scales = members.mapNotNull { it.scale }
        val mode = if (scales.isEmpty()) ClusterMode.UNKNOWN else {
            val share = scales.count { it == "major" }.toDouble() / scales.size
            when {
                share >= config.majorShareForMajor -> ClusterMode.MAJOR
                share <= config.majorShareForMinor -> ClusterMode.MINOR
                else -> ClusterMode.MIXED
            }
        }

        val centroids = members.mapNotNull { it.spectralCentroid?.takeIf { v -> v.isFinite() } }
        val median = libraryCentroidMedian
        val timbre = if (centroids.isEmpty() || median == null) null
        else if (Stats.upperMedian(centroids.toDoubleArray()) > median) TimbreBand.BRIGHT else TimbreBand.WARM

        val tokens = buildList {
            when (tempo) {
                TempoBand.SLOW -> add("Slow"); TempoBand.MID -> add("Mid-tempo"); TempoBand.UPBEAT -> add("Upbeat"); null -> {}
            }
            add(
                when (energy) {
                    EnergyBand.CALM -> "Calm"; EnergyBand.MODERATE -> "Moderate"; EnergyBand.ENERGETIC -> "Energetic"
                }
            )
            when (mode) {
                ClusterMode.MAJOR -> add("Major"); ClusterMode.MINOR -> add("Minor"); ClusterMode.MIXED, ClusterMode.UNKNOWN -> {}
            }
            when (timbre) {
                TimbreBand.BRIGHT -> add("Bright"); TimbreBand.WARM -> add("Warm"); null -> {}
            }
        }
        return ClusterLabel(tempo, energy, mode, timbre, tokens.joinToString(" "))
    }

    companion object {
        /**
         * Cosmetic uniqueness only: the first cluster (lowest id) keeps the bare name, later ones get
         * " · 2", " · 3". clusterId - never this suffix - is the identity, and suffixes may shift
         * when the set of clusters sharing a name changes.
         */
        fun uniqueNames(baseNamesByClusterId: Map<Int, String>): Map<Int, String> {
            val seen = HashMap<String, Int>()
            val out = LinkedHashMap<Int, String>()
            for (id in baseNamesByClusterId.keys.sorted()) {
                val base = baseNamesByClusterId.getValue(id)
                val n = (seen[base] ?: 0) + 1
                seen[base] = n
                out[id] = if (n == 1) base else "$base · $n"
            }
            return out
        }
    }
}

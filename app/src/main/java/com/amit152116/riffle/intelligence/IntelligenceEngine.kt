package com.amit152116.riffle.intelligence

import com.amit152116.riffle.intelligence.cluster.ClusterBuilder
import com.amit152116.riffle.intelligence.cluster.ClusterResult
import com.amit152116.riffle.intelligence.cluster.PreviousRun
import com.amit152116.riffle.intelligence.model.ClusterConfig
import com.amit152116.riffle.intelligence.model.EligibilityConfig
import com.amit152116.riffle.intelligence.model.EligibleRecording
import com.amit152116.riffle.intelligence.model.InputHash
import com.amit152116.riffle.intelligence.model.SimilarityConfig
import com.amit152116.riffle.intelligence.model.sha256Hex
import com.amit152116.riffle.intelligence.similarity.KotlinSimilarityKernel
import com.amit152116.riffle.intelligence.similarity.SimilarityIndex
import com.amit152116.riffle.intelligence.similarity.SimilarityIndexBuilder
import com.amit152116.riffle.intelligence.similarity.SimilarityKernel

class EngineOutput(val similarity: SimilarityIndex, val clusters: ClusterResult)

class IntelligenceEngine(
    val similarityConfig: SimilarityConfig = SimilarityConfig(),
    val clusterConfig: ClusterConfig = ClusterConfig(),
    val eligibility: EligibilityConfig = EligibilityConfig(),
    private val kernel: SimilarityKernel = KotlinSimilarityKernel()
) {
    fun configHash(): String =
        sha256Hex(similarityConfig.canonical() + "\n" + clusterConfig.canonical() + "\n" + eligibility.canonical())

    fun inputHash(recordings: List<EligibleRecording>): String =
        InputHash.compute(similarityConfig, clusterConfig, recordings, eligibility)

    fun build(recordings: List<EligibleRecording>, previous: PreviousRun?): EngineOutput {
        require(recordings.isNotEmpty()) { "cannot build intelligence from zero recordings" }
        return EngineOutput(
            similarity = SimilarityIndexBuilder(similarityConfig, kernel).build(recordings),
            clusters = ClusterBuilder(clusterConfig).build(recordings, previous)
        )
    }
}

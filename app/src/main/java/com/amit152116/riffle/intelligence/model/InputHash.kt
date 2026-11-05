package com.amit152116.riffle.intelligence.model

object InputHash {
    /**
     * SHA-256 over both configs plus the sorted (audioContentId, extractorVersion, features
     * configHash) tuples of every eligible recording. Eligibility already requires a present
     * Track, so a recording that loses its last present Track drops out of the list and the
     * hash changes - no separate presence flag is needed.
     */
    fun compute(
        similarity: SimilarityConfig,
        cluster: ClusterConfig,
        recordings: List<EligibleRecording>,
        eligibility: EligibilityConfig = EligibilityConfig()
    ): String {
        val sb = StringBuilder()
        sb.append(similarity.canonical()).append('\n').append(cluster.canonical()).append('\n')
            .append(eligibility.canonical()).append('\n')
        for (r in recordings.sortedBy { it.audioContentId }) {
            sb.append(r.audioContentId).append('|')
                .append(r.extractorVersion ?: "").append('|')
                .append(r.featuresConfigHash ?: "").append('\n')
        }
        return sha256Hex(sb.toString())
    }
}

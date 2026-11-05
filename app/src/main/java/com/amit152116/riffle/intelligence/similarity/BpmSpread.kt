package com.amit152116.riffle.intelligence.similarity

import com.amit152116.riffle.intelligence.math.Stats
import com.amit152116.riffle.intelligence.model.EligibleRecording
import com.amit152116.riffle.intelligence.model.SimilarityConfig

object BpmSpread {
    /**
     * The library's own typical BPM variation: IQR of valid (> 0) BPMs, floored so a library with
     * almost no tempo variation doesn't make tiny differences read as maximal. Fallback when there
     * are too few samples for a stable IQR.
     */
    fun compute(recordings: List<EligibleRecording>, config: SimilarityConfig): Double {
        val bpms = recordings.filter { it.hasValidBpm }.map { it.bpm!! }.sorted().toDoubleArray()
        if (bpms.size < config.bpmSpreadMinSamples) return config.bpmSpreadFallback
        val iqr = Stats.percentileSorted(bpms, 75.0) - Stats.percentileSorted(bpms, 25.0)
        return maxOf(iqr, config.bpmSpreadFloor)
    }
}

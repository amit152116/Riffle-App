package com.amit152116.riffle.analysis

import com.amit152116.riffle.dsp.AudioFeatures
import kotlin.math.log10
import kotlin.math.pow

/**
 * Combines the features of independently analysed windows into one [AudioFeatures] for the track.
 * Every rule ignores non-finite values so a silent or failed window cannot poison the result.
 */
object FeatureAggregator {
    private const val LUFS_FLOOR = -70f
    private const val MIN_WEIGHT = 0.001

    fun combine(windows: List<AudioFeatures>, totalDurationSec: Float): AudioFeatures {
        require(windows.isNotEmpty()) { "no windows to combine" }
        if (windows.size == 1) {
            return windows[0].copy(duration = totalDurationSec, fingerprint = IntArray(0), fingerprintAlgorithm = -1)
        }

        val (bpm, bpmConfidence) = bpmOf(windows)
        val (keyName, scale, keyStrength) = keyOf(windows)
        val mfccSize = windows.minOf { it.mfccMean.size }
        return AudioFeatures(
            bpm = bpm,
            bpmConfidence = bpmConfidence,
            keyName = keyName,
            scale = scale,
            keyStrength = keyStrength,
            loudnessLufs = powerMeanLufs(windows),
            danceability = mean(windows) { it.danceability },
            energy = mean(windows) { it.energy },
            spectralCentroid = mean(windows) { it.spectralCentroid },
            onsetRate = mean(windows) { it.onsetRate },
            dynamicComplexity = mean(windows) { it.dynamicComplexity },
            dissonance = mean(windows) { it.dissonance },
            zcr = mean(windows) { it.zcr },
            mfccMean = FloatArray(mfccSize) { i -> mean(windows) { it.mfccMean[i] } },
            duration = totalDurationSec,
            fingerprint = IntArray(0),
            fingerprintAlgorithm = -1,
            configHash = windows.first().configHash
        )
    }

    private fun mean(windows: List<AudioFeatures>, f: (AudioFeatures) -> Float): Float {
        val v = windows.map(f).filter { it.isFinite() }
        return if (v.isEmpty()) 0f else (v.sumOf { it.toDouble() } / v.size).toFloat()
    }

    /** Confidence-weighted median of the valid (> 0) BPMs; confidence = their mean confidence. */
    private fun bpmOf(windows: List<AudioFeatures>): Pair<Float, Float> {
        val valid = windows.filter { it.bpm.isFinite() && it.bpm > 0f }.sortedBy { it.bpm }
        if (valid.isEmpty()) return 0f to 0f
        val weights = valid.map { it.bpmConfidence.takeIf { c -> c.isFinite() }?.toDouble()?.coerceAtLeast(MIN_WEIGHT) ?: MIN_WEIGHT }
        val half = weights.sum() / 2
        var cumulative = 0.0
        var chosen = valid.last().bpm
        for (i in valid.indices) {
            cumulative += weights[i]
            if (cumulative >= half) {
                chosen = valid[i].bpm
                break
            }
        }
        val confidence = valid.map { it.bpmConfidence }.filter { it.isFinite() }
        return chosen to (if (confidence.isEmpty()) 0f else (confidence.sumOf { it.toDouble() } / confidence.size).toFloat())
    }

    /** Sum of key strength per (key, scale); the highest total wins, ties by key then scale name. */
    private fun keyOf(windows: List<AudioFeatures>): Triple<String, String, Float> {
        val totals = HashMap<Pair<String, String>, Pair<Double, Int>>()
        for (w in windows) {
            if (w.keyName.isBlank()) continue
            val strength = w.keyStrength.takeIf { it.isFinite() }?.toDouble() ?: 0.0
            val k = w.keyName to w.scale
            val (sum, n) = totals[k] ?: (0.0 to 0)
            totals[k] = (sum + strength) to (n + 1)
        }
        if (totals.isEmpty()) return Triple("", "", 0f)
        val best = totals.entries.sortedWith(
            compareByDescending<Map.Entry<Pair<String, String>, Pair<Double, Int>>> { it.value.first }
                .thenBy { it.key.first }.thenBy { it.key.second }
        ).first()
        return Triple(best.key.first, best.key.second, (best.value.first / best.value.second).toFloat())
    }

    /** Integrated loudness of concatenated audio is the power mean of the parts: 10*log10(mean(10^(L/10))). */
    private fun powerMeanLufs(windows: List<AudioFeatures>): Float {
        val v = windows.map { it.loudnessLufs }.filter { it.isFinite() }
        if (v.isEmpty()) return LUFS_FLOOR
        val meanPower = v.sumOf { 10.0.pow(it / 10.0) } / v.size
        return (10 * log10(meanPower)).toFloat()
    }
}

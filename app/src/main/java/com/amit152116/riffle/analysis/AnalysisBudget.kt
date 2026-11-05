package com.amit152116.riffle.analysis

/** Monotonic time source (never the wall clock, so a clock change cannot alter behaviour). */
interface MonotonicClock {
    fun nowNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

/**
 * Cost model and chunk budget. Defaults are conservative placeholders; Task 9's device benchmark
 * sets the two factors. Nothing here is ever persisted or hashed (spec, invariant 2).
 */
data class AnalysisBudgetConfig(
    val chunkBudgetSeconds: Double = 180.0,
    val singleRunBudgetSeconds: Double = 150.0,
    val decodeSecondsPerAudioSecond: Double = 0.0436, // measured 0.0349 s/s (decode+fingerprint, CPH2661) x1.25
    val featureSecondsPerAnalysedSecond: Double = 0.0735, // measured 0.0588 s/analysed-s x1.25
    val windowedAnalysedSeconds: Double = 270.0,
    val minEstimateSeconds: Double = 1.0,
    val maxEstimateSeconds: Double = 3600.0,
    val fallbackDurationSeconds: Double = 300.0
)

/**
 * Decides only WHEN a chunk stops. It can never change what is computed for a track: the plan
 * depends on the duration alone, and every input here is sanitised so the estimator cannot throw
 * or return a non-finite value.
 */
class AnalysisBudget(
    config: AnalysisBudgetConfig = AnalysisBudgetConfig(),
    private val planConfig: AnalysisPlanConfig = AnalysisPlanConfig()
) {
    private val defaults = AnalysisBudgetConfig()
    private val c = sanitise(config)

    val chunkBudgetSeconds: Double get() = c.chunkBudgetSeconds

    fun estimateSeconds(durationSec: Double?): Double {
        val d = durationSec?.takeIf { it.isFinite() && it > 0.0 } ?: c.fallbackDurationSeconds
        val analysed = when (AnalysisPlan.forDuration(d, planConfig)) {
            is AnalysisPlan.Full -> d
            is AnalysisPlan.Windowed -> c.windowedAnalysedSeconds.coerceAtMost(d)
        }
        val raw = c.decodeSecondsPerAudioSecond * d + c.featureSecondsPerAnalysedSecond * analysed
        val finite = if (raw.isFinite()) raw else c.maxEstimateSeconds
        return finite.coerceIn(c.minEstimateSeconds, c.maxEstimateSeconds)
    }

    /** The first track of a chunk always starts (guaranteed progress); later ones only while they fit. */
    fun shouldStartNext(elapsedSeconds: Double, nextEstimateSeconds: Double, startedInChunk: Int): Boolean {
        if (startedInChunk <= 0) return true
        if (!elapsedSeconds.isFinite() || elapsedSeconds < 0.0) return false
        val estimate = nextEstimateSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: c.maxEstimateSeconds
        return elapsedSeconds + estimate <= c.chunkBudgetSeconds
    }

    fun exceedsSingleRun(estimateSeconds: Double): Boolean =
        estimateSeconds.isFinite() && estimateSeconds > c.singleRunBudgetSeconds

    private fun sanitise(x: AnalysisBudgetConfig): AnalysisBudgetConfig {
        fun pos(v: Double, d: Double) = if (v.isFinite() && v > 0.0) v else d
        val min = pos(x.minEstimateSeconds, defaults.minEstimateSeconds)
        val max = pos(x.maxEstimateSeconds, defaults.maxEstimateSeconds).coerceAtLeast(min)
        return AnalysisBudgetConfig(
            chunkBudgetSeconds = pos(x.chunkBudgetSeconds, defaults.chunkBudgetSeconds),
            singleRunBudgetSeconds = pos(x.singleRunBudgetSeconds, defaults.singleRunBudgetSeconds),
            decodeSecondsPerAudioSecond = pos(x.decodeSecondsPerAudioSecond, defaults.decodeSecondsPerAudioSecond),
            featureSecondsPerAnalysedSecond = pos(x.featureSecondsPerAnalysedSecond, defaults.featureSecondsPerAnalysedSecond),
            windowedAnalysedSeconds = pos(x.windowedAnalysedSeconds, defaults.windowedAnalysedSeconds),
            minEstimateSeconds = min,
            maxEstimateSeconds = max,
            fallbackDurationSeconds = pos(x.fallbackDurationSeconds, defaults.fallbackDurationSeconds)
        )
    }
}

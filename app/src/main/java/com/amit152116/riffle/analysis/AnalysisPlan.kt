package com.amit152116.riffle.analysis

data class AnalysisPlanConfig(
    val fullAnalysisMaxSeconds: Double = 360.0,
    val windowCount: Int = 6,
    val windowSeconds: Double = 45.0
)

class AnalysisWindow(val startSec: Double, val endSec: Double) {
    val lengthSec: Double get() = endSec - startSec
    override fun toString() = "AnalysisWindow($startSec..$endSec)"
}

/**
 * How one track is analysed. A pure function of the duration and the fixed parameters - never of
 * cost estimates, budgets or timing (spec, invariant 2) - so the same file always yields the
 * same result however the work was chunked.
 */
sealed interface AnalysisPlan {
    /** The whole track through the existing path (unchanged results). */
    data object Full : AnalysisPlan

    /** Features from these windows (combined afterwards); the fingerprint still covers the whole track. */
    data class Windowed(val windows: List<AnalysisWindow>) : AnalysisPlan

    companion object {
        private const val DEFAULT_FULL_MAX = 360.0
        private const val DEFAULT_WINDOW_SECONDS = 45.0

        fun forDuration(durationSec: Double?, config: AnalysisPlanConfig = AnalysisPlanConfig()): AnalysisPlan {
            val d = durationSec
            if (d == null || !d.isFinite() || d <= 0.0) return Full
            val fullMax = config.fullAnalysisMaxSeconds.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_FULL_MAX
            if (d <= fullMax) return Full
            val count = config.windowCount.coerceAtLeast(1)
            val len = (config.windowSeconds.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_WINDOW_SECONDS).coerceAtMost(d)
            val windows = (0 until count).map { i ->
                val centre = (i + 0.5) / count * d
                val start = (centre - len / 2).coerceIn(0.0, d - len)
                AnalysisWindow(start, start + len)
            }
            return Windowed(windows)
        }
    }
}

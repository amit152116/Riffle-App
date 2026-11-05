package com.amit152116.riffle.analysis

import kotlin.math.ceil
import kotlin.math.roundToInt

data class SegmenterConfig(val segmentSeconds: Double = 300.0, val warmupSeconds: Double = 12.0)

data class SegmentRange(val startSec: Double, val endSec: Double)

/**
 * Segment and warm-up boundaries for a resumable fingerprint, all expressed in whole Chromaprint hops
 * (5460 samples at 44.1 kHz) so that item i of a later segment lines up with item i of a continuous run.
 * Each later segment is fed [warmupHops] early to let the filters settle; those items are dropped when stitching.
 */
class FingerprintSegmenter(config: SegmenterConfig = SegmenterConfig()) {
    private val hopSec = HOP_SAMPLES_44K / 44_100.0
    val warmupHops: Int
    val segmentHops: Int

    init {
        val warm = config.warmupSeconds.takeIf { it.isFinite() && it > 0.0 } ?: 12.0
        val seg = config.segmentSeconds.takeIf { it.isFinite() && it > 0.0 } ?: 300.0
        warmupHops = ceil(warm / hopSec).toInt()
        segmentHops = maxOf((seg / hopSec).roundToInt(), warmupHops * 4)
    }

    fun segmentCount(durationSec: Double): Int {
        if (!durationSec.isFinite() || durationSec <= 0.0) return 1
        return maxOf(1, ceil(durationSec / (segmentHops * hopSec) - 1e-9).toInt())
    }

    fun feedRange(index: Int): SegmentRange {
        val start = index * segmentHops * hopSec
        val end = (index + 1) * segmentHops * hopSec
        return SegmentRange(if (index == 0) 0.0 else start - warmupHops * hopSec, end)
    }

    fun dropItems(index: Int): Int = if (index == 0) 0 else warmupHops

    fun stitch(parts: List<IntArray>): IntArray {
        val kept = parts.mapIndexed { i, p -> if (p.size <= dropItems(i)) IntArray(0) else p.copyOfRange(dropItems(i), p.size) }
        val out = IntArray(kept.sumOf { it.size })
        var pos = 0
        kept.forEach { System.arraycopy(it, 0, out, pos, it.size); pos += it.size }
        return out
    }

    companion object {
        const val HOP_SAMPLES_44K = 5460
    }
}

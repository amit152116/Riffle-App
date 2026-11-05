package com.amit152116.riffle.analysis

/**
 * How many recordings to analyse at once. Measured on the target phone: concurrent Essentia extractions are
 * bit-identical to serial ones and scale ~2.7x at 3 threads and ~3.6x at 4. Foreground work may use several
 * cores; background work stays gentle (the OS throttles background processes anyway).
 */
object AnalysisParallelism {
    private const val MAX_SLOTS = 4
    private const val HEAP_MB_PER_SLOT = 96 // a 4-min 48 kHz track holds ~46 MB of PCM, briefly twice that when trimmed
    private const val HEAVY_ABOVE_SECONDS = 240.0

    /**
     * @param maxHeapMb the app's Java heap limit (`Runtime.maxMemory()` in MB). Decoded PCM lives on the Java heap, so
     * a small heap caps the slots; an out-of-memory error would otherwise count as a failed attempt for a good track.
     */
    fun choose(cores: Int, charging: Boolean, foreground: Boolean, maxHeapMb: Int = Int.MAX_VALUE): Int {
        if (cores < 1) return 1
        val wanted = when {
            foreground -> MAX_SLOTS
            charging -> 2
            else -> 1
        }
        val heapSlots = (maxHeapMb / HEAP_MB_PER_SLOT).coerceAtLeast(1)
        return wanted.coerceAtMost(maxOf(1, cores / 2)).coerceAtMost(heapSlots).coerceAtLeast(1)
    }

    /** Long tracks hold the biggest PCM buffers: at most two at once, and only one on a small heap. */
    fun maxConcurrentHeavy(maxHeapMb: Int): Int = if (maxHeapMb >= 384) 2 else 1

    /** Unknown or long durations are "heavy": their decode buffers dominate memory. */
    fun isHeavy(durationSec: Double?): Boolean =
        durationSec == null || !durationSec.isFinite() || durationSec > HEAVY_ABOVE_SECONDS
}

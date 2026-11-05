package com.amit152116.riffle.analysis

enum class ForegroundStep { RUN_CHUNK, WAIT_FOR_HASH, FINISH }

/**
 * Pure decisions for the foreground analysis service: what to do next, and what to tell the user.
 * The service keeps running while anything is analysable, waits (bounded) for the hash pass to produce more
 * recordings, then stops and leaves any remainder to the WorkManager worker.
 */
object ForegroundAnalysisPolicy {
    const val MAX_HASH_WAIT_POLLS = 120 // x 5 s = 10 minutes of waiting for hashing to catch up

    fun next(pendingAnalysis: Int, pendingHash: Int, hashWaitPolls: Int): ForegroundStep = when {
        pendingAnalysis > 0 -> ForegroundStep.RUN_CHUNK
        pendingHash > 0 && hashWaitPolls < MAX_HASH_WAIT_POLLS -> ForegroundStep.WAIT_FOR_HASH
        else -> ForegroundStep.FINISH
    }

    /** Percent done relative to what was pending when the service started, clamped to 0..100. */
    fun progressPercent(startPending: Int, pending: Int): Int {
        val total = startPending.coerceAtLeast(0)
        if (total == 0) return 100
        val done = (total - pending.coerceAtLeast(0)).coerceIn(0, total)
        return done * 100 / total
    }

    fun notificationText(startPending: Int, pending: Int): String {
        val total = startPending.coerceAtLeast(0)
        val done = (total - pending.coerceAtLeast(0)).coerceIn(0, total)
        return "Analysing music: $done of $total"
    }
}

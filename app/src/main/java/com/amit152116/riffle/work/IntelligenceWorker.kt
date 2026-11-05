package com.amit152116.riffle.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.log.RiffleLog
import kotlinx.coroutines.CancellationException

/**
 * Rebuilds similarity + clusters when the eligible recordings or the algorithm configs changed
 * (IntelligenceRepository.rebuildIfStale is a no-op otherwise). Chained after AnalysisWorker by
 * LibraryIngestPipeline - never enqueued standalone, since it consumes AudioFeatures rows.
 */
/** Bounds Result.retry(): a rebuild that keeps failing must not reschedule itself forever. */
object IntelligenceRetryPolicy {
    const val MAX_ATTEMPTS = 3
    fun shouldGiveUp(runAttemptCount: Int): Boolean = runAttemptCount >= MAX_ATTEMPTS
}

class IntelligenceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val log = RiffleLog(this)

    override suspend fun doWork(): Result {
        if (IntelligenceRetryPolicy.shouldGiveUp(runAttemptCount)) {
            log.e("Intelligence pass giving up after $runAttemptCount attempts")
            return Result.failure()
        }
        return try {
            val result = AppContainer.getInstance(applicationContext).intelligenceRepository.rebuildIfStale()
            log.w(
                "Intelligence pass finished: rebuilt=${result.rebuilt} recordings=${result.nRecordings} " +
                    "clusters=${result.nClusters} durationMs=${result.durationMs}"
            )
            Result.success()
        } catch (e: CancellationException) {
            // A cancelled coroutine must stay cancelled; Result.retry() here would mask WorkManager's stop.
            throw e
        } catch (e: Exception) {
            log.e("Intelligence pass failed unexpectedly", e)
            Result.retry()
        }
    }
}

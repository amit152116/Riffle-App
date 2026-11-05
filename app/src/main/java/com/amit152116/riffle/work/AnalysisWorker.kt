package com.amit152116.riffle.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.log.RiffleLog
import kotlinx.coroutines.CancellationException

/**
 * Runs one time-boxed chunk of analysis (see AnalysisRunner) and leaves the queue moving: the chunk
 * enqueues its own continuation, or the intelligence rebuild once nothing is pending. Chained after
 * LibraryHashWorker by LibraryIngestPipeline - never enqueued standalone, since AudioFeatures/
 * Fingerprint rows key off AudioContent.id, which only exists once hashing has run.
 */
class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val log = RiffleLog(this)

    override suspend fun doWork(): Result {
        val container = AppContainer.getInstance(applicationContext)
        return try {
            val chunk = container.libraryScanner.analyzeChunk(WorkManagerContinuationScheduler(applicationContext))
            log.w("Analysis chunk finished: analyzed=${chunk?.analyzed} failed=${chunk?.failed} more=${chunk?.morePending}")
            Result.success()
        } catch (e: CancellationException) {
            throw e // the runner already enqueued the continuation; never turn a system stop into a retry
        } catch (e: Exception) {
            // Per-item failures are handled inside the chunk - reaching here means something broader
            // broke (e.g. the DB itself). Linear 30 s backoff, three attempts, then give up.
            log.e("Analysis chunk failed unexpectedly (attempt $runAttemptCount)", e)
            if (AnalysisRetryPolicy.shouldRetry(runAttemptCount)) Result.retry() else Result.failure()
        }
    }
}

package com.amit152116.riffle.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.log.RiffleLog

/**
 * Runs LibraryScanner.analyzePendingAudioContent() as a WorkManager job, chained after
 * LibraryHashWorker by LibraryIngestPipeline - never enqueued standalone, since AudioFeatures/
 * Fingerprint rows key off AudioContent.id, which only exists once hashing has run.
 */
class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val log = RiffleLog(this)

    override suspend fun doWork(): Result {
        return try {
            val container = AppContainer.getInstance(applicationContext)
            val result = container.libraryScanner.analyzePendingAudioContent()
            log.w("Analysis pass finished: analyzed=${result.analyzed} failed=${result.failed}")
            Result.success()
        } catch (e: Exception) {
            // Per-item extraction failures are already caught and logged as IngestErrors inside
            // analyzePendingAudioContent() - reaching here means something broader broke (e.g.
            // the DB itself), worth a retry, same distinction LibraryHashWorker draws.
            log.e("Analysis pass failed unexpectedly", e)
            Result.retry()
        }
    }
}

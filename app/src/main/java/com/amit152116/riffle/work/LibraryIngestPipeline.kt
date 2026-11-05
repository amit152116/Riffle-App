package com.amit152116.riffle.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Owns the ordering between the hash pass and the analysis pass: AudioFeatures/Fingerprint
 * rows key off AudioContent.id, which only exists after LibraryHashWorker runs, so AnalysisWorker
 * must never be enqueued standalone. Both workers stay simple, single-purpose CoroutineWorkers
 * with no knowledge of what precedes or follows them - this object is the one place that knows
 * the chain.
 */
object LibraryIngestPipeline {
    private const val WORK_NAME = "library_ingest_pipeline"

    /**
     * KEEP, not REPLACE: both passes re-query "pending" fresh each run, so restarting a run
     * already in progress resets progress for no benefit (same reasoning LibraryHashWorker's
     * own enqueue() used to give before this object took over unique-work naming).
     */
    fun enqueue(context: Context) {
        val hash = OneTimeWorkRequestBuilder<LibraryHashWorker>().build()
        val analyze = OneTimeWorkRequestBuilder<AnalysisWorker>().build()
        WorkManager.getInstance(context)
            .beginUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, hash)
            .then(analyze)
            .enqueue()
    }
}

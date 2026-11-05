package com.amit152116.riffle.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.amit152116.riffle.service.AnalysisForegroundService

/**
 * Owns the ordering between the hash pass and the analysis pass: AudioFeatures/Fingerprint
 * rows key off AudioContent.id, which only exists after LibraryHashWorker runs, so AnalysisWorker
 * must never be enqueued standalone. The analysis worker processes time-boxed chunks and enqueues
 * its own continuation and, when nothing is pending, the intelligence rebuild (see AnalysisWork).
 */
object LibraryIngestPipeline {
    private const val WORK_NAME = "library_ingest_pipeline_v2"

    // Pre-chunking chain (hash -> analyze -> intelligence). Devices may still hold it stuck in
    // exponential backoff, and KEEP would make a re-enqueue under the same name a no-op, so it is
    // cancelled here (a no-op when absent). This is the only place the old name may appear.
    private const val LEGACY_WORK_NAME = "library_ingest_pipeline"

    /**
     * KEEP, not REPLACE: both passes re-query "pending" fresh each run, so restarting a run
     * already in progress resets progress for no benefit (same reasoning LibraryHashWorker's
     * own enqueue() used to give before this object took over unique-work naming).
     */
    fun enqueue(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        val hash = OneTimeWorkRequestBuilder<LibraryHashWorker>().build()
        workManager
            .beginUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, hash)
            .then(AnalysisWork.analysisRequest())
            .enqueue()
        // Also run analysis as a foreground service while the app is visible: it survives the screen turning off,
        // which background jobs do not. Shares the analysis lock with the worker; if the system refuses to start it
        // (app not visible) nothing changes and the worker chain above does the work.
        AnalysisForegroundService.startIfPossible(context)
    }
}

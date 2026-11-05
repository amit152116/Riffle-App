package com.amit152116.riffle.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.amit152116.riffle.analysis.ContinuationScheduler
import java.util.concurrent.TimeUnit

object AnalysisWork {
    const val CONTINUATION_WORK_NAME = "analysis_continuation"
    const val INTELLIGENCE_WORK_NAME = "intelligence_after_analysis"

    private val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()

    fun analysisRequest(delaySeconds: Long = 0L): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, AnalysisRetryPolicy.RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
            .apply { if (delaySeconds > 0) setInitialDelay(delaySeconds, TimeUnit.SECONDS) }
            .build()
}

/**
 * The next chunk is a fresh unique request appended to any pending one, so a running chunk never depends on
 * KEEP semantics; intelligence runs once after the last chunk (hash-gated inside the worker).
 */
class WorkManagerContinuationScheduler(context: Context) : ContinuationScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun enqueueNextChunk(delaySeconds: Long) {
        workManager.enqueueUniqueWork(
            AnalysisWork.CONTINUATION_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE,
            AnalysisWork.analysisRequest(delaySeconds)
        )
    }

    override fun enqueueIntelligence() {
        workManager.enqueueUniqueWork(
            AnalysisWork.INTELLIGENCE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<IntelligenceWorker>().build()
        )
    }
}

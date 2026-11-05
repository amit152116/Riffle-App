package com.amit152116.riffle.data.db.library

import android.content.Context
import android.net.Uri
import android.os.Process
import com.amit152116.riffle.analysis.AnalysisBudget
import com.amit152116.riffle.analysis.AnalysisParallelism
import com.amit152116.riffle.analysis.AnalysisOutcome
import com.amit152116.riffle.analysis.AnalysisWorkSource
import com.amit152116.riffle.analysis.PendingRecording
import com.amit152116.riffle.analysis.RecordingAnalyzer
import com.amit152116.riffle.analysis.TrackAnalyzer
import com.amit152116.riffle.data.db.analysis.AnalysisResultWriter
import com.amit152116.riffle.data.db.analysis.toFingerprint
import com.amit152116.riffle.data.db.analysis.toRoomAudioFeatures
import com.amit152116.riffle.dsp.PcmStreamDecoder
import com.amit152116.riffle.log.RiffleLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Failed analysis attempts per recording before it is left alone (visible as an IngestError). */
const val MAX_ANALYSIS_ATTEMPTS = 3
const val ANALYZE_STAGE = "analyze"
const val ANALYZE_INTERRUPTED_STAGE = "analyze_interrupted"

class RoomAnalysisWorkSource(
    private val repository: LibraryRepository,
    private val extractorVersion: String,
    private val configHash: String,
    private val maxAttempts: Int = MAX_ANALYSIS_ATTEMPTS
) : AnalysisWorkSource {
    override suspend fun nextPending(limit: Int): List<PendingRecording> =
        repository.getPendingAnalysis(extractorVersion, configHash, maxAttempts, limit)
            .map { PendingRecording(it.audioContentId, it.durationSec) }

    /** Counts a failed attempt on the track's own IngestError row; the cap in [nextPending] does the rest. */
    suspend fun recordFailure(audioContentId: Long, path: String, message: String?) {
        val previous = repository.getIngestErrorByPath(path)?.takeIf { it.stage == ANALYZE_STAGE && it.resolvedAt == null }
        repository.insertIngestError(
            IngestError(
                path = path, dev = null, inode = null, size = null, mtime = null,
                stage = ANALYZE_STAGE, message = message,
                attempts = (previous?.attempts ?: 0) + 1,
                lastAttemptAt = System.currentTimeMillis(), resolvedAt = null
            )
        )
    }
}

/**
 * Analyses one recording and persists the result. Per-track failures (including OutOfMemoryError) become
 * FAILED plus an IngestError attempt; cancellation is never swallowed. A recording whose analysis was
 * interrupted by the system [OVERSIZED_AFTER_INTERRUPTIONS] times while its estimate exceeds the
 * single-run budget is given up on with an explicit message (spec: oversized-track rule).
 */
class RoomRecordingAnalyzer(
    private val context: Context,
    private val repository: LibraryRepository,
    private val source: RoomAnalysisWorkSource,
    private val trackAnalyzer: TrackAnalyzer,
    private val resultWriter: AnalysisResultWriter,
    private val budget: AnalysisBudget = AnalysisBudget()
) : RecordingAnalyzer {
    private val log = RiffleLog(this)

    // Long tracks hold large PCM buffers: never more than a few of them at once, however many slots there are.
    private val heavyPermits = Semaphore(AnalysisParallelism.maxConcurrentHeavy(heapMb()))

    override suspend fun analyze(recording: PendingRecording): AnalysisOutcome =
        if (AnalysisParallelism.isHeavy(recording.durationSec)) heavyPermits.withPermit { analyzeNow(recording) }
        else analyzeNow(recording)

    private suspend fun analyzeNow(recording: PendingRecording): AnalysisOutcome = withContext(Dispatchers.IO) {
        val path = repository.getAnyPresentTrackPath(recording.audioContentId)
        if (path == null) {
            log.w("No present track left for AudioContent ${recording.audioContentId}, skipping analysis")
            return@withContext AnalysisOutcome.FAILED
        }
        val interruptions = repository.getIngestErrorByPath(path)
            ?.takeIf { it.stage == ANALYZE_INTERRUPTED_STAGE && it.resolvedAt == null }?.attempts ?: 0
        if (interruptions >= OVERSIZED_AFTER_INTERRUPTIONS &&
            budget.exceedsSingleRun(budget.estimateSeconds(recording.durationSec))
        ) {
            repeat(3) { source.recordFailure(recording.audioContentId, path, "exceeds single-run budget") }
            return@withContext AnalysisOutcome.FAILED
        }
        val priorityBefore = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            val job = coroutineContext[Job]
            val features = trackAnalyzer.analyze(
                isCancelled = { job?.isActive == false } // lets a system stop / the service's Stop interrupt between chunks
            ) { PcmStreamDecoder.open(context, Uri.parse(path)) }
            val now = System.currentTimeMillis()
            resultWriter.write(
                features.toRoomAudioFeatures(audioContentId = recording.audioContentId, analyzedAt = now),
                features.toFingerprint(audioContentId = recording.audioContentId, computedAt = now)
            )
            AnalysisOutcome.ANALYZED
        } catch (e: CancellationException) {
            withContext(NonCancellable) { markInterrupted(path) }
            throw e
        } catch (e: Throwable) { // includes OutOfMemoryError: one bad track must not stop the queue
            log.w("Failed to analyze AudioContent ${recording.audioContentId} via $path", e)
            source.recordFailure(recording.audioContentId, path, e.message ?: e.javaClass.simpleName)
            AnalysisOutcome.FAILED
        } finally {
            Process.setThreadPriority(priorityBefore)
        }
    }

    private suspend fun markInterrupted(path: String) {
        val previous = repository.getIngestErrorByPath(path)
            ?.takeIf { it.stage == ANALYZE_INTERRUPTED_STAGE && it.resolvedAt == null }
        repository.insertIngestError(
            IngestError(
                path = path, dev = null, inode = null, size = null, mtime = null,
                stage = ANALYZE_INTERRUPTED_STAGE, message = "interrupted by the system",
                attempts = (previous?.attempts ?: 0) + 1, lastAttemptAt = System.currentTimeMillis(), resolvedAt = null
            )
        )
    }

    private companion object {
        const val OVERSIZED_AFTER_INTERRUPTIONS = 3

        fun heapMb(): Int = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

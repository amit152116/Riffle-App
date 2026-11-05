package com.amit152116.riffle.analysis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class PendingRecording(val audioContentId: Long, val durationSec: Double?)

enum class AnalysisOutcome { ANALYZED, FAILED }

interface AnalysisWorkSource {
    /** Recordings still worth trying, in a stable order. Exhausted (repeatedly failing) ones are not returned. */
    suspend fun nextPending(limit: Int): List<PendingRecording>
}

interface RecordingAnalyzer {
    /** Must persist its own result and catch per-track failures (returning FAILED); only cancellation may escape. */
    suspend fun analyze(recording: PendingRecording): AnalysisOutcome
}

interface ContinuationScheduler {
    fun enqueueNextChunk(delaySeconds: Long = 0L)
    fun enqueueIntelligence()
}

data class ChunkResult(val analyzed: Int, val failed: Int, val stoppedByBudget: Boolean, val morePending: Boolean)

/**
 * One time-boxed chunk of analysis. A chunk processes many recordings (normal songs never become
 * one job each): before each recording it asks the [budget] whether it still fits, except that the
 * first recording is always attempted so the queue always moves. Nothing in here can change what a
 * recording's result is, only when the chunk stops.
 */
class AnalysisRunner(
    private val source: AnalysisWorkSource,
    private val analyzer: RecordingAnalyzer,
    private val scheduler: ContinuationScheduler,
    private val budget: AnalysisBudget = AnalysisBudget(),
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val batchSize: Int = 50,
    /** How many recordings may be analysed at once; re-read at the start of every chunk (values < 1 mean 1). */
    private val parallelism: () -> Int = { 1 }
) {
    suspend fun runChunk(): ChunkResult = coroutineScope {
        val startNanos = clock.nowNanos()
        val slots = parallelism().coerceAtLeast(1)
        // Wall-clock budget: concurrent analyses slow each other a little, so pad each estimate.
        val contention = 1.0 + CONTENTION_PER_EXTRA_SLOT * (slots - 1)
        val permits = Semaphore(slots)
        val seen = HashSet<Long>() // tried in this chunk: a failing recording is never retried within it
        val analyzed = AtomicInteger()
        val failed = AtomicInteger()
        val childCancellation = AtomicReference<CancellationException?>(null)
        var stoppedByBudget = false
        loop@ while (true) {
            ensureActive()
            val batch = source.nextPending(batchSize + seen.size).filter { it.audioContentId !in seen }.take(batchSize)
            if (batch.isEmpty()) break
            for (rec in batch) {
                permits.acquire() // waiting for a free slot consumes wall-clock time, so check the budget after it
                childCancellation.get()?.let { permits.release(); throw it } // stop launching once a child was cancelled
                val elapsed = (clock.nowNanos() - startNanos) / 1e9
                if (!budget.shouldStartNext(elapsed, budget.estimateSeconds(rec.durationSec) * contention, seen.size)) {
                    permits.release()
                    stoppedByBudget = true
                    break@loop
                }
                if (!isActive) {
                    permits.release()
                    ensureActive()
                }
                seen.add(rec.audioContentId)
                launch {
                    try {
                        when (analyzer.analyze(rec)) {
                            AnalysisOutcome.ANALYZED -> analyzed.incrementAndGet()
                            AnalysisOutcome.FAILED -> failed.incrementAndGet()
                        }
                    } catch (e: CancellationException) {
                        childCancellation.compareAndSet(null, e) // a child's own cancellation must not vanish silently
                        throw e
                    } finally {
                        permits.release()
                    }
                }
            }
        }
        // coroutineScope waits for every in-flight recording before returning (or cancels them all on failure).
        val started = seen.toSet()
        this.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
        childCancellation.get()?.let { throw it }
        val more = stoppedByBudget || source.nextPending(started.size + 1).any { it.audioContentId !in started }
        ChunkResult(analyzed.get(), failed.get(), stoppedByBudget, more)
    }

    /**
     * Runs a chunk and always leaves the queue moving. A system stop (cancellation) enqueues the next
     * chunk inside a non-cancellable block and rethrows: it is never turned into a retry, so backoff
     * cannot accumulate. Unexpected errors propagate without scheduling (the worker owns that policy).
     */
    suspend fun runChunkAndSchedule(): ChunkResult {
        val result = try {
            runChunk()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { scheduler.enqueueNextChunk() }
            throw e
        }
        if (result.morePending) scheduler.enqueueNextChunk() else scheduler.enqueueIntelligence()
        return result
    }

    private companion object {
        const val CONTENTION_PER_EXTRA_SLOT = 0.15
    }
}

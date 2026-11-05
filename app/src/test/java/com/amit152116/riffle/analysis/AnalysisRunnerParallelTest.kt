package com.amit152116.riffle.analysis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AnalysisRunnerParallelTest {
    private class Scheduler : ContinuationScheduler {
        var continuations = 0
        var intelligence = 0
        override fun enqueueNextChunk(delaySeconds: Long) { continuations++ }
        override fun enqueueIntelligence() { intelligence++ }
    }

    private class Source(ids: List<Long>, val durationSec: Double? = 200.0) : AnalysisWorkSource {
        val pending = java.util.concurrent.CopyOnWriteArrayList(ids)
        override suspend fun nextPending(limit: Int) = pending.take(limit).map { PendingRecording(it, durationSec) }
    }

    private class Analyzer(
        private val source: Source,
        private val workMillis: Long = 20,
        private val failing: Set<Long> = emptySet(),
        private val onStart: (Long) -> Unit = {}
    ) : RecordingAnalyzer {
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        val analysedIds = java.util.Collections.synchronizedList(ArrayList<Long>())

        override suspend fun analyze(recording: PendingRecording): AnalysisOutcome {
            val now = running.incrementAndGet()
            maxRunning.accumulateAndGet(now, ::maxOf)
            try {
                onStart(recording.audioContentId)
                delay(workMillis)
                analysedIds += recording.audioContentId
                return if (recording.audioContentId in failing) AnalysisOutcome.FAILED
                else { source.pending.remove(recording.audioContentId); AnalysisOutcome.ANALYZED }
            } finally {
                running.decrementAndGet()
            }
        }
    }

    private fun runner(source: Source, analyzer: RecordingAnalyzer, sched: Scheduler, parallelism: Int,
                       budget: AnalysisBudget = AnalysisBudget(), batch: Int = 50) =
        AnalysisRunner(source, analyzer, sched, budget, SystemMonotonicClock, batch, parallelism = { parallelism })

    @Test fun neverExceedsTheParallelism_andActuallyReachesIt() = runBlocking {
        val src = Source((1L..24L).toList()); val a = Analyzer(src)
        val r = runner(src, a, Scheduler(), parallelism = 3).runChunk()
        assertEquals(24, r.analyzed)
        assertEquals(3, a.maxRunning.get())
    }

    @Test fun everyRecordingIsAnalysedExactlyOnce_regardlessOfParallelism() = runBlocking {
        for (p in listOf(1, 2, 4, 8)) {
            val src = Source((1L..30L).toList()); val a = Analyzer(src, workMillis = 5)
            val sched = Scheduler()
            runner(src, a, sched, p, batch = 7).runChunkAndSchedule()
            assertEquals("p=$p", (1L..30L).toList(), a.analysedIds.sorted())
            assertEquals("p=$p analysed twice", 30, a.analysedIds.size)
            assertEquals(1, sched.intelligence); assertEquals(0, sched.continuations)
        }
    }

    @Test fun invalidParallelism_isTreatedAsOne() = runBlocking {
        for (p in listOf(0, -3)) {
            val src = Source((1L..6L).toList()); val a = Analyzer(src, workMillis = 5)
            runner(src, a, Scheduler(), p).runChunk()
            assertEquals("p=$p", 1, a.maxRunning.get())
        }
    }

    @Test fun aFailingRecording_doesNotStopTheOthers_andIsNotRetriedInTheChunk() = runBlocking {
        val src = Source((1L..10L).toList()); val a = Analyzer(src, failing = setOf(3L, 4L))
        val r = runner(src, a, Scheduler(), parallelism = 4).runChunk()
        assertEquals(8, r.analyzed); assertEquals(2, r.failed)
        assertEquals(10, a.analysedIds.size)
        assertFalse(r.morePending)
    }

    @Test fun theBudgetStopsStartingNewRecordings_butInFlightOnesFinish() = runBlocking {
        val src = Source((1L..40L).toList()); val a = Analyzer(src, workMillis = 30)
        val tiny = AnalysisBudget(AnalysisBudgetConfig(chunkBudgetSeconds = 0.05, minEstimateSeconds = 0.001, maxEstimateSeconds = 0.001))
        val sched = Scheduler()
        val r = runner(src, a, sched, parallelism = 3, budget = tiny).runChunkAndSchedule()
        assertTrue(r.stoppedByBudget && r.morePending)
        assertTrue("started only what fit: ${r.analyzed}", r.analyzed in 1..12)
        assertEquals(r.analyzed, a.analysedIds.size) // nothing left half-done
        assertEquals(1, sched.continuations); assertEquals(0, sched.intelligence)
    }

    @Test fun firstRecordingIsAlwaysStarted_evenWithAnImpossibleBudget() = runBlocking {
        val src = Source(listOf(1L, 2L, 3L), durationSec = 3600.0); val a = Analyzer(src)
        val tiny = AnalysisBudget(AnalysisBudgetConfig(chunkBudgetSeconds = 0.001))
        val r = runner(src, a, Scheduler(), parallelism = 3, budget = tiny).runChunk()
        assertTrue(r.analyzed >= 1)
    }

    @Test fun cancellation_cancelsEveryInFlightRecording_enqueuesOneContinuation_andRethrows() = runBlocking {
        val src = Source((1L..12L).toList())
        val started = AtomicInteger()
        val a = Analyzer(src, workMillis = 5_000, onStart = { started.incrementAndGet() })
        val sched = Scheduler()
        var thrown: Throwable? = null
        val job: Job = launch {
            try { runner(src, a, sched, parallelism = 3).runChunkAndSchedule() } catch (e: CancellationException) { thrown = e; throw e }
        }
        while (started.get() < 3) delay(5)
        job.cancel()
        job.join()
        assertTrue(thrown is CancellationException)
        assertEquals(1, sched.continuations); assertEquals(0, sched.intelligence)
        assertEquals(0, a.running.get()) // every in-flight analysis was cancelled and unwound
        assertTrue(a.analysedIds.isEmpty())
    }

    @Test fun anUnexpectedErrorInOneRecording_propagates_withoutSchedulingAnything() {
        val src = Source((1L..6L).toList())
        val a = Analyzer(src, onStart = { if (it == 2L) throw IllegalStateException("db down") })
        val sched = Scheduler()
        var thrown: Throwable? = null
        try { runBlocking { runner(src, a, sched, parallelism = 3).runChunkAndSchedule() } } catch (e: IllegalStateException) { thrown = e }
        assertTrue(thrown != null)
        assertEquals(0, sched.continuations); assertEquals(0, sched.intelligence)
    }
}

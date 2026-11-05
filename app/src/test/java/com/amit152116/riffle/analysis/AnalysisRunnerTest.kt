package com.amit152116.riffle.analysis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisRunnerTest {
    private class FakeClock : MonotonicClock {
        var nanos = 0L
        override fun nowNanos() = nanos
        fun advanceSeconds(s: Double) { nanos += (s * 1e9).toLong() }
    }

    private class Scheduler : ContinuationScheduler {
        var continuations = 0
        var lastDelay = -1L
        var intelligence = 0
        override fun enqueueNextChunk(delaySeconds: Long) { continuations++; lastDelay = delaySeconds }
        override fun enqueueIntelligence() { intelligence++ }
    }

    /** Pending until analysed; failing ids stay pending (as in the real source until attempts run out). */
    private class Source(ids: List<Long>, private val durationSec: Double? = 200.0, val failing: Set<Long> = emptySet()) : AnalysisWorkSource {
        val pending = ids.toMutableList()
        override suspend fun nextPending(limit: Int) = pending.take(limit).map { PendingRecording(it, durationSec) }
    }

    private class Analyzer(
        private val source: Source, private val clock: FakeClock, private val secondsEach: Double,
        val analyzedOrder: MutableList<Long> = ArrayList(), private val onEach: ((Long) -> Unit)? = null
    ) : RecordingAnalyzer {
        override suspend fun analyze(recording: PendingRecording): AnalysisOutcome {
            onEach?.invoke(recording.audioContentId)
            clock.advanceSeconds(secondsEach)
            analyzedOrder += recording.audioContentId
            return if (recording.audioContentId in source.failing) AnalysisOutcome.FAILED
            else { source.pending.remove(recording.audioContentId); AnalysisOutcome.ANALYZED }
        }
    }

    private fun runner(source: Source, analyzer: RecordingAnalyzer, scheduler: Scheduler, clock: FakeClock,
                       budget: AnalysisBudget = AnalysisBudget(), batch: Int = 50) =
        AnalysisRunner(source, analyzer, scheduler, budget, clock, batch)

    @Test fun normalSongs_allFitOneChunk_soNoContinuationAndIntelligenceRunsOnce() = runBlocking {
        val clock = FakeClock(); val sched = Scheduler(); val src = Source((1L..100L).toList())
        val r = runner(src, Analyzer(src, clock, 0.5), sched, clock)
        val result = r.runChunkAndSchedule()
        assertEquals(100, result.analyzed); assertFalse(result.morePending)
        assertEquals(0, sched.continuations); assertEquals(1, sched.intelligence)
    }

    @Test fun budgetStopsTheChunk_andTheNextChunkIsScheduled() = runBlocking {
        val clock = FakeClock(); val sched = Scheduler(); val src = Source((1L..10L).toList())
        val budget = AnalysisBudget(AnalysisBudgetConfig(chunkBudgetSeconds = 100.0))
        val est = budget.estimateSeconds(200.0)
        val perTrack = est // each track "takes" exactly its estimate
        val result = runner(src, Analyzer(src, clock, perTrack), sched, clock, budget).runChunkAndSchedule()
        val fit = 1 + ((100.0 - perTrack) / perTrack).toInt() // first is free, then while elapsed + est <= 100
        assertEquals(fit, result.analyzed)
        assertTrue(result.stoppedByBudget && result.morePending)
        assertEquals(1, sched.continuations); assertEquals(0, sched.intelligence)
    }

    @Test fun firstTrackIsAlwaysAttempted_evenWhenItsEstimateExceedsTheBudget() = runBlocking {
        val clock = FakeClock(); val sched = Scheduler(); val src = Source(listOf(1L, 2L, 3L), durationSec = 3600.0)
        val budget = AnalysisBudget(AnalysisBudgetConfig(chunkBudgetSeconds = 1.0))
        val result = runner(src, Analyzer(src, clock, 5.0), sched, clock, budget).runChunkAndSchedule()
        assertEquals(1, result.analyzed)
        assertEquals(1, sched.continuations)
    }

    @Test fun repeatedChunks_alwaysMakeProgress_untilTheQueueIsEmpty() = runBlocking {
        val clock = FakeClock(); val sched = Scheduler(); val src = Source((1L..25L).toList())
        val budget = AnalysisBudget(AnalysisBudgetConfig(chunkBudgetSeconds = 0.001))
        val analyzer = Analyzer(src, clock, 1.0)
        var chunks = 0
        while (src.pending.isNotEmpty() && chunks < 100) { runner(src, analyzer, sched, clock, budget).runChunkAndSchedule(); chunks++ }
        assertTrue(src.pending.isEmpty())
        assertEquals(25, chunks) // one track per chunk with a tiny budget
        assertEquals(24, sched.continuations); assertEquals(1, sched.intelligence)
    }

    @Test fun failingRecordings_areNotRetriedWithinAChunk_andDoNotLoopForever() = runBlocking {
        val clock = FakeClock(); val sched = Scheduler()
        val src = Source(listOf(1L, 2L, 3L, 4L), failing = setOf(2L, 3L))
        val analyzer = Analyzer(src, clock, 0.1)
        val result = runner(src, analyzer, sched, clock).runChunk()
        assertEquals(2, result.analyzed); assertEquals(2, result.failed)
        assertEquals(listOf(1L, 2L, 3L, 4L), analyzer.analyzedOrder) // each exactly once
        assertFalse(result.morePending) // the failed ones stay pending in the source but were already tried this chunk
    }

    @Test fun cancellation_enqueuesContinuation_andRethrows() {
        val clock = FakeClock(); val sched = Scheduler(); val src = Source(listOf(1L, 2L, 3L))
        val analyzer = Analyzer(src, clock, 1.0, onEach = { if (it == 2L) throw CancellationException("system stop") })
        assertThrows(CancellationException::class.java) { runBlocking { runner(src, analyzer, sched, clock).runChunkAndSchedule() } }
        assertEquals(1, sched.continuations); assertEquals(0, sched.intelligence)
        assertEquals(listOf(1L), analyzer.analyzedOrder) // 1 was persisted before the stop
    }

    @Test fun unexpectedErrors_propagate_withoutSchedulingAnything() {
        val clock = FakeClock(); val sched = Scheduler(); val src = Source(listOf(1L, 2L))
        val analyzer = Analyzer(src, clock, 1.0, onEach = { if (it == 2L) throw IllegalStateException("db down") })
        assertThrows(IllegalStateException::class.java) { runBlocking { runner(src, analyzer, sched, clock).runChunkAndSchedule() } }
        assertEquals(0, sched.continuations); assertEquals(0, sched.intelligence)
    }

    @Test fun emptyQueue_schedulesOnlyIntelligence() = runBlocking {
        val clock = FakeClock(); val sched = Scheduler(); val src = Source(emptyList())
        val result = runner(src, Analyzer(src, clock, 1.0), sched, clock).runChunkAndSchedule()
        assertEquals(0, result.analyzed); assertFalse(result.morePending)
        assertEquals(0, sched.continuations); assertEquals(1, sched.intelligence)
    }

    @Test fun outputDoesNotDependOnTheBudget_everyRecordingIsAnalysedExactlyOnceInOrder() = runBlocking {
        val ids = (1L..40L).toList()
        val budgets = listOf(0.0, -3.0, Double.NaN, 0.001, 1.0, 50.0, 180.0, 1e9, Double.POSITIVE_INFINITY)
        var reference: List<Long>? = null
        for (chunkBudget in budgets) {
            val clock = FakeClock(); val sched = Scheduler(); val src = Source(ids)
            val analyzer = Analyzer(src, clock, 7.0)
            val budget = AnalysisBudget(AnalysisBudgetConfig(chunkBudgetSeconds = chunkBudget))
            var guard = 0
            while (src.pending.isNotEmpty() && guard++ < 200) runner(src, analyzer, sched, clock, budget).runChunkAndSchedule()
            assertTrue("budget=$chunkBudget did not finish", src.pending.isEmpty())
            assertEquals("budget=$chunkBudget", ids, analyzer.analyzedOrder.distinct())
            assertEquals("budget=$chunkBudget analysed twice", ids.size, analyzer.analyzedOrder.size)
            reference = reference ?: analyzer.analyzedOrder
            assertEquals(reference, analyzer.analyzedOrder)
        }
    }

    @Test fun batching_doesNotSkipOrRepeat_whenTheBatchIsSmallerThanTheQueue() = runBlocking {
        val clock = FakeClock(); val sched = Scheduler(); val src = Source((1L..23L).toList())
        val analyzer = Analyzer(src, clock, 0.1)
        runner(src, analyzer, sched, clock, batch = 5).runChunk()
        assertEquals((1L..23L).toList(), analyzer.analyzedOrder)
    }
}

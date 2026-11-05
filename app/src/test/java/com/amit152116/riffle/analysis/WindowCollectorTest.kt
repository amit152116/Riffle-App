package com.amit152116.riffle.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WindowCollectorTest {
    private class Emitted(val index: Int, val samples: FloatArray, val rate: Int)

    /** Feeds a ramp (sample value == absolute index) in random-sized chunks. */
    private fun run(
        windows: List<AnalysisWindow>, rate: Int, totalSamples: Int, chunkMax: Int, seed: Int = 1
    ): List<Emitted> {
        val out = ArrayList<Emitted>()
        val c = WindowCollector(windows) { i, s, r -> out += Emitted(i, s, r) }
        val rng = Random(seed)
        var pos = 0
        while (pos < totalSamples) {
            val n = minOf(rng.nextInt(1, chunkMax + 1), totalSamples - pos)
            val chunk = FloatArray(n) { (pos + it).toFloat() }
            c.accept(chunk, n, rate)
            pos += n
        }
        c.finish()
        return out
    }

    @Test fun eachWindow_receivesExactlyItsSamples_regardlessOfChunking() {
        val rate = 1000
        val windows = listOf(AnalysisWindow(2.0, 5.0), AnalysisWindow(10.0, 12.5), AnalysisWindow(20.0, 23.0))
        for (chunkMax in listOf(1, 7, 333, 5000, 100_000)) {
            val out = run(windows, rate, 30 * rate, chunkMax)
            assertEquals("chunkMax=$chunkMax", listOf(0, 1, 2), out.map { it.index })
            out.forEach { e ->
                val start = (windows[e.index].startSec * rate).toInt()
                assertEquals(((windows[e.index].endSec - windows[e.index].startSec) * rate).toInt(), e.samples.size)
                assertEquals(start.toFloat(), e.samples.first(), 0f)
                assertEquals((start + e.samples.size - 1).toFloat(), e.samples.last(), 0f)
                assertEquals(rate, e.rate)
            }
        }
    }

    @Test fun windowsAreEmittedWhenComplete_notAtTheEnd() {
        val emitted = ArrayList<Int>()
        val c = WindowCollector(listOf(AnalysisWindow(1.0, 2.0), AnalysisWindow(5.0, 6.0))) { i, _, _ -> emitted += i }
        c.accept(FloatArray(3000) { it.toFloat() }, 3000, 1000) // covers 0-3 s: window 0 done
        assertEquals(listOf(0), emitted)
    }

    @Test fun streamEndingInsideAWindow_flushesTheCollectedPart() {
        val out = run(listOf(AnalysisWindow(4.0, 10.0)), 1000, 7 * 1000, 500)
        assertEquals(1, out.size)
        assertEquals(3000, out[0].samples.size) // 4 s .. 7 s
        assertEquals(4000f, out[0].samples.first(), 0f)
    }

    @Test fun windowsWithFewerThanOneSecond_areDropped() {
        val out = run(listOf(AnalysisWindow(4.0, 10.0)), 1000, 4 * 1000 + 300, 500)
        assertTrue(out.isEmpty())
    }

    @Test fun streamEndingBeforeAWindow_emitsNothingForIt() {
        val out = run(listOf(AnalysisWindow(50.0, 60.0)), 1000, 10 * 1000, 500)
        assertTrue(out.isEmpty())
    }

    @Test fun overlappingWindows_eachGetTheirOwnBuffer() {
        val out = run(listOf(AnalysisWindow(1.0, 4.0), AnalysisWindow(2.0, 5.0)), 1000, 8 * 1000, 300)
        assertEquals(2, out.size)
        assertEquals(1000f, out[0].samples.first(), 0f)
        assertEquals(2000f, out[1].samples.first(), 0f)
    }

    @Test fun works_at48kAnd22k() {
        for (rate in listOf(22_050, 48_000)) {
            val out = run(listOf(AnalysisWindow(3.0, 5.0)), rate, 10 * rate, 4096)
            assertEquals(2 * rate, out.single().samples.size)
        }
    }

    @Test fun sampleRateChangeMidStream_isRejected() {
        val c = WindowCollector(listOf(AnalysisWindow(0.0, 5.0))) { _, _, _ -> }
        c.accept(FloatArray(10), 10, 44_100)
        assertThrows(IllegalStateException::class.java) { c.accept(FloatArray(10), 10, 48_000) }
    }

    @Test fun finishWithNoInput_emitsNothing() {
        val c = WindowCollector(listOf(AnalysisWindow(0.0, 5.0))) { _, _, _ -> throw AssertionError("no window expected") }
        c.finish()
    }
}

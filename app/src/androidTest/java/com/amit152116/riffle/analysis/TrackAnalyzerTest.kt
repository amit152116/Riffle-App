package com.amit152116.riffle.analysis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.dsp.PcmStreamDecoder
import com.amit152116.riffle.dsp.WavFixtures
import com.amit152116.riffle.dsp.expectedFingerprintItems
import com.amit152116.riffle.dsp.fingerprintItemTolerance
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class TrackAnalyzerTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "track-analyzer-test").apply { mkdirs() }
    private val extractor = EssentiaExtractor()

    @After fun cleanUp() { dir.deleteRecursively() }

    /** Clicks every 0.5 s (120 BPM) over a low tone. */
    private fun clickFixture(name: String, seconds: Double, rate: Int, channels: Int): File {
        val period = rate / 2
        return File(dir, name).also {
            WavFixtures.writePcm16Wav(it, seconds, rate, channels) { f ->
                val phase = (f % period).toInt()
                val click = if (phase < 300) (1.0 - phase / 300.0) * sin(2 * PI * 1000.0 * phase / rate) else 0.0
                (0.6 * click + 0.05 * sin(2 * PI * 110.0 * f / rate)).toFloat()
            }
        }
    }

    private fun analyze(f: File, cfg: AnalysisPlanConfig = AnalysisPlanConfig()) =
        TrackAnalyzer(extractor, cfg).analyze { PcmStreamDecoder.open(f.absolutePath) }

    @Test fun shortTrack_takesTheFullPath_andEqualsTheDirectExtraction() {
        val f = clickFixture("short.wav", 60.0, 44_100, 2)
        val viaAnalyzer = analyze(f)
        val direct = extractor.extractFile(f.absolutePath)
        assertEquals(direct.bpm, viaAnalyzer.bpm, 0f)
        assertEquals(direct.loudnessLufs, viaAnalyzer.loudnessLufs, 0f)
        assertArrayEquals(direct.mfccMean, viaAnalyzer.mfccMean, 0f)
        assertArrayEquals(direct.fingerprint, viaAnalyzer.fingerprint)
        assertEquals(direct.duration, viaAnalyzer.duration, 0f)
    }

    @Test fun windowedTrack_hasFullLengthFingerprint_fullDuration_andSaneFeatures() {
        val cfg = AnalysisPlanConfig(fullAnalysisMaxSeconds = 20.0, windowCount = 3, windowSeconds = 8.0)
        val f = clickFixture("windowed.wav", 120.0, 22_050, 1)
        val r = analyze(f, cfg)
        assertEquals(120.0, r.duration.toDouble(), 0.5)
        assertEquals(expectedFingerprintItems(120.0), r.fingerprint.size.toDouble(), fingerprintItemTolerance(120.0))
        assertEquals(EssentiaExtractor.FINGERPRINT_CLI_ALGORITHM, r.fingerprintAlgorithm)
        assertEquals(120.0, r.bpm.toDouble(), 6.0)
        assertTrue(r.loudnessLufs.isFinite() && r.energy in 0f..1f && r.mfccMean.size == 13 && r.mfccMean.all { it.isFinite() })
    }

    @Test fun windowedFingerprint_equalsTheStreamedFingerprintOfTheWholeTrack() {
        val cfg = AnalysisPlanConfig(fullAnalysisMaxSeconds = 20.0, windowCount = 2, windowSeconds = 6.0)
        val f = clickFixture("fp.wav", 90.0, 44_100, 2)
        val r = analyze(f, cfg)
        val whole = extractor.extractFile(f.absolutePath).fingerprint // 44.1 kHz: streamed == batch exactly
        assertArrayEquals(whole, r.fingerprint)
    }

    @Test fun windowedResult_isIndependentOfHowTheDecoderChunksTheStream() {
        // The decoder decides chunk sizes; two runs over the same file must be identical.
        val cfg = AnalysisPlanConfig(fullAnalysisMaxSeconds = 20.0, windowCount = 3, windowSeconds = 8.0)
        val f = clickFixture("det.wav", 100.0, 48_000, 2)
        val a = analyze(f, cfg); val b = analyze(f, cfg)
        assertEquals(a.bpm, b.bpm, 0f); assertArrayEquals(a.mfccMean, b.mfccMean, 0f); assertArrayEquals(a.fingerprint, b.fingerprint)
    }

    @Test fun rates_and_channelCounts_all_work() {
        val cfg = AnalysisPlanConfig(fullAnalysisMaxSeconds = 10.0, windowCount = 2, windowSeconds = 5.0)
        for ((rate, ch) in listOf(22_050 to 1, 44_100 to 2, 48_000 to 2)) {
            val r = analyze(clickFixture("r_${rate}_$ch.wav", 40.0, rate, ch), cfg)
            assertTrue("rate=$rate", r.fingerprint.isNotEmpty() && r.duration > 39f)
        }
    }

    @Test fun trackShorterThanThePlannedWindows_stillProducesAResult() {
        // Container claims a long track but only a few seconds decode: windows past the end are dropped.
        val cfg = AnalysisPlanConfig(fullAnalysisMaxSeconds = 1.0, windowCount = 6, windowSeconds = 3.0)
        val r = analyze(clickFixture("tiny.wav", 8.0, 44_100, 1), cfg)
        assertTrue(r.mfccMean.size == 13 && r.duration > 7f)
    }

    @Test fun twentyFiveMinuteTrack_completesWithBoundedHeap() {
        val f = clickFixture("long.wav", 25 * 60.0, 22_050, 1)
        val rt = Runtime.getRuntime()
        System.gc()
        val base = rt.totalMemory() - rt.freeMemory()
        val peak = java.util.concurrent.atomic.AtomicLong(base)
        val sampler = Thread { while (!Thread.currentThread().isInterrupted) { peak.accumulateAndGet(rt.totalMemory() - rt.freeMemory(), ::maxOf); try { Thread.sleep(20) } catch (_: InterruptedException) { return@Thread } } }
        sampler.start()
        val r = try { analyze(f) } finally { sampler.interrupt(); sampler.join() }
        assertEquals(1500.0, r.duration.toDouble(), 1.0)
        assertEquals(expectedFingerprintItems(1500.0), r.fingerprint.size.toDouble(), fingerprintItemTolerance(1500.0))
        val usedMb = (peak.get() - base) / (1024 * 1024)
        assertTrue("peak extra heap ${usedMb} MB", usedMb < 96) // one 45 s window + a 64k chunk + the fingerprint, not 25 minutes of PCM
    }

    @Test fun cancellation_stopsAWindowedAnalysisAtTheNextChunk_andThrows() {
        val cfg = AnalysisPlanConfig(fullAnalysisMaxSeconds = 20.0, windowCount = 3, windowSeconds = 8.0)
        val f = clickFixture("cancel.wav", 600.0, 22_050, 1)
        var polls = 0
        org.junit.Assert.assertThrows(java.util.concurrent.CancellationException::class.java) {
            TrackAnalyzer(extractor, cfg).analyze(isCancelled = { ++polls > 5 }) { PcmStreamDecoder.open(f.absolutePath) }
        }
        assertEquals(6, polls) // stopped at the first poll that asked to cancel; it did not keep decoding
    }

    @Test fun cancellation_alsoInterruptsTheWholeTrackDecode() {
        val f = clickFixture("cancel-full.wav", 60.0, 44_100, 2)
        org.junit.Assert.assertThrows(java.util.concurrent.CancellationException::class.java) {
            TrackAnalyzer(extractor).analyze(isCancelled = { true }) { PcmStreamDecoder.open(f.absolutePath) }
        }
    }
}

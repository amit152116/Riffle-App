package com.amit152116.riffle.analysis

import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.dsp.PcmStreamDecoder
import com.amit152116.riffle.dsp.WavFixtures
import com.amit152116.riffle.dsp.decodeToMonoPcm
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

/** Manual performance experiments (logcat tag RiffleExp). Numbers only; asserts nothing about speed. */
@RunWith(AndroidJUnit4::class)
class PerformanceExperimentsTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "perf").apply { mkdirs() }
    private val extractor = EssentiaExtractor()

    @After fun cleanUp() { dir.deleteRecursively() }

    private fun music(seconds: Double, rate: Int, ch: Int, name: String): File = File(dir, name).also {
        WavFixtures.writePcm16Wav(it, seconds, rate, ch) { f ->
            val t = f.toDouble() / rate
            val body = (0.3 * sin(2 * PI * 220 * t) + 0.2 * sin(2 * PI * 440 * t * (1 + 0.01 * sin(t))) + 0.1 * sin(2 * PI * 1250 * t))
            (body * (if ((f % (rate / 2)) < 400) 1.0 else 0.4)).toFloat()
        }
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Double> {
        val t = System.nanoTime()
        val r = block()
        return r to (System.nanoTime() - t) / 1e9
    }

    private fun log(msg: String) = Log.i("RiffleExp", msg)

    @Test fun e0_stageTimings_and_resampleCost() {
        val f44 = music(120.0, 44_100, 2, "s44.wav")
        val (s44, r44) = decodeToMonoPcm(f44.absolutePath)
        extractor.extract(s44, r44, withFingerprint = false) // warm-up
        val (_, t44) = timed { extractor.extract(s44, r44, withFingerprint = false) }
        log("E0 120s@44.1k features-only=%.2fs (%.4f s/s) stages: %s".format(t44, t44 / 120, extractor.lastStageTimings()))
        val (_, tfp) = timed { extractor.extract(s44, r44, withFingerprint = true) }
        log("E0 120s@44.1k with-fingerprint=%.2fs stages: %s".format(tfp, extractor.lastStageTimings()))

        val f48 = music(120.0, 48_000, 2, "s48.wav")
        val (s48, r48) = decodeToMonoPcm(f48.absolutePath)
        val (_, t48) = timed { extractor.extract(s48, r48, withFingerprint = false) }
        log("E0 120s@48k features-only=%.2fs (%.4f s/s; includes speex resample) stages: %s".format(t48, t48 / 120, extractor.lastStageTimings()))
        assertTrue(t44 > 0)
    }

    @Test fun e1_threadPriority_effect() {
        val f = music(60.0, 44_100, 2, "p.wav")
        val (s, r) = decodeToMonoPcm(f.absolutePath)
        extractor.extract(s, r, withFingerprint = false) // warm-up on this thread
        for ((label, prio) in listOf(
            "DEFAULT(0)" to Process.THREAD_PRIORITY_DEFAULT,
            "BACKGROUND(10)" to Process.THREAD_PRIORITY_BACKGROUND,
            "LESS_FAVORABLE(1)" to Process.THREAD_PRIORITY_LESS_FAVORABLE,
            "DEFAULT(0)-again" to Process.THREAD_PRIORITY_DEFAULT
        )) {
            val times = (1..3).map {
                var t = 0.0
                val th = Thread {
                    Process.setThreadPriority(prio)
                    t = timed { extractor.extract(s, r, withFingerprint = false) }.second
                }
                th.start(); th.join(); t
            }
            log("E1 priority $label 60s features: ${times.joinToString { "%.2f".format(it) }} s")
        }
    }

    @Test fun e2_decodeAhead_pipelining() {
        val files = (1..4).map { music(90.0, 44_100, 2, "d$it.wav") }
        extractor.extract(FloatArray(44_100 * 5) { (it % 100) / 100f }, 44_100, withFingerprint = false) // warm-up

        val (_, serial) = timed {
            for (f in files) {
                val (s, r) = decodeToMonoPcm(f.absolutePath)
                extractor.extract(s, r)
            }
        }
        val pool = Executors.newSingleThreadExecutor()
        val (_, pipelined) = timed {
            var next: Future<Pair<FloatArray, Int>>? = pool.submit<Pair<FloatArray, Int>> { decodeToMonoPcm(files[0].absolutePath) }
            for (i in files.indices) {
                val (s, r) = next!!.get()
                next = if (i + 1 < files.size) pool.submit<Pair<FloatArray, Int>> { decodeToMonoPcm(files[i + 1].absolutePath) } else null
                extractor.extract(s, r)
            }
        }
        pool.shutdown()
        log("E2 4x90s serial=%.2fs pipelined=%.2fs gain=%.1f%%".format(serial, pipelined, 100 * (serial - pipelined) / serial))
        assertTrue(serial > 0)
    }

    @Test fun e4_parallelExtraction_isSafe_bitIdentical_andScales() {
        // Different content per thread so shared-state leakage between concurrent extractions would show.
        val signals = (0 until 8).map { k ->
            FloatArray(45 * 44_100) { i ->
                val t = i / 44_100.0
                ((0.3 * sin(2 * PI * (200.0 + 37 * k) * t) + 0.2 * sin(2 * PI * (440.0 + 11 * k) * t)) *
                    (if ((i % (22_050 - k * 900)) < 400) 1.0 else 0.4)).toFloat()
            }
        }
        extractor.setSerialExtraction(true)
        val refs = signals.map { extractor.extract(it, 44_100, withFingerprint = true) }
        extractor.setSerialExtraction(false)
        try {
            var base = 0.0
            for (n in listOf(1, 2, 3, 4, 6, 8)) {
                val jobs = (0 until n).map { k -> k }
                val pool = Executors.newFixedThreadPool(n)
                val (results, wall) = timed {
                    jobs.map { k -> pool.submit<List<com.amit152116.riffle.dsp.AudioFeatures>> { (1..3).map { extractor.extract(signals[k], 44_100, withFingerprint = true) } } }
                        .map { it.get() }
                }
                pool.shutdown()
                var mismatches = 0
                results.forEachIndexed { k, list ->
                    list.forEach { r ->
                        val ref = refs[k]
                        if (r.bpm != ref.bpm || r.loudnessLufs != ref.loudnessLufs || r.energy != ref.energy || r.keyName != ref.keyName ||
                            !r.mfccMean.contentEquals(ref.mfccMean) || !r.fingerprint.contentEquals(ref.fingerprint) || r.dissonance != ref.dissonance
                        ) mismatches++
                    }
                }
                val throughput = n * 3 / wall
                if (n == 1) base = throughput
                log("E4 threads=$n wall=%.2fs throughput=%.2f extracts/s speedup=%.2fx mismatches=$mismatches".format(wall, throughput, throughput / base))
                assertTrue("concurrent extraction changed results (threads=$n)", mismatches == 0)
            }
        } finally {
            extractor.setSerialExtraction(true)
        }
    }

    /**
     * Regression for a heap-corruption abort: Essentia's global Logger queue is not thread-safe, and some inputs
     * (very short, silent, noisy, clipped, odd rates) make the rhythm network print warnings. Hammer concurrent
     * extraction with such inputs; the process must survive and every result must be finite.
     */
    @Test fun e5_concurrentExtraction_withWarningProneInputs_doesNotCrash() {
        val rnd = java.util.Random(7)
        val inputs = listOf(
            FloatArray(44_100 * 2),                                                    // silence
            FloatArray(44_100 * 3) { (rnd.nextGaussian() * 0.3).toFloat().coerceIn(-1f, 1f) }, // noise
            FloatArray(44_100 * 2) { if (it % 2 == 0) 1f else -1f },                 // clipped Nyquist
            FloatArray(22_050) { (0.5 * sin(2 * PI * 440 * it / 22_050)).toFloat() }, // 0.5 s tone
            FloatArray(44_100 * 6) { if (it % 22_050 < 200) 1f else 0f },            // sparse clicks
            FloatArray(48_000 * 4) { (0.4 * sin(2 * PI * 97 * it / 48_000)).toFloat() } // 48 kHz
        )
        val rates = listOf(44_100, 44_100, 44_100, 44_100, 44_100, 48_000)
        val pool = Executors.newFixedThreadPool(8)
        val failures = AtomicInteger()
        val jobs = (0 until 8).map { t ->
            pool.submit {
                repeat(40) { i ->
                    val k = (t + i) % inputs.size
                    try {
                        val r = extractor.extract(inputs[k], rates[k], withFingerprint = true)
                        if (!r.bpm.isFinite() || !r.energy.isFinite() || r.mfccMean.any { !it.isFinite() }) failures.incrementAndGet()
                    } catch (e: RuntimeException) {
                        // a clean Java exception for a degenerate input is acceptable; a crash is not
                    }
                }
            }
        }
        jobs.forEach { it.get() }
        pool.shutdown()
        assertTrue("non-finite results: ${failures.get()}", failures.get() == 0)
    }

    @Test fun e3_decodeCostAlone() {
        val f = music(120.0, 44_100, 2, "dc.wav")
        val chunk = FloatArray(1 shl 16)
        PcmStreamDecoder.open(f.absolutePath).use { while (it.read(chunk) >= 0) {} }
        val (_, t) = timed { PcmStreamDecoder.open(f.absolutePath).use { while (it.read(chunk) >= 0) {} } }
        log("E3 decode-only 120s stereo wav = %.2fs (%.4f s/s)".format(t, t / 120))
    }
}

package com.amit152116.riffle.analysis

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.dsp.FingerprintStream
import com.amit152116.riffle.dsp.PcmStreamDecoder
import com.amit152116.riffle.dsp.WavFixtures
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/** Prints the measured cost factors (logcat tag RiffleBench). Run manually on the target phone, warm and cool. */
@RunWith(AndroidJUnit4::class)
class AnalysisBenchmarkTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "bench").apply { mkdirs() }
    private val extractor = EssentiaExtractor()

    @After fun cleanUp() { dir.deleteRecursively() }

    private fun fixture(seconds: Double, rate: Int, ch: Int): File = File(dir, "b_${rate}_$ch.wav").also {
        WavFixtures.writePcm16Wav(it, seconds, rate, ch) { f ->
            val t = f.toDouble() / rate
            val body = (0.3 * sin(2 * PI * 220 * t) + 0.2 * sin(2 * PI * 440 * t * (1 + 0.01 * sin(t)))).toFloat()
            body * (if ((f % (rate / 2)) < 400) 1f else 0.4f)
        }
    }

    private inline fun secs(block: () -> Unit): Double {
        val t = System.nanoTime()
        block()
        return (System.nanoTime() - t) / 1e9
    }

    @Test fun measureFactors() {
        val seconds = 300.0
        val f = fixture(seconds, 44_100, 2)
        val chunk = FloatArray(1 shl 16)
        PcmStreamDecoder.open(f.absolutePath).use { while (it.read(chunk) >= 0) {} } // warm-up
        val decode = secs { PcmStreamDecoder.open(f.absolutePath).use { while (it.read(chunk) >= 0) {} } }
        val fingerprint = secs {
            PcmStreamDecoder.open(f.absolutePath).use { d ->
                FingerprintStream(extractor, d.sampleRate).use { s ->
                    while (true) {
                        val n = d.read(chunk)
                        if (n < 0) break
                        s.feed(chunk, n)
                    }
                    s.finish()
                }
            }
        }
        val window = FloatArray(45 * 44_100) { (0.3 * sin(2 * PI * 220.0 * it / 44_100)).toFloat() }
        val features = secs { extractor.extract(window, 44_100, withFingerprint = false) }
        val full = secs { extractor.extractFile(f.absolutePath) }

        val decodePerSec = decode / seconds
        val fingerprintPerSec = fingerprint / seconds
        val featurePerAnalysedSec = features / 45.0
        Log.i(
            "RiffleBench",
            "decode=%.4f s/s fingerprint(decode+chroma)=%.4f s/s features=%.4f s/analysed-s fullTrack300s=%.1f s"
                .format(decodePerSec, fingerprintPerSec, featurePerAnalysedSec, full)
        )
        Log.i(
            "RiffleBench",
            "suggested defaults (x1.5 safety): decodeSecondsPerAudioSecond=%.4f featureSecondsPerAnalysedSecond=%.4f"
                .format(fingerprintPerSec * 1.5, featurePerAnalysedSec * 1.5)
        )
        assertTrue(decode > 0 && features > 0)
    }
}

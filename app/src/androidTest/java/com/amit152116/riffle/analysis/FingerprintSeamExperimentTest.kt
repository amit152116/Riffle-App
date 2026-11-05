package com.amit152116.riffle.analysis

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.dsp.FingerprintStream
import com.amit152116.riffle.dsp.PcmStreamDecoder
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Manual measurement (Task 10 gate): is a segmented, stitched fingerprint close enough to a continuous one?
 * Run with `-Pandroid.testInstrumentationRunnerArguments.seamDir=<dir of real audio files on the device>`;
 * skipped otherwise. Results go to logcat tag RiffleSeam. Asserts nothing about accuracy.
 */
@RunWith(AndroidJUnit4::class)
class FingerprintSeamExperimentTest {
    private val extractor = EssentiaExtractor()
    private val segmenter = FingerprintSegmenter()

    private fun similarity(a: IntArray, b: IntArray, from: Int, to: Int, shift: Int = 0): Double {
        var same = 0L; var n = 0L
        for (i in from until to) {
            val j = i + shift
            if (i !in a.indices || j !in b.indices) continue
            same += 32 - Integer.bitCount(a[i] xor b[j]); n += 32
        }
        return if (n == 0L) Double.NaN else same.toDouble() / n
    }

    private fun fingerprintRange(path: String, startSec: Double, endSec: Double): IntArray =
        PcmStreamDecoder.open(path).use { d ->
            val rate = d.sampleRate
            val skip = (startSec * rate).toLong()
            val stop = (endSec * rate).toLong()
            FingerprintStream(extractor, rate).use { s ->
                val buf = FloatArray(1 shl 15)
                var pos = 0L
                while (pos < stop) {
                    val n = d.read(buf)
                    if (n < 0) break
                    val from = maxOf(0L, skip - pos).toInt()
                    val to = minOf(n.toLong(), stop - pos).toInt()
                    if (to > from) s.feed(buf.copyOfRange(from, to), to - from)
                    pos += n
                }
                s.finish()
            }
        }

    @Test fun measureSeams() {
        val dirPath = InstrumentationRegistry.getArguments().getString("seamDir")
        assumeTrue("pass -Pandroid.testInstrumentationRunnerArguments.seamDir=<dir> to run", dirPath != null)
        val files = File(dirPath!!).listFiles { f -> f.isFile }.orEmpty().sortedBy { it.name }.take(5)
        assumeTrue("no files in $dirPath", files.isNotEmpty())
        for (f in files) {
            val duration = PcmStreamDecoder.open(f.absolutePath).use { it.durationSec } ?: continue
            val count = segmenter.segmentCount(duration)
            val full = fingerprintRange(f.absolutePath, 0.0, Double.MAX_VALUE / 4)
            val parts = (0 until count).map { i ->
                val r = segmenter.feedRange(i)
                fingerprintRange(f.absolutePath, r.startSec, r.endSec)
            }
            val stitched = segmenter.stitch(parts)
            val common = minOf(full.size, stitched.size)
            val overall = similarity(full, stitched, 0, common)
            val seamItems = (1 until count).map { segmenter.segmentHops * it }
            val seamLocal = seamItems.map { c -> similarity(full, stitched, c - 40, c + 40) }
            val shifts = seamItems.map { c -> (-3..3).maxByOrNull { sh -> similarity(full, stitched, c - 40, c + 40, sh) } }
            Log.i(
                "RiffleSeam",
                "${f.name}: duration=%.0fs segments=$count full=${full.size} stitched=${stitched.size} overall=%.4f seamLocal=$seamLocal bestShift=$shifts"
                    .format(duration, overall)
            )
            assertTrue("length differs by ${full.size - stitched.size}", kotlin.math.abs(full.size - stitched.size) <= count)
        }
    }
}

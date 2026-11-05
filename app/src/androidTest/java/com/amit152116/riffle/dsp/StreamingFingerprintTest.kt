package com.amit152116.riffle.dsp

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Chromaprint TEST2 emits ~8.0766 items/s minus a ~22-item filter delay (measured: 60 s -> 463 items). */
internal fun expectedFingerprintItems(seconds: Double) = seconds * 8.0766 - 22
internal fun fingerprintItemTolerance(seconds: Double) = maxOf(5.0, expectedFingerprintItems(seconds) * 0.005)

@RunWith(AndroidJUnit4::class)
class StreamingFingerprintTest {
    private val extractor = EssentiaExtractor()

    /** Deterministic non-trivial signal: three drifting tones plus a slow amplitude beat. */
    private fun signal(seconds: Double, rate: Int): FloatArray = FloatArray((seconds * rate).toInt()) { i ->
        val t = i.toDouble() / rate
        (0.3 * sin(2 * PI * (220 + 30 * sin(t * 0.7)) * t) +
            0.2 * sin(2 * PI * 440.0 * t) * (0.6 + 0.4 * sin(2 * PI * 2.0 * t)) +
            0.1 * sin(2 * PI * 1250.0 * t)).toFloat()
    }

    private fun streamed(samples: FloatArray, rate: Int, chunkMax: Int, seed: Int = 3): IntArray {
        val rng = Random(seed)
        FingerprintStream(extractor, rate).use { s ->
            var pos = 0
            while (pos < samples.size) {
                val n = minOf(rng.nextInt(1, chunkMax + 1), samples.size - pos)
                s.feed(samples.copyOfRange(pos, pos + n), n)
                pos += n
            }
            return s.finish()
        }
    }

    private fun bitSimilarity(a: IntArray, b: IntArray): Double {
        val n = minOf(a.size, b.size)
        var same = 0L
        for (i in 0 until n) same += 32 - Integer.bitCount(a[i] xor b[i])
        return same.toDouble() / (32L * n)
    }

    @Test fun at44100_streamedFingerprintEqualsBatchExactly_forAnyChunking() {
        val x = signal(40.0, 44_100)
        val batch = extractor.extract(x, 44_100).fingerprint
        assertTrue(batch.isNotEmpty())
        for (chunkMax in listOf(1_000, 4_096, 44_100, 500_000)) {
            assertArrayEquals("chunkMax=$chunkMax", batch, streamed(x, 44_100, chunkMax))
        }
    }

    @Test fun at48000_streamedFingerprintIsChunkingIndependent_andCloseToBatch() {
        val x = signal(40.0, 48_000)
        val a = streamed(x, 48_000, 3_000, seed = 1)
        val b = streamed(x, 48_000, 70_000, seed = 2)
        assertArrayEquals(a, b)
        val batch = extractor.extract(x, 48_000).fingerprint
        assertTrue("lengths ${a.size} vs ${batch.size}", kotlin.math.abs(a.size - batch.size) <= 3)
        assertTrue("similarity ${bitSimilarity(a, batch)}", bitSimilarity(a, batch) > 0.95)
    }

    @Test fun at22050Mono_lengthMatchesDuration() {
        val x = signal(60.0, 22_050)
        val fp = streamed(x, 22_050, 10_000)
        assertEquals(expectedFingerprintItems(60.0), fp.size.toDouble(), fingerprintItemTolerance(60.0))
    }

    @Test fun differentAudio_givesADifferentFingerprint() {
        val a = streamed(signal(30.0, 44_100), 44_100, 8_000)
        val b = streamed(FloatArray(30 * 44_100) { i -> (0.3 * sin(2 * PI * 777.0 * i / 44_100)).toFloat() }, 44_100, 8_000)
        assertTrue(bitSimilarity(a, b) < 0.9)
    }

    @Test fun featuresOnly_skipsTheFingerprint_andKeepsEveryOtherFeatureEqual() {
        val x = signal(30.0, 44_100)
        val full = extractor.extract(x, 44_100)
        val lean = extractor.extract(x, 44_100, withFingerprint = false)
        assertEquals(0, lean.fingerprint.size); assertEquals(-1, lean.fingerprintAlgorithm)
        assertEquals(full.bpm, lean.bpm, 0f); assertEquals(full.loudnessLufs, lean.loudnessLufs, 0f)
        assertArrayEquals(full.mfccMean, lean.mfccMean, 0f)
        assertEquals(2, full.fingerprintAlgorithm)
    }

    @Test fun emptyStream_finishesWithAnEmptyFingerprint_andDoesNotCrash() {
        FingerprintStream(extractor, 44_100).use { assertEquals(0, it.finish().size) }
    }

    @Test fun closeIsIdempotent_abortsAnUnfinishedStream_andUseAfterFinishFails() {
        val s = FingerprintStream(extractor, 44_100)
        s.feed(FloatArray(1000))
        s.close(); s.close()
        assertThrows(IllegalStateException::class.java) { s.feed(FloatArray(10)) }
        val t = FingerprintStream(extractor, 44_100)
        t.finish()
        assertThrows(IllegalStateException::class.java) { t.feed(FloatArray(10)) }
        assertThrows(IllegalStateException::class.java) { t.finish() }
    }

    @Test fun manyOpenAndAbortCycles_doNotLeakOrCrash() {
        repeat(200) { FingerprintStream(extractor, 48_000).use { it.feed(FloatArray(4_800)) } }
    }

    @Test fun invalidSampleRate_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { FingerprintStream(extractor, 0) }
        assertThrows(IllegalArgumentException::class.java) { FingerprintStream(extractor, -44_100) }
    }

    @Test fun countLargerThanTheArray_isRejected() {
        FingerprintStream(extractor, 44_100).use {
            assertThrows(IllegalArgumentException::class.java) { it.feed(FloatArray(10), 11) }
        }
    }
}

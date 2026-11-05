package com.amit152116.riffle.dsp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class PcmStreamDecoderTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pcm-stream-test").apply { mkdirs() }

    @After fun cleanUp() { dir.deleteRecursively() }

    private fun wav(name: String, seconds: Double, rate: Int, channels: Int): File =
        File(dir, name).also { WavFixtures.writePcm16Wav(it, seconds, rate, channels) { f -> (0.5 * sin(2 * PI * 330.0 * f / rate)).toFloat() } }

    private fun readAll(d: PcmStreamDecoder, chunk: Int): FloatArray {
        val out = ArrayList<FloatArray>(); val buf = FloatArray(chunk); var total = 0
        while (true) {
            val n = d.read(buf)
            if (n < 0) break
            assertTrue("read returned 0 for a non-empty buffer", n > 0)
            out += buf.copyOf(n); total += n
        }
        val all = FloatArray(total); var p = 0
        out.forEach { System.arraycopy(it, 0, all, p, it.size); p += it.size }
        return all
    }

    @Test fun streamedSamples_equalTheWholeTrackDecode_forAnyBufferSize() {
        for ((rate, ch) in listOf(44_100 to 2, 22_050 to 1, 48_000 to 2)) {
            val f = wav("t_${rate}_$ch.wav", 6.0, rate, ch)
            val (whole, wholeRate) = decodeToMonoPcm(f.absolutePath)
            for (chunk in listOf(1, 999, 4_096, 65_536)) {
                PcmStreamDecoder.open(f.absolutePath).use { d ->
                    assertEquals(rate, d.sampleRate)
                    assertEquals(wholeRate, d.sampleRate)
                    assertArrayEquals("rate=$rate ch=$ch chunk=$chunk", whole, readAll(d, chunk), 0f)
                }
            }
        }
    }

    @Test fun duration_isReported() {
        val f = wav("dur.wav", 12.0, 44_100, 2)
        PcmStreamDecoder.open(f.absolutePath).use { assertEquals(12.0, it.durationSec!!, 0.1) }
    }

    @Test fun readAfterEndOfStream_keepsReturningMinusOne() {
        val f = wav("eos.wav", 1.0, 44_100, 1)
        PcmStreamDecoder.open(f.absolutePath).use { d ->
            readAll(d, 8_192)
            assertEquals(-1, d.read(FloatArray(10))); assertEquals(-1, d.read(FloatArray(10)))
        }
    }

    @Test fun closeIsIdempotent_andReadAfterCloseFails() {
        val d = PcmStreamDecoder.open(wav("c.wav", 1.0, 44_100, 1).absolutePath)
        d.close(); d.close()
        assertThrows(IllegalStateException::class.java) { d.read(FloatArray(10)) }
    }

    @Test fun missingOrNonAudioFile_failsCleanly() {
        assertThrows(Exception::class.java) { PcmStreamDecoder.open(File(dir, "nope.wav").absolutePath) }
        val junk = File(dir, "junk.wav").apply { writeBytes(ByteArray(2_000) { 7 }) }
        assertThrows(Exception::class.java) { PcmStreamDecoder.open(junk.absolutePath) }
    }

    @Test fun emptyDestination_readsNothing() {
        PcmStreamDecoder.open(wav("e.wav", 1.0, 44_100, 1).absolutePath).use { assertEquals(0, it.read(FloatArray(0))) }
    }

    @Test fun decodeAllMono_matchesTheStreamAndHonoursTheHardCap() {
        val f = wav("cap.wav", 5.0, 22_050, 1)
        PcmStreamDecoder.open(f.absolutePath).use { d ->
            val (samples, rate) = decodeAllMono(d, maxSeconds = 900.0)
            assertEquals(22_050, rate); assertEquals(5 * 22_050, samples.size)
        }
        PcmStreamDecoder.open(f.absolutePath).use { d ->
            assertThrows(IllegalStateException::class.java) { decodeAllMono(d, maxSeconds = 2.0) }
        }
    }
}

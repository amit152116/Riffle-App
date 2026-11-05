package com.amit152116.riffle.dsp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val SAMPLE_RATE = 44100

/** Short percussive "click" burst: a decaying 2kHz sine, like a woodblock hit. */
private fun click(durationSamples: Int, sampleRate: Int): FloatArray = FloatArray(durationSamples) { i ->
    val t = i.toFloat() / sampleRate
    (sin(2.0 * PI * 2000.0 * t) * exp(-t * 60.0)).toFloat()
}

/** A click track at [bpm], repeated for [seconds]. Real percussive onsets, unlike a mock. */
private fun clickTrackAt(bpm: Double, seconds: Double, sampleRate: Int): FloatArray {
    val totalSamples = (seconds * sampleRate).toInt()
    val audio = FloatArray(totalSamples)
    val intervalSamples = (60.0 / bpm * sampleRate).toInt()
    val clickBurst = click(min(2000, intervalSamples / 2), sampleRate)
    var pos = 0
    while (pos < totalSamples) {
        for (i in clickBurst.indices) {
            if (pos + i >= totalSamples) break
            audio[pos + i] = clickBurst[i]
        }
        pos += intervalSamples
    }
    return audio
}

private fun clickTrack(bpm: Double, seconds: Double): FloatArray =
    clickTrackAt(bpm, seconds, SAMPLE_RATE)

private fun min(a: Int, b: Int) = if (a < b) a else b

private fun sineWaveAt(freqHz: Double, seconds: Double, sampleRate: Int): FloatArray {
    val totalSamples = (seconds * sampleRate).toInt()
    return FloatArray(totalSamples) { i ->
        sin(2.0 * PI * freqHz * i / sampleRate).toFloat()
    }
}

private fun sineWave(freqHz: Double, seconds: Double): FloatArray = sineWaveAt(freqHz, seconds, SAMPLE_RATE)

/** Writes 16-bit PCM stereo `samples` (mono, duplicated to both channels) as a WAV file. */
private fun writeStereoClickTrackWav(file: File, monoSamples: FloatArray, sampleRate: Int) {
    val channels = 2
    val bytesPerSample = 2
    val dataSize = monoSamples.size * channels * bytesPerSample
    RandomAccessFile(file, "rw").use { raf ->
        raf.setLength(0)
        fun writeIntLe(v: Int) {
            raf.write(v and 0xFF)
            raf.write((v shr 8) and 0xFF)
            raf.write((v shr 16) and 0xFF)
            raf.write((v shr 24) and 0xFF)
        }
        fun writeShortLe(v: Int) {
            raf.write(v and 0xFF)
            raf.write((v shr 8) and 0xFF)
        }
        raf.writeBytes("RIFF")
        writeIntLe(36 + dataSize)
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        writeIntLe(16) // fmt chunk size
        writeShortLe(1) // PCM
        writeShortLe(channels)
        writeIntLe(sampleRate)
        writeIntLe(sampleRate * channels * bytesPerSample) // byte rate
        writeShortLe(channels * bytesPerSample) // block align
        writeShortLe(16) // bits per sample
        raf.writeBytes("data")
        writeIntLe(dataSize)
        for (s in monoSamples) {
            val clamped = (s.coerceIn(-1f, 1f) * 32767).toInt()
            writeShortLe(clamped)
            writeShortLe(clamped)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class EssentiaExtractorTest {

    @Test
    fun extractFile_decodesRealWavFile_detectsApproximateBpm() {
        // Exercises the real entry point: MediaExtractor/MediaCodec decode, stereo->mono
        // downmix, and the native speex resampler (44.1kHz test above never touches any of
        // that, since resampling is a no-op when fromRate == toRate). 32kHz input
        // specifically: if the resample step were silently skipped, RhythmExtractor2013
        // would read this ~100bpm track as ~137.8bpm (32000/44100 * 100 inverted) and fail
        // the bounds below.
        val wavSampleRate = 32000
        val monoAudio = clickTrackAt(bpm = 100.0, seconds = 8.0, sampleRate = wavSampleRate)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wavFile = File(context.cacheDir, "essentia_test_click_track.wav")
        writeStereoClickTrackWav(wavFile, monoAudio, wavSampleRate)

        val extractor = EssentiaExtractor()
        val result = extractor.extractFile(wavFile.absolutePath)

        assertTrue("BPM should be roughly 100 (was ${result.bpm})", result.bpm in 90.0f..110.0f)
        assertEquals("MFCC array should have 13 bands", 13, result.mfccMean.size)
        assertTrue(
            "Duration should match input length (was ${result.duration})",
            result.duration in 7.5f..8.5f
        )

        wavFile.delete()
    }

    @Test
    fun extractFile_longTrack_completesWithoutCrashing() {
        // Exercises the FIR anti-alias filter and decode buffers at a size where naive
        // implementations show their memory/time cost, without the multi-minute wall time
        // of a full 5-10 minute track (device/CI time budget).
        val wavSampleRate = 48000
        val seconds = 15.0
        val monoAudio = clickTrackAt(bpm = 120.0, seconds = seconds, sampleRate = wavSampleRate)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wavFile = File(context.cacheDir, "essentia_test_long_track.wav")
        writeStereoClickTrackWav(wavFile, monoAudio, wavSampleRate)

        val extractor = EssentiaExtractor()
        val elapsedMs = try {
            val start = System.nanoTime()
            val result = extractor.extractFile(wavFile.absolutePath)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            assertTrue(
                "Duration should match input length (was ${result.duration})",
                result.duration in (seconds - 1).toFloat()..(seconds + 1).toFloat()
            )
            assertTrue("Fingerprint should be non-empty on a real track", result.fingerprint.isNotEmpty())
            elapsed
        } finally {
            wavFile.delete()
        }

        // Not a strict perf assertion (device-dependent) -- this is here so the wall time is
        // visible in the test report rather than silently unmeasured, per this task's report.
        assertTrue("A 15s track took ${elapsedMs}ms; investigate if this grows unreasonably", elapsedMs < 60_000)
    }

    @Test
    fun clickTrack_detectsApproximateBpm() {
        val extractor = EssentiaExtractor()
        val audio = clickTrack(bpm = 100.0, seconds = 8.0)

        val result = extractor.extract(audio, SAMPLE_RATE)

        // A real synthetic click track, not a hardcoded mock value: this fails if the
        // Essentia pipeline (or the KISS-FFT rebuild) regresses.
        assertTrue("BPM should be roughly 100 (was ${result.bpm})", result.bpm in 90.0f..110.0f)
        assertTrue("BPM confidence should be non-negative", result.bpmConfidence >= 0f)
        assertEquals("MFCC array should have 13 bands", 13, result.mfccMean.size)
        assertEquals("Duration should match input length", 8.0f, result.duration, 0.1f)
    }

    @Test
    fun sineWave_producesSaneSpectralFeatures() {
        val extractor = EssentiaExtractor()
        // 220Hz (A3) rather than a C-major-biased signal, so this can't accidentally
        // match old mock defaults (120bpm/C major).
        val audio = sineWave(freqHz = 220.0, seconds = 3.0)

        val result = extractor.extract(audio, SAMPLE_RATE)

        assertTrue("Zero-crossing rate should be > 0 for a tone", result.zcr > 0f)
        assertTrue(
            "Spectral centroid should be in a sane audible range (was ${result.spectralCentroid})",
            result.spectralCentroid in 0f..(SAMPLE_RATE / 2f)
        )
        assertTrue("Dissonance should be within [0, 1]", result.dissonance in 0f..1f)
        assertTrue("Key strength should be within [0, 1]", result.keyStrength in 0f..1f)
        assertTrue("Scale should be reported as major or minor", result.scale == "major" || result.scale == "minor")
        assertTrue("Chromaprint fingerprint should be non-empty", result.fingerprint.isNotEmpty())
        assertEquals(
            "fingerprintAlgorithm should match ../riffle/riffle/fingerprint.py's stored CLI algorithm number",
            2, result.fingerprintAlgorithm
        )
    }

    @Test
    fun configHash_matchesRiffleExtractionConfigHash() {
        // Computed independently in Python from ../riffle/riffle/features.py's own
        // EXTRACTION_CONFIG + extraction_config_hash(): sha256(json.dumps({"sample_rate":
        // 44100,"frame_size":2048,"hop_size":1024,"window_type":"hann","mfcc_bands":13,
        // "rhythm_method":"multifeature"}, sort_keys=True, separators=(",",":"))). Not just
        // "some hash" -- this is proof our config JSON shape and values are byte-identical
        // to riffle's, not merely each field individually matching.
        val expectedHash = "a4f8c745ae78bc8996b15514476df71ee8ff50888aa586dc3f135848fb14e80c"

        val extractor = EssentiaExtractor()
        val a = extractor.extract(sineWave(freqHz = 220.0, seconds = 1.0), SAMPLE_RATE)
        val b = extractor.extract(clickTrack(bpm = 100.0, seconds = 1.0), SAMPLE_RATE)

        assertEquals("configHash should match riffle's extraction_config_hash()", expectedHash, a.configHash)
        assertEquals("configHash is about the config, not the audio -- must be stable across calls", a.configHash, b.configHash)
    }

    @Test
    fun configHash_isCallableWithoutAudioAndMatchesExtractionResult() {
        val expectedHash = "a4f8c745ae78bc8996b15514476df71ee8ff50888aa586dc3f135848fb14e80c"
        val extractor = EssentiaExtractor()

        val standalone = extractor.configHash()
        val fromExtraction = extractor.extract(sineWave(freqHz = 220.0, seconds = 1.0), SAMPLE_RATE).configHash

        assertEquals("configHash() must match riffle's extraction_config_hash()", expectedHash, standalone)
        assertEquals("standalone configHash() must equal the value attached to a real extraction", fromExtraction, standalone)
    }

    @Test
    fun sameAudio_producesIdenticalFingerprint_differentAudio_doesNot() {
        // Chromaprint's own contract (used for dedup in Subsystem A): identical audio must
        // hash identically, and distinct audio must not collide trivially.
        // Two pure sine tones a clean octave apart are too degenerate a case for this (chroma
        // is close to octave-invariant); a tone vs. a percussive click track is not.
        val extractor = EssentiaExtractor()
        val a1 = extractor.extract(sineWave(freqHz = 220.0, seconds = 3.0), SAMPLE_RATE)
        val a2 = extractor.extract(sineWave(freqHz = 220.0, seconds = 3.0), SAMPLE_RATE)
        val b = extractor.extract(clickTrack(bpm = 100.0, seconds = 3.0), SAMPLE_RATE)

        assertTrue("Fingerprint of identical audio should match", a1.fingerprint.contentEquals(a2.fingerprint))
        assertTrue(
            "Fingerprint of different audio should not match",
            !a1.fingerprint.contentEquals(b.fingerprint)
        )
    }

    @Test
    fun resample_downsampling_attenuatesContentAboveNewNyquist() {
        // Exercises the real native path (speex, see speex_resampler/README.md), not a
        // reimplementation of it. Resampling always targets 44100 (Essentia's fixed rate),
        // so we resample DOWN INTO 44100 from double that rate: new Nyquist = 22050Hz.
        val extractor = EssentiaExtractor()
        val fromRate = 88200
        val toRate = EssentiaExtractor.TARGET_SAMPLE_RATE
        val seconds = 1.0

        // Below the new Nyquist: should pass through close to full amplitude.
        val below = sineWaveAt(freqHz = 8000.0, seconds = seconds, sampleRate = fromRate)
        // Above the new Nyquist: without an anti-alias filter this folds back into the
        // passband as a spurious low-frequency signal instead of being attenuated.
        val above = sineWaveAt(freqHz = 30000.0, seconds = seconds, sampleRate = fromRate)

        val belowResampled = extractor.nativeResampleForTest(below, fromRate, toRate)
        val aboveResampled = extractor.nativeResampleForTest(above, fromRate, toRate)

        fun rms(x: FloatArray) = kotlin.math.sqrt(x.map { it * it }.average()).toFloat()

        val belowRms = rms(belowResampled)
        val aboveRms = rms(aboveResampled)

        assertTrue("An 8kHz tone should survive resampling to 44.1kHz mostly intact (rms=$belowRms)", belowRms > 0.5f)
        assertTrue(
            "A 30kHz tone (above the new 22.05kHz Nyquist) should be attenuated by the anti-alias " +
                "filter, not aliased through near full amplitude (below=$belowRms, above=$aboveRms)",
            aboveRms < belowRms * 0.3f
        )
    }
}

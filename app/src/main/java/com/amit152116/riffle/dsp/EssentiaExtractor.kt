package com.amit152116.riffle.dsp

import android.content.Context
import android.net.Uri

/**
 * Data class representing the audio features extracted by Essentia,
 * mirroring the Python `features.py` implementation.
 */
data class AudioFeatures(
    val bpm: Float,
    val bpmConfidence: Float,
    val keyName: String,
    val scale: String,
    val keyStrength: Float,
    val loudnessLufs: Float,
    val danceability: Float,
    val energy: Float,
    val spectralCentroid: Float,
    val onsetRate: Float,
    val dynamicComplexity: Float,
    val dissonance: Float,
    val zcr: Float,
    val mfccMean: FloatArray, // 13-band MFCC mean
    val duration: Float, // Required for fingerprinting context
    val fingerprint: IntArray, // Raw chromaprint values (libchromaprint, algorithm 2 == fpcalc's CLI numbering)
    val fingerprintAlgorithm: Int, // 2 = computed (matches ../riffle/riffle/fingerprint.py's stored value); -1 = fingerprinting failed for this audio
    /**
     * SHA-256 of the extraction config (frame/hop size, window, MFCC bands, rhythm method),
     * matching ../riffle/riffle/features.py::extraction_config_hash() byte for byte — same
     * JSON shape, same hash. Compare this across rows to detect "extraction config changed,
     * should re-analyze," the same way riffle does; not tied to a specific audio file's
     * content, so it's identical across every AudioFeatures returned by this build.
     */
    val configHash: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioFeatures
        return mfccMean.contentEquals(other.mfccMean) && fingerprint.contentEquals(other.fingerprint)
    }

    override fun hashCode(): Int {
        var result = mfccMean.contentHashCode()
        result = 31 * result + fingerprint.contentHashCode()
        return result
    }
}

class EssentiaExtractor {
    companion object {
        /** Sample rate Essentia's algorithms (and ../riffle/riffle/features.py) are tuned for. */
        const val TARGET_SAMPLE_RATE = 44100

        /** Bumped manually whenever the native build changes (algorithm set, config). */
        const val EXTRACTOR_VERSION = "essentia-lightweight-1"
        const val ANALYZER = "chromaprint"
        const val FINGERPRINT_CLI_ALGORITHM = 2

        init {
            System.loadLibrary("riffle_dsp")
        }
    }

    private external fun nativeExtract(samples: FloatArray, sampleRate: Int, withFingerprint: Boolean): AudioFeatures

    private external fun nativeFingerprintBegin(sampleRate: Int): Long
    private external fun nativeFingerprintFeed(handle: Long, samples: FloatArray, count: Int)
    private external fun nativeFingerprintFinish(handle: Long): IntArray
    private external fun nativeFingerprintAbort(handle: Long)

    internal fun fingerprintBegin(sampleRate: Int): Long = nativeFingerprintBegin(sampleRate)
    internal fun fingerprintFeed(handle: Long, samples: FloatArray, count: Int) = nativeFingerprintFeed(handle, samples, count)
    internal fun fingerprintFinish(handle: Long): IntArray = nativeFingerprintFinish(handle)
    internal fun fingerprintAbort(handle: Long) = nativeFingerprintAbort(handle)

    private external fun nativeConfigHash(): String
    private external fun nativeLastStageTimings(): String
    private external fun nativeSetSerialExtraction(serial: Boolean)

    /** Experiment hook: false lets several extract() calls run concurrently (only factory creation stays locked). */
    internal fun setSerialExtraction(serial: Boolean) = nativeSetSerialExtraction(serial)

    /** Per-stage timings of this thread's last extract() (diagnostics/benchmarks). */
    fun lastStageTimings(): String = nativeLastStageTimings()

    /**
     * Test-only: exposes the native speex resampler directly (see speex_resampler/README.md).
     * `@JvmName` avoids Kotlin's internal-function name mangling (`...$app`), which would
     * otherwise mismatch the fixed `Java_...` JNI symbol name below.
     */
    @JvmName("nativeResampleForTest")
    internal external fun nativeResampleForTest(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray

    /**
     * Runs the Essentia feature-extraction pipeline over decoded mono PCM audio at any
     * [sampleRate]: native code resamples to [TARGET_SAMPLE_RATE] itself (via speex, see
     * speex_resampler/README.md) before running any algorithm, since RhythmExtractor2013 and
     * most others assume 44100 internally and have no sampleRate parameter at all, mirroring
     * ../riffle/riffle/features.py's fixed config. `samples` must already be mono, normalized
     * to [-1, 1].
     */
    fun extract(samples: FloatArray, sampleRate: Int, withFingerprint: Boolean = true): AudioFeatures =
        nativeExtract(samples, sampleRate, withFingerprint)

    /**
     * The current native build's extraction config hash, without decoding any audio -
     * cheap and deterministic (see EssentiaWrapper.cpp's extractionConfigHash(), a
     * memoized SHA-256 of a fixed config struct). Callers needing "has this content
     * already been analyzed under the current config" must call this rather than
     * running a throwaway extraction just to read configHash off the result.
     */
    fun configHash(): String = nativeConfigHash()

    /**
     * Decodes the given file with Android's MediaExtractor/MediaCodec (no ffmpeg dependency)
     * and downmixes to mono, then extracts audio features (resampling happens in native code,
     * see [extract]). Ensure the app has read permissions for the file.
     *
     * Takes a plain filesystem path, not a `content://` URI — MediaExtractor's
     * [MediaExtractor.setDataSource] string overload only resolves local paths and
     * http/https/rtsp URLs. Library tracks are always `content://` (see [Track.path][
     * com.amit152116.riffle.data.db.library.Track]); use [extractContent] for those,
     * the same way [com.amit152116.riffle.data.db.library.AudioHasher] uses the
     * context+Uri overload instead of this one. This overload stays for the WAV
     * fixtures in `EssentiaExtractorTest`, which write real files to local storage.
     */
    fun extractFile(path: String): AudioFeatures {
        val (samples, sampleRate) = decodeToMonoPcm(path)
        return extract(samples, sampleRate)
    }

    /**
     * Same as [extractFile] but for a MediaStore `content://` [uri], via the
     * context-aware [MediaExtractor.setDataSource] overload — see [extractFile]'s kdoc
     * for why the plain-path overload doesn't work for these.
     */
    fun extractContent(context: Context, uri: Uri): AudioFeatures {
        val (samples, sampleRate) = decodeToMonoPcm(context, uri)
        return extract(samples, sampleRate)
    }
}

/**
 * Decodes an audio file to mono float PCM at the codec's own sample rate (see [PcmStreamDecoder]).
 * Resampling to Essentia's target rate happens in native code (see EssentiaWrapper.cpp), not here.
 */
internal fun decodeToMonoPcm(path: String): Pair<FloatArray, Int> =
    PcmStreamDecoder.open(path).use { decodeAllMono(it) }

/** [uri] variant of [decodeToMonoPcm] — see [EssentiaExtractor.extractContent]'s kdoc. */
internal fun decodeToMonoPcm(context: Context, uri: Uri): Pair<FloatArray, Int> =
    PcmStreamDecoder.open(context, uri).use { decodeAllMono(it) }

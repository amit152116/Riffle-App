package com.amit152116.riffle.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

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

        init {
            System.loadLibrary("riffle_dsp")
        }
    }

    private external fun nativeExtract(samples: FloatArray, sampleRate: Int): AudioFeatures

    private external fun nativeConfigHash(): String

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
    fun extract(samples: FloatArray, sampleRate: Int): AudioFeatures = nativeExtract(samples, sampleRate)

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
 * Decodes an audio file to mono float PCM using platform decoders, at whatever sample rate
 * the codec reports. Replaces Essentia's MonoLoader, which needs ffmpeg (unavailable on
 * Android) — see AGENTS.md's "hard, unresolved part" note on audio decode. Resampling to
 * Essentia's target rate happens in native code (see EssentiaWrapper.cpp), not here.
 */
internal fun decodeToMonoPcm(path: String): Pair<FloatArray, Int> {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(path)
    } catch (e: Exception) {
        extractor.release()
        throw e
    }
    return decodeToMonoPcm(extractor)
}

/** [uri] variant of [decodeToMonoPcm] — see [EssentiaExtractor.extractContent]'s kdoc. */
internal fun decodeToMonoPcm(context: Context, uri: Uri): Pair<FloatArray, Int> {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
    } catch (e: Exception) {
        extractor.release()
        throw e
    }
    return decodeToMonoPcm(extractor)
}

/**
 * Drains an already-configured [extractor] (data source already set by either
 * overload above) to mono float PCM. Always releases [extractor], including on
 * every failure path below.
 */
private fun decodeToMonoPcm(extractor: MediaExtractor): Pair<FloatArray, Int> {
    try {
        var trackIndex = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                inputFormat = format
                break
            }
        }
        require(trackIndex >= 0 && inputFormat != null) { "No audio track found" }
        extractor.selectTrack(trackIndex)

        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            // Real values come from INFO_OUTPUT_FORMAT_CHANGED, not the input format:
            // formats like HE-AAC report half the true sample rate on the input side.
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount =
                if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                    inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                else 1

            // Grows in place instead of collecting a ShortArray per output buffer plus a
            // separate mono array plus copyOf(): a 10-minute 48kHz stereo track would
            // otherwise hold ~450MB across those redundant copies at once.
            var mono = FloatArray(1 shl 16)
            var monoSize = 0
            fun ensureCapacity(extra: Int) {
                if (monoSize + extra > mono.size) {
                    mono = mono.copyOf(maxOf(mono.size * 2, monoSize + extra))
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            val encoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            require(encoding == android.media.AudioFormat.ENCODING_PCM_16BIT) {
                                "Unsupported decoder PCM encoding $encoding (only 16-bit PCM " +
                                    "is handled; reinterpreting other encodings as 16-bit " +
                                    "would silently corrupt the samples)"
                            }
                        }
                    }

                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // no output ready yet, loop again
                    }

                    outputBufferIndex >= 0 -> {
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuffer = outputBuffer.asShortBuffer()

                            val frames = shortBuffer.remaining() / channelCount
                            ensureCapacity(frames)
                            val frame = ShortArray(channelCount)
                            repeat(frames) {
                                shortBuffer.get(frame)
                                var sum = 0
                                for (c in 0 until channelCount) sum += frame[c]
                                mono[monoSize++] = (sum.toFloat() / channelCount) / 32768.0f
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }

            return mono.copyOf(monoSize) to sampleRate
        } finally {
            codec.stop()
            codec.release()
        }
    } finally {
        extractor.release()
    }
}

package com.amit152116.riffle.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Pull-based MediaCodec decode to mono float PCM at the codec's own sample rate. [read] fills at most
 * `dest.size` samples and returns the count, or -1 at the end of the stream. Memory is bounded by one
 * codec output buffer regardless of track length.
 */
class PcmStreamDecoder private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    inputFormat: MediaFormat
) : AutoCloseable {
    var sampleRate: Int = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        private set
    val durationSec: Double? =
        if (inputFormat.containsKey(MediaFormat.KEY_DURATION))
            inputFormat.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0 }?.let { it / 1_000_000.0 }
        else null

    private var channelCount =
        if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
    private val info = MediaCodec.BufferInfo()
    private var pending = FloatArray(1 shl 14) // decoded mono samples not yet handed out
    private var pendingPos = 0
    private var pendingEnd = 0
    private var sawInputEos = false
    private var sawOutputEos = false
    private var closed = false

    fun read(dest: FloatArray): Int {
        check(!closed) { "decoder is closed" }
        if (dest.isEmpty()) return 0
        while (pendingPos == pendingEnd) {
            if (sawOutputEos) return -1
            pump()
        }
        val n = minOf(dest.size, pendingEnd - pendingPos)
        System.arraycopy(pending, pendingPos, dest, 0, n)
        pendingPos += n
        return n
    }

    /** One round of feeding input and draining at most one output buffer into [pending]. */
    private fun pump() {
        if (!sawInputEos) {
            val i = codec.dequeueInputBuffer(10_000)
            if (i >= 0) {
                val buf = codec.getInputBuffer(i)!!
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) {
                    codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    sawInputEos = true
                } else {
                    codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        val o = codec.dequeueOutputBuffer(info, 10_000)
        when {
            o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val f = codec.outputFormat
                sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channelCount = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    val enc = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    require(enc == android.media.AudioFormat.ENCODING_PCM_16BIT) {
                        "Unsupported decoder PCM encoding $enc (only 16-bit PCM is handled; reinterpreting " +
                            "other encodings as 16-bit would silently corrupt the samples)"
                    }
                }
            }
            o == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
            o >= 0 -> {
                if (info.size > 0) {
                    val out = codec.getOutputBuffer(o)!!
                    out.order(ByteOrder.LITTLE_ENDIAN)
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    val shorts = out.asShortBuffer()
                    val frames = shorts.remaining() / channelCount
                    if (pending.size < frames) pending = FloatArray(frames)
                    val frame = ShortArray(channelCount)
                    for (k in 0 until frames) {
                        shorts.get(frame)
                        var sum = 0
                        for (c in 0 until channelCount) sum += frame[c]
                        pending[k] = (sum.toFloat() / channelCount) / 32768.0f
                    }
                    pendingPos = 0
                    pendingEnd = frames
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            codec.stop()
        } catch (_: IllegalStateException) {
        }
        codec.release()
        extractor.release()
    }

    companion object {
        fun open(path: String): PcmStreamDecoder = open { it.setDataSource(path) }

        fun open(context: Context, uri: Uri): PcmStreamDecoder = open { it.setDataSource(context, uri, null) }

        private fun open(setSource: (MediaExtractor) -> Unit): PcmStreamDecoder {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                setSource(extractor)
                var index = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { index = i; format = f; break }
                }
                require(index >= 0 && format != null) { "No audio track found" }
                extractor.selectTrack(index)
                codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
                codec.configure(format, null, null, 0)
                codec.start()
                return PcmStreamDecoder(extractor, codec, format)
            } catch (e: Throwable) {
                try { codec?.release() } catch (_: Throwable) {}
                extractor.release()
                throw e
            }
        }
    }
}

const val FULL_DECODE_MAX_SECONDS = 900.0

/**
 * Whole-track decode into one exactly-sized buffer: sized once from the container duration (+2 %
 * headroom) instead of doubling, so peak memory is one array, not two. Falls back to growth only if
 * the duration is missing or wrong, and fails cleanly beyond [maxSeconds] so an unknown-length file
 * cannot exhaust the heap.
 */
internal fun decodeAllMono(
    decoder: PcmStreamDecoder,
    maxSeconds: Double = FULL_DECODE_MAX_SECONDS,
    isCancelled: () -> Boolean = { false }
): Pair<FloatArray, Int> {
    val rate = decoder.sampleRate
    val maxSamples = (maxSeconds * rate).toLong().coerceAtMost(Int.MAX_VALUE.toLong() - 8).toInt()
    val expected = decoder.durationSec?.let { (it * rate * 1.02).toLong() + 8_192 }?.coerceIn(1L, maxSamples.toLong())?.toInt()
    var buf = FloatArray(expected ?: (1 shl 16))
    var size = 0
    val chunk = FloatArray(1 shl 15)
    while (true) {
        if (isCancelled()) throw java.util.concurrent.CancellationException("decode cancelled")
        val n = decoder.read(chunk)
        if (n < 0) break
        check(size.toLong() + n <= maxSamples) { "track exceeds the ${maxSeconds.toInt()} s whole-track decode limit" }
        if (size + n > buf.size) buf = buf.copyOf(minOf(maxOf(buf.size * 2L, size + n.toLong()), maxSamples.toLong()).toInt())
        System.arraycopy(chunk, 0, buf, size, n)
        size += n
    }
    return (if (size == buf.size) buf else buf.copyOf(size)) to decoder.sampleRate
}

package com.amit152116.riffle.data.db.library

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.amit152116.riffle.data.db.HashMethod
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

data class AudioIdentity(
    val audioHash: String,
    val hashMethod: HashMethod,
    // Read from the same MediaExtractor pass that computes the hash, at no extra
    // setDataSource() cost -- null only on the whole-file fallback path, where the
    // stream couldn't be demuxed at all (so there's no format to read anyway).
    val codec: String?,
    val sampleRate: Int?,
    val channels: Int?
)

/**
 * Mirrors ../riffle/riffle/hashing.py::audio_identity: identity is the hash of the
 * encoded audio stream, not the file, so tag edits don't change it. riffle gets this via
 * `ffmpeg -c:a copy -f streamhash` (demux without decode); there's no ffmpeg on Android, but
 * MediaExtractor.readSampleData() already hands back the same raw compressed bytes before
 * any decoder touches them, so hashing those as they're read is the same semantic via a
 * different implementation. Falls back to a whole-file hash (weaker: tag-edit-sensitive)
 * only if the stream can't be demuxed at all, same as riffle's fallback.
 */
object AudioHasher {
    private const val BUFFER_SIZE = 1 shl 20 // 1MB

    fun computeAudioIdentity(context: Context, uri: Uri): AudioIdentity {
        streamHash(context, uri)?.let { return it }
        return AudioIdentity(wholeFileHash(context, uri), HashMethod.WHOLE_FILE, null, null, null)
    }

    private fun streamHash(context: Context, uri: Uri): AudioIdentity? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)

            var audioTrack = -1
            var codec: String? = null
            var sampleRate: Int? = null
            var channels: Int? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    codec = mime
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    break
                }
            }
            if (audioTrack < 0) return null
            extractor.selectTrack(audioTrack)

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bytes = ByteArray(BUFFER_SIZE)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                buffer.rewind()
                buffer.get(bytes, 0, size)
                digest.update(bytes, 0, size)
                extractor.advance()
            }
            AudioIdentity(digest.digest().toHexString(), HashMethod.STREAMHASH, codec, sampleRate, channels)
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun wholeFileHash(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri for whole-file hashing")
        stream.use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}

package com.amit152116.riffle.dsp

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes a 16-bit PCM WAV in blocks (constant memory, so 25-minute fixtures are safe). */
object WavFixtures {
    fun writePcm16Wav(file: File, seconds: Double, sampleRate: Int, channels: Int, sample: (frame: Long) -> Float) {
        val frames = (seconds * sampleRate).toLong()
        val dataBytes = frames * channels * 2
        require(dataBytes + 36 < Int.MAX_VALUE) { "fixture too large for a 32-bit WAV" }
        BufferedOutputStream(FileOutputStream(file), 1 shl 16).use { out ->
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt((36 + dataBytes).toInt()).put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
            h.putInt(sampleRate).putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
            h.put("data".toByteArray()).putInt(dataBytes.toInt())
            out.write(h.array())
            val block = ByteBuffer.allocate(8192 * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
            var f = 0L
            while (f < frames) {
                block.clear()
                val n = minOf(8192L, frames - f).toInt()
                for (i in 0 until n) {
                    val v = (sample(f + i).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    repeat(channels) { block.putShort(v) }
                }
                out.write(block.array(), 0, n * channels * 2)
                f += n
            }
        }
    }
}

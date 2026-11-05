package com.amit152116.riffle.analysis

/**
 * Collects the samples of each [AnalysisWindow] out of a stream of arbitrary-sized chunks (one pass,
 * bounded memory: at most the windows currently open) and emits every window as soon as it is
 * complete. Windows may overlap; each has its own buffer. A window cut short by the end of the
 * stream is flushed by [finish] if it holds at least one second of audio; shorter ones are dropped.
 */
class WindowCollector(
    private val windows: List<AnalysisWindow>,
    private val onWindow: (index: Int, samples: FloatArray, sampleRate: Int) -> Unit
) {
    private var sampleRate = 0
    private var position = 0L // absolute index of the next incoming sample
    private val buffers = arrayOfNulls<FloatArray>(windows.size)
    private val filled = IntArray(windows.size)
    private val done = BooleanArray(windows.size)

    fun accept(chunk: FloatArray, count: Int, sampleRate: Int) {
        require(sampleRate > 0) { "sampleRate must be positive" }
        if (this.sampleRate == 0) this.sampleRate = sampleRate
        else check(this.sampleRate == sampleRate) { "sample rate changed mid-stream: ${this.sampleRate} -> $sampleRate" }
        val chunkStart = position
        val chunkEnd = position + count
        for (i in windows.indices) {
            if (done[i]) continue
            val wStart = (windows[i].startSec * sampleRate).toLong()
            val wEnd = (windows[i].endSec * sampleRate).toLong()
            if (chunkEnd <= wStart) continue
            val from = maxOf(chunkStart, wStart)
            val to = minOf(chunkEnd, wEnd)
            if (to > from) {
                val buf = buffers[i] ?: FloatArray((wEnd - wStart).toInt()).also { buffers[i] = it }
                val n = (to - from).toInt()
                System.arraycopy(chunk, (from - chunkStart).toInt(), buf, (from - wStart).toInt(), n)
                filled[i] += n
            }
            if (chunkEnd >= wEnd) emit(i)
        }
        position = chunkEnd
    }

    fun finish() {
        for (i in windows.indices) if (!done[i]) emit(i)
    }

    private fun emit(i: Int) {
        done[i] = true
        val buf = buffers[i]
        buffers[i] = null
        val n = filled[i]
        if (buf != null && sampleRate > 0 && n >= sampleRate) {
            onWindow(i, if (n == buf.size) buf else buf.copyOf(n), sampleRate)
        }
    }
}

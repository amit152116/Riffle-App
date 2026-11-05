package com.amit152116.riffle.dsp

/**
 * Streaming full-length Chromaprint fingerprint (native: speex to 44.1 kHz, then Chromaprint TEST2).
 * Bounded memory whatever the track length. Not thread-safe; owns a native handle, so always
 * [close] it (use `use {}`). [finish] returns the raw fingerprint and releases the handle.
 */
class FingerprintStream(private val extractor: EssentiaExtractor, sampleRate: Int) : AutoCloseable {
    private var handle: Long
    private var done = false

    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        handle = extractor.fingerprintBegin(sampleRate)
        check(handle != 0L) { "native fingerprint session was not created" }
    }

    fun feed(samples: FloatArray, count: Int = samples.size) {
        check(!done) { "fingerprint stream already closed or finished" }
        require(count in 0..samples.size) { "count $count out of range 0..${samples.size}" }
        extractor.fingerprintFeed(handle, samples, count)
    }

    fun finish(): IntArray {
        check(!done) { "fingerprint stream already closed or finished" }
        done = true
        val h = handle
        handle = 0L
        return extractor.fingerprintFinish(h) // frees the native session even when it throws
    }

    override fun close() {
        if (done) return
        done = true
        val h = handle
        handle = 0L
        extractor.fingerprintAbort(h)
    }
}

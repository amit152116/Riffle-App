package com.amit152116.riffle.analysis

import com.amit152116.riffle.dsp.AudioFeatures
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.dsp.FingerprintStream
import com.amit152116.riffle.dsp.PcmStreamDecoder
import com.amit152116.riffle.dsp.decodeAllMono

/**
 * Analyses one track. The plan depends on the container duration alone (spec, invariant 2):
 * FULL keeps today's whole-track path unchanged; WINDOWED streams the file once, fingerprinting all of
 * it and analysing only the planned windows. Blocking: call from a background dispatcher.
 */
class TrackAnalyzer(
    private val extractor: EssentiaExtractor,
    private val planConfig: AnalysisPlanConfig = AnalysisPlanConfig()
) {
    /**
     * @param isCancelled polled between decoded chunks; when it turns true the analysis stops with a
     * [java.util.concurrent.CancellationException]. A native extraction already in progress (a 45 s window, or one
     * whole <= 6 min track) still runs to its end, so a stop is honoured within seconds, not minutes.
     */
    fun analyze(isCancelled: () -> Boolean = { false }, open: () -> PcmStreamDecoder): AudioFeatures = open().use { decoder ->
        when (val plan = AnalysisPlan.forDuration(decoder.durationSec, planConfig)) {
            is AnalysisPlan.Full -> analyzeFull(decoder, isCancelled)
            is AnalysisPlan.Windowed -> analyzeWindowed(decoder, plan, isCancelled)
        }
    }

    private fun analyzeFull(decoder: PcmStreamDecoder, isCancelled: () -> Boolean): AudioFeatures {
        val (samples, rate) = decodeAllMono(decoder, isCancelled = isCancelled)
        return extractor.extract(samples, rate)
    }

    private fun analyzeWindowed(decoder: PcmStreamDecoder, plan: AnalysisPlan.Windowed, isCancelled: () -> Boolean): AudioFeatures {
        val rate = decoder.sampleRate
        val perWindow = ArrayList<AudioFeatures>(plan.windows.size)
        val collector = WindowCollector(plan.windows) { _, samples, r ->
            perWindow += extractor.extract(samples, r, withFingerprint = false)
        }
        var totalSamples = 0L
        val fingerprint = FingerprintStream(extractor, rate).use { stream ->
            val chunk = FloatArray(CHUNK_SAMPLES)
            while (true) {
                if (isCancelled()) throw java.util.concurrent.CancellationException("analysis cancelled")
                val n = decoder.read(chunk)
                if (n < 0) break
                check(decoder.sampleRate == rate) { "sample rate changed mid-stream" }
                stream.feed(chunk, n)
                collector.accept(chunk, n, rate)
                totalSamples += n
            }
            collector.finish()
            stream.finish()
        }
        check(perWindow.isNotEmpty()) { "no analysable window in ${totalSamples / rate.toDouble()} s of audio" }
        return FeatureAggregator.combine(perWindow, (totalSamples / rate.toDouble()).toFloat()).copy(
            fingerprint = fingerprint,
            fingerprintAlgorithm = EssentiaExtractor.FINGERPRINT_CLI_ALGORITHM
        )
    }

    private companion object {
        const val CHUNK_SAMPLES = 1 shl 16
    }
}

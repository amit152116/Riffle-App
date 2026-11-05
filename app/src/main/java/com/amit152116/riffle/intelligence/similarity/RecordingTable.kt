package com.amit152116.riffle.intelligence.similarity

import com.amit152116.riffle.intelligence.model.EligibleRecording
import kotlin.math.sqrt

/**
 * Struct-of-arrays view of recordings so the O(n^2) loop touches flat primitives only. MFCC is stored
 * already standardised with the calibration (c0 dropped when the calibration says so).
 */
class RecordingTable(val recordings: List<EligibleRecording>, calibration: SimilarityCalibration) {
    val size: Int = recordings.size
    val mfccDims: Int = calibration.mfccDims
    val mfcc = DoubleArray(size * mfccDims)
    val mfccNorm = DoubleArray(size)
    val bpm = DoubleArray(size)
    val keyPosition = IntArray(size)
    val energy = DoubleArray(size)

    init {
        val start = calibration.mfccStartDim
        recordings.forEachIndexed { i, r ->
            var sumSq = 0.0
            for (d in 0 until mfccDims) {
                val v = (r.mfcc[start + d] - calibration.mfccMean[start + d]) / calibration.mfccStd[start + d]
                mfcc[i * mfccDims + d] = v
                sumSq += v * v
            }
            mfccNorm[i] = sqrt(sumSq)
            bpm[i] = if (r.hasValidBpm) r.bpm!! else Double.NaN
            keyPosition[i] = KeyDistance.position(r.keyName, r.scale)
            energy[i] = r.energy
        }
    }
}

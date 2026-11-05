package com.amit152116.riffle.intelligence.cluster

import com.amit152116.riffle.intelligence.TestData.rec
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureVectorizerTest {
    @Test fun columnOrder_isScalarsThenMfcc() {
        val r = rec(1, bpm = 100.0, energy = 0.4, danceability = 0.3, loudness = -8.0,
            centroid = 1800.0, onset = 4.0, mfcc = DoubleArray(13) { it.toDouble() })
        val data = FeatureVectorizer.vectorize(listOf(r)).data
        assertEquals(listOf(100.0, 0.4, 0.3, -8.0, 1800.0, 4.0), data.take(6))
        assertEquals((0..12).map { it.toDouble() }, data.drop(6))
    }

    @Test fun invalidBpm_isImputedWithMedianOfValidValues() {
        val recs = listOf(rec(1, bpm = 100.0), rec(2, bpm = 110.0), rec(3, bpm = 0.0), rec(4, bpm = null))
        val data = FeatureVectorizer.vectorize(recs).data
        assertEquals(105.0, data[2 * FeatureVectorizer.DIM], 1e-12)
        assertEquals(105.0, data[3 * FeatureVectorizer.DIM], 1e-12)
        assertEquals(100.0, data[0], 1e-12)
    }

    @Test fun nullScalars_areImputedPerColumn() {
        val recs = listOf(rec(1, danceability = 0.2), rec(2, danceability = 0.4), rec(3, danceability = null))
        val data = FeatureVectorizer.vectorize(recs).data
        assertEquals(0.3, data[2 * FeatureVectorizer.DIM + 2], 1e-12)
    }

    @Test fun allMissingColumn_imputesZero() {
        val data = FeatureVectorizer.vectorize(listOf(rec(1, onset = null), rec(2, onset = null))).data
        assertEquals(0.0, data[5], 0.0)
    }

    @Test fun groups_coverEveryDimensionExactlyOnce() {
        val all = (FeatureVectorizer.MUSICAL_DIMS + FeatureVectorizer.TIMBRE_DIMS).sorted()
        assertEquals((0 until FeatureVectorizer.DIM).toList(), all)
    }
}

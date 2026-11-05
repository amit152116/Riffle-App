package com.amit152116.riffle.intelligence.cluster

import com.amit152116.riffle.intelligence.model.ClusterConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class StandardScalerTest {
    private val data = doubleArrayOf(1.0, 10.0, 2.0, 10.0, 3.0, 10.0)

    @Test fun fit_usesPopulationStd() {
        val s = StandardScaler.fit(data, 3, 2)
        assertArrayEquals(doubleArrayOf(2.0, 10.0), s.mean, 1e-12)
        assertEquals(sqrt(2.0 / 3.0), s.std[0], 1e-12)
    }

    @Test fun constantColumn_hasUnitStd_andTransformsToZero() {
        val s = StandardScaler.fit(data, 3, 2)
        assertEquals(1.0, s.std[1], 0.0)
        val out = s.transform(data, 3, doubleArrayOf(1.0, 1.0))
        for (i in 0 until 3) assertEquals(0.0, out[i * 2 + 1], 1e-9)
        assertEquals(-1.0 / sqrt(2.0 / 3.0), out[0], 1e-9)
    }

    @Test fun nearConstantColumn_fromFloatingPointNoise_isTreatedAsConstant() {
        val noisy = DoubleArray(50) { 0.1 }
        val s = StandardScaler.fit(noisy, 50, 1)
        assertEquals(1.0, s.std[0], 0.0)
    }

    @Test fun clip_limitsZScores_soOutliersCannotDominate() {
        val col = DoubleArray(20) { if (it == 0) 100.0 else 0.0 } // one huge outlier: raw z ~ 4.36
        val s = StandardScaler.fit(col, 20, 1)
        val unclipped = s.transform(col, 20, doubleArrayOf(1.0))
        val clipped = s.transform(col, 20, doubleArrayOf(1.0), clip = 3.0)
        assertTrue(unclipped.max() > 4.0)
        assertEquals(3.0, clipped.max(), 1e-12)
        assertEquals(unclipped[1], clipped[1], 1e-12) // ordinary values untouched
        assertEquals(3.0, s.transformVector(doubleArrayOf(100.0), doubleArrayOf(1.0), clip = 3.0)[0], 1e-12)
    }

    @Test fun transformVector_matchesTransformRow() {
        val s = StandardScaler.fit(data, 3, 2)
        val f = doubleArrayOf(0.5, 2.0)
        assertArrayEquals(s.transform(data, 3, f).copyOfRange(2, 4), s.transformVector(doubleArrayOf(2.0, 10.0), f), 1e-12)
    }

    @Test fun groupWeights_sumOfSquaresIsOne_andSplitEvenlyWithinGroup() {
        val f = GroupWeights.factors(ClusterConfig())
        assertEquals(1.0, f.sumOf { it * it }, 1e-12)
        FeatureVectorizer.MUSICAL_DIMS.forEach { assertEquals(f[FeatureVectorizer.MUSICAL_DIMS[0]], f[it], 1e-15) }
        FeatureVectorizer.TIMBRE_DIMS.forEach { assertEquals(f[FeatureVectorizer.TIMBRE_DIMS[0]], f[it], 1e-15) }
    }

    @Test fun pythonParityWeighting_isEqualPerDimension() {
        val f = GroupWeights.factors(ClusterConfig(musicalWeight = 5.0 / 19, timbreWeight = 14.0 / 19))
        f.forEach { assertEquals(sqrt(1.0 / 19), it, 1e-12) }
    }
}

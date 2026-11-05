package com.amit152116.riffle.intelligence

import com.amit152116.riffle.intelligence.cluster.ClusterBuilder
import com.amit152116.riffle.intelligence.model.EligibleRecording
import com.amit152116.riffle.intelligence.model.SimilarityConfig
import com.amit152116.riffle.intelligence.similarity.SimilarityIndexBuilder
import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random

/**
 * Manual benchmark - remove @Ignore locally. n = 50_000 is about 2.5 billion pair scores and takes
 * minutes; run it on its own. Decision rule from the spec: only build a native SimilarityKernel if a
 * Kotlin build exceeds ~10 s at a realistic scale on the device.
 */
@Ignore("manual benchmark")
class IntelligenceBenchmarkTest {
    private fun synthetic(n: Int): List<EligibleRecording> {
        val rng = Random(1)
        return (1..n).map { id ->
            TestData.rec(
                id.toLong(),
                bpm = rng.nextDouble(60.0, 200.0),
                key = listOf("C", "D", "F#", "Bb", "A").random(rng),
                scale = listOf("major", "minor").random(rng),
                energy = rng.nextDouble(),
                danceability = rng.nextDouble(),
                loudness = rng.nextDouble(-30.0, -5.0),
                centroid = rng.nextDouble(500.0, 4000.0),
                onset = rng.nextDouble(1.0, 8.0),
                mfcc = DoubleArray(13) { rng.nextDouble(-20.0, 20.0) }
            )
        }
    }

    @Test
    fun timings() {
        for (n in listOf(783, 10_000)) {
            val recs = synthetic(n)
            val t0 = System.nanoTime()
            SimilarityIndexBuilder(SimilarityConfig()).build(recs)
            val t1 = System.nanoTime()
            ClusterBuilder().build(recs)
            val t2 = System.nanoTime()
            println("n=$n similarity=${(t1 - t0) / 1_000_000}ms clustering=${(t2 - t1) / 1_000_000}ms")
        }
    }
}

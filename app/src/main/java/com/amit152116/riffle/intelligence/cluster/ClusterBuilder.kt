package com.amit152116.riffle.intelligence.cluster

import com.amit152116.riffle.intelligence.math.Stats
import com.amit152116.riffle.intelligence.model.ClusterConfig
import com.amit152116.riffle.intelligence.model.EligibleRecording
import java.util.Locale
import kotlin.math.sqrt

class PreviousCentroid(val clusterId: Int, val rawCentroid: DoubleArray)
class PreviousRun(val centroids: List<PreviousCentroid>, val nextClusterId: Int)

class ClusterOut(
    val clusterId: Int,
    val label: ClusterLabel,
    val displayName: String,
    val rawCentroid: DoubleArray,
    val size: Int,
    val matchDistance: Double?
)

class AssignmentOut(val audioContentId: Long, val clusterId: Int, val distance: Double)

class ClusterResult(
    val clusters: List<ClusterOut>,
    val assignments: List<AssignmentOut>,
    val scalerMean: DoubleArray,
    val scalerStd: DoubleArray,
    val silhouette: Double?,
    val kScores: String,
    val nextClusterId: Int
) {
    val k: Int get() = clusters.size
}

private class KSelection(val labels: IntArray, val k: Int, val silhouette: Double?, val kScores: String)

class ClusterBuilder(private val config: ClusterConfig = ClusterConfig()) {

    fun build(recordings: List<EligibleRecording>, previous: PreviousRun? = null): ClusterResult {
        require(recordings.isNotEmpty()) { "cannot cluster zero recordings" }
        val recs = recordings.sortedBy { it.audioContentId }
        val n = recs.size
        val dim = FeatureVectorizer.DIM
        val raw = FeatureVectorizer.vectorize(recs).data
        val scaler = StandardScaler.fit(raw, n, dim)
        val factors = GroupWeights.factors(config)
        val scaled = scaler.transform(raw, n, factors, config.zClip)

        val maxK = minOf(config.kCeil, n / config.minClusterSize)
        val selection = if (maxK < 2) single(n, "") else selectK(scaled, n, dim, maxK)
        val k = selection.k
        val labels = selection.labels

        val members = List(k) { ArrayList<Int>() }
        for (i in 0 until n) members[labels[i]].add(i)
        val rawCentroids = Array(k) { c -> meanOf(raw, members[c], dim) }
        val scaledCentroids = Array(k) { c -> meanOf(scaled, members[c], dim) }
        val distances = DoubleArray(n) { i -> euclid(scaled, i * dim, scaledCentroids[labels[i]]) }

        val ids = IntArray(k)
        val matchDistance = arrayOfNulls<Double>(k)
        val matched = BooleanArray(k)
        val prev = previous?.centroids.orEmpty().filter { it.rawCentroid.size == dim }
        var next = maxOf(previous?.nextClusterId ?: 0, (prev.maxOfOrNull { it.clusterId } ?: -1) + 1)
        if (prev.isNotEmpty()) {
            val prevScaled = prev.map { scaler.transformVector(it.rawCentroid, factors, config.zClip) }
            val cost = Array(k) { r -> DoubleArray(prev.size) { c -> euclid(scaledCentroids[r], prevScaled[c]) } }
            val threshold = config.matchFactor * Stats.median(distances)
            val assignment = Hungarian.assign(cost)
            for (r in 0 until k) {
                val c = assignment[r]
                if (c >= 0 && cost[r][c] <= threshold) {
                    matched[r] = true
                    ids[r] = prev[c].clusterId
                    matchDistance[r] = cost[r][c]
                }
            }
        }
        for (r in 0 until k) if (!matched[r]) ids[r] = next++

        val labeler = ClusterLabeler(config, recs)
        val labelOf = List(k) { c -> labeler.label(members[c].map { recs[it] }) }
        val baseNames = (0 until k).associate { c -> ids[c] to (if (k == 1) "All" else labelOf[c].baseName) }
        val names = ClusterLabeler.uniqueNames(baseNames)

        val clusters = (0 until k).map { c ->
            ClusterOut(ids[c], labelOf[c], names.getValue(ids[c]), rawCentroids[c], members[c].size, matchDistance[c])
        }.sortedBy { it.clusterId }
        val assignments = (0 until n).map { i -> AssignmentOut(recs[i].audioContentId, ids[labels[i]], distances[i]) }
        return ClusterResult(clusters, assignments, scaler.mean, scaler.std, selection.silhouette, selection.kScores, next)
    }

    private fun single(n: Int, kScores: String) = KSelection(IntArray(n), 1, null, kScores)

    private fun selectK(scaled: DoubleArray, n: Int, dim: Int, maxK: Int): KSelection {
        // Floor of 5 keeps playlist granularity (silhouette alone favours 2-3 clusters on music);
        // a small library (maxK < 5) lets silhouette choose among 2..maxK instead of forcing one k.
        val rangeMin = if (maxK >= config.kFloor) config.kFloor else 2
        val log = ArrayList<String>()

        fun evaluate(k: Int): KSelection? {
            val fit = KMeans.fit(scaled, n, dim, k, config.seed, config.nInit, config.maxIter, config.tolerance)
            // k-means happily spends a cluster on a few outliers (silent files, clips). Rejecting the whole k
            // for that made every k fail on real data; fold such clusters into their nearest neighbour instead.
            val merged = mergeUndersized(scaled, n, dim, fit.labels, k, config.minClusterSize)
            val label = if (merged.k == k) "$k" else "$k→${merged.k}"
            if (merged.k < 2) {
                log += "$label:collapsed"
                return null
            }
            val s = Silhouette.score(scaled, n, dim, merged.labels, merged.k, config.silhouetteSampleCap, config.seed)
            if (s == null) {
                log += "$label:n/a"
                return null
            }
            log += "$label:${String.format(Locale.ROOT, "%.3f", s)}"
            return KSelection(merged.labels, merged.k, s, "")
        }

        var best: KSelection? = null
        for (k in rangeMin..maxK) {
            val cand = evaluate(k) ?: continue
            val b = best
            if (b == null || cand.silhouette!! > b.silhouette!! ||
                (cand.silhouette == b.silhouette && cand.k < b.k)
            ) best = cand
        }
        val kScores = log.joinToString(",")
        val chosen = best ?: return single(n, kScores)
        return KSelection(chosen.labels, chosen.k, chosen.silhouette, kScores)
    }

    private class Merged(val labels: IntArray, val k: Int)

    /**
     * Repeatedly folds the smallest cluster below [minSize] into the cluster with the nearest centroid
     * (empty clusters just vanish), then relabels 0..k'-1 in the original order. Deterministic.
     */
    private fun mergeUndersized(scaled: DoubleArray, n: Int, dim: Int, labels: IntArray, k: Int, minSize: Int): Merged {
        val sums = Array(k) { DoubleArray(dim) }
        val sizes = IntArray(k)
        for (i in 0 until n) {
            val l = labels[i]
            sizes[l]++
            for (d in 0 until dim) sums[l][d] += scaled[i * dim + d]
        }
        val alive = BooleanArray(k) { sizes[it] > 0 }
        val current = labels.copyOf()
        fun centroid(c: Int) = DoubleArray(dim) { sums[c][it] / sizes[c] }
        while (true) {
            val live = (0 until k).filter { alive[it] }
            if (live.size <= 1) break
            val smallest = live.minByOrNull { sizes[it] }!!
            if (sizes[smallest] >= minSize) break
            val cs = centroid(smallest)
            val target = live.filter { it != smallest }.minByOrNull { c ->
                val cc = centroid(c)
                var d2 = 0.0
                for (d in 0 until dim) d2 += (cs[d] - cc[d]) * (cs[d] - cc[d])
                d2
            }!!
            for (d in 0 until dim) sums[target][d] += sums[smallest][d]
            sizes[target] += sizes[smallest]
            sizes[smallest] = 0
            alive[smallest] = false
            for (i in 0 until n) if (current[i] == smallest) current[i] = target
        }
        val remap = IntArray(k) { -1 }
        var next = 0
        for (c in 0 until k) if (alive[c]) remap[c] = next++
        return Merged(IntArray(n) { remap[current[it]] }, next)
    }

    private fun meanOf(data: DoubleArray, rows: List<Int>, dim: Int): DoubleArray {
        val out = DoubleArray(dim)
        for (i in rows) for (d in 0 until dim) out[d] += data[i * dim + d]
        for (d in 0 until dim) out[d] /= rows.size
        return out
    }

    private fun euclid(data: DoubleArray, offset: Int, centroid: DoubleArray): Double {
        var s = 0.0
        for (d in centroid.indices) {
            val diff = data[offset + d] - centroid[d]
            s += diff * diff
        }
        return sqrt(s)
    }

    private fun euclid(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (d in a.indices) {
            val diff = a[d] - b[d]
            s += diff * diff
        }
        return sqrt(s)
    }
}

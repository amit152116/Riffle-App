package com.amit152116.riffle.intelligence.cluster

/** Kuhn-Munkres (potentials form), O(rows^2 * cols). Handles rectangular matrices. */
object Hungarian {
    /** For each row, the assigned column, or -1 when rows > cols and the row is left out. */
    fun assign(cost: Array<DoubleArray>): IntArray {
        val rows = cost.size
        if (rows == 0) return IntArray(0)
        val cols = cost[0].size
        if (cols == 0) return IntArray(rows) { -1 }
        if (rows > cols) {
            val transposed = Array(cols) { c -> DoubleArray(rows) { r -> cost[r][c] } }
            val colToRow = assign(transposed)
            val result = IntArray(rows) { -1 }
            colToRow.forEachIndexed { c, r -> if (r >= 0) result[r] = c }
            return result
        }
        val u = DoubleArray(rows + 1)
        val v = DoubleArray(cols + 1)
        val p = IntArray(cols + 1)
        val way = IntArray(cols + 1)
        for (i in 1..rows) {
            p[0] = i
            var j0 = 0
            val minv = DoubleArray(cols + 1) { Double.MAX_VALUE }
            val used = BooleanArray(cols + 1)
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = Double.MAX_VALUE
                var j1 = 0
                for (j in 1..cols) {
                    if (used[j]) continue
                    val cur = cost[i0 - 1][j - 1] - u[i0] - v[j]
                    if (cur < minv[j]) {
                        minv[j] = cur
                        way[j] = j0
                    }
                    if (minv[j] < delta) {
                        delta = minv[j]
                        j1 = j
                    }
                }
                for (j in 0..cols) {
                    if (used[j]) {
                        u[p[j]] += delta
                        v[j] -= delta
                    } else minv[j] -= delta
                }
                j0 = j1
            } while (p[j0] != 0)
            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }
        val result = IntArray(rows) { -1 }
        for (j in 1..cols) if (p[j] != 0) result[p[j] - 1] = j - 1
        return result
    }
}

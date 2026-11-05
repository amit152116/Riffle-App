package com.amit152116.riffle.intelligence.cluster

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HungarianTest {
    @Test fun square_findsMinimumTotalCost() {
        val cost = arrayOf(
            doubleArrayOf(4.0, 1.0, 3.0),
            doubleArrayOf(2.0, 0.0, 5.0),
            doubleArrayOf(3.0, 2.0, 2.0)
        )
        assertArrayEquals(intArrayOf(1, 0, 2), Hungarian.assign(cost))
    }

    @Test fun moreColumnsThanRows_assignsEveryRow() {
        val cost = arrayOf(doubleArrayOf(1.0, 9.0, 9.0), doubleArrayOf(9.0, 1.0, 9.0))
        assertArrayEquals(intArrayOf(0, 1), Hungarian.assign(cost))
    }

    @Test fun moreRowsThanColumns_leavesTheWorstRowUnassigned() {
        val cost = arrayOf(doubleArrayOf(1.0, 9.0), doubleArrayOf(9.0, 1.0), doubleArrayOf(5.0, 5.0))
        assertArrayEquals(intArrayOf(0, 1, -1), Hungarian.assign(cost))
    }

    @Test fun emptyInputs() {
        assertEquals(0, Hungarian.assign(emptyArray()).size)
        assertArrayEquals(intArrayOf(-1, -1), Hungarian.assign(arrayOf(DoubleArray(0), DoubleArray(0))))
    }

    @Test fun identityIsOptimalForADiagonalMatrix() {
        val n = 6
        val cost = Array(n) { r -> DoubleArray(n) { c -> if (r == c) 0.0 else 10.0 } }
        assertArrayEquals(IntArray(n) { it }, Hungarian.assign(cost))
    }
}

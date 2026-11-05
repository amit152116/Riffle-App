package com.amit152116.riffle.intelligence.similarity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyDistanceTest {
    private fun d(a: String?, sa: String?, b: String?, sb: String?) = KeyDistance.distance(a, sa, b, sb)

    @Test fun sameKey_zero() = assertEquals(0, d("C", "major", "C", "major"))
    @Test fun relativeMinor_isZero() = assertEquals(0, d("C", "major", "A", "minor"))
    @Test fun adjacentFifths_isOne() = assertEquals(1, d("C", "major", "G", "major"))
    @Test fun tritone_isSix() = assertEquals(6, d("C", "major", "F#", "major"))
    @Test fun minorKeysUseRelativeMajor() = assertEquals(1, d("A", "minor", "E", "minor"))
    @Test fun enharmonicSpellings_areEqual() {
        assertEquals(0, d("C#", "major", "Db", "major"))
        assertEquals(0, d("G#", "minor", "Ab", "minor"))
        assertEquals(0, d("A#", "major", "Bb", "major"))
    }
    @Test fun nullScale_treatedAsMajor() = assertEquals(1, d("C", null, "G", null))
    @Test fun missingKey_isNull() {
        assertNull(d(null, "major", "C", "major"))
        assertNull(d("  ", "major", "C", "major"))
    }
    @Test fun unparseableKey_isMaximal() = assertEquals(6, d("H", "major", "C", "major"))
    @Test fun symmetric() {
        val keys = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        for (a in keys) for (b in keys) for (sa in listOf("major", "minor")) for (sb in listOf("major", "minor")) {
            assertEquals(d(a, sa, b, sb), d(b, sb, a, sa))
        }
    }
    @Test fun matchesPythonCircleOfFifthsOrder() {
        val circle = listOf("C", "G", "D", "A", "E", "B", "F#", "Db", "Ab", "Eb", "Bb", "F")
        circle.forEachIndexed { i, k -> assertEquals(i, KeyDistance.position(k, "major")) }
    }
}

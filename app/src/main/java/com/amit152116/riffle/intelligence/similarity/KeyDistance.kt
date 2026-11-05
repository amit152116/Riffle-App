package com.amit152116.riffle.intelligence.similarity

import kotlin.math.abs

object KeyDistance {
    const val MISSING = -2
    const val UNPARSEABLE = -1

    private val NATURAL = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** Pitch class 0..11 for names like "C", "F#", "Bb", "C♯"; null when unparseable. */
    fun pitchClass(name: String): Int? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        var pc = NATURAL[trimmed[0].uppercaseChar()] ?: return null
        for (ch in trimmed.drop(1)) {
            pc += when (ch) {
                '#', '♯' -> 1
                'b', '♭' -> -1
                else -> return null
            }
        }
        return ((pc % 12) + 12) % 12
    }

    /**
     * Circle-of-fifths position (0..11) of the key's relative major: fifths index = (7 * pc) mod 12,
     * and a minor key is first shifted +3 semitones to its relative major. Equivalent to the
     * Python's CIRCLE_OF_FIFTHS table for valid names, and it accepts every enharmonic spelling.
     */
    fun position(keyName: String?, scale: String?): Int {
        if (keyName == null || keyName.isBlank()) return MISSING
        val pc = pitchClass(keyName) ?: return UNPARSEABLE
        val majorPc = if (scale == "minor") (pc + 3) % 12 else pc
        return (7 * majorPc) % 12
    }

    /** Null when either key is missing; 6 (maximal) when either is unparseable. */
    fun distance(posA: Int, posB: Int): Int? {
        if (posA == MISSING || posB == MISSING) return null
        if (posA == UNPARSEABLE || posB == UNPARSEABLE) return 6
        val d = abs(posA - posB)
        return minOf(d, 12 - d)
    }

    fun distance(keyA: String?, scaleA: String?, keyB: String?, scaleB: String?): Int? =
        distance(position(keyA, scaleA), position(keyB, scaleB))
}

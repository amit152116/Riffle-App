package com.amit152116.riffle.intelligence.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DoublePackingTest {
    @Test fun roundTrips() {
        val values = doubleArrayOf(0.0, -1.5, 3.14159, 1e300)
        assertArrayEquals(values, values.toLittleEndianBytes().toLittleEndianDoubles(), 0.0)
    }

    @Test fun isLittleEndianFloat64() {
        val bytes = doubleArrayOf(1.0).toLittleEndianBytes()
        assertArrayEquals(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0xF0.toByte(), 0x3F),
            bytes
        )
    }

    @Test fun rejectsLengthNotMultipleOfEight() {
        assertThrows(IllegalArgumentException::class.java) { ByteArray(7).toLittleEndianDoubles() }
    }
}

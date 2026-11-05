package com.amit152116.riffle.intelligence.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Little-endian float64 per value - same layout as packMfcc in AnalysisMapping.kt. */
fun DoubleArray.toLittleEndianBytes(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 8).order(ByteOrder.LITTLE_ENDIAN)
    for (v in this) buffer.putDouble(v)
    return buffer.array()
}

fun ByteArray.toLittleEndianDoubles(): DoubleArray {
    require(size % 8 == 0) { "byte length $size is not a multiple of 8" }
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return DoubleArray(size / 8) { buffer.getDouble() }
}

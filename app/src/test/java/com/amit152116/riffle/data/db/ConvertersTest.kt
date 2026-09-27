package com.amit152116.riffle.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun hashMethod_roundTrips() {
        val stored = converters.fromHashMethod(HashMethod.WHOLE_FILE)
        assertEquals(HashMethod.WHOLE_FILE, converters.toHashMethod(stored))
    }

    @Test
    fun hashMethod_null_roundTrips() {
        assertNull(converters.fromHashMethod(null))
        assertNull(converters.toHashMethod(null))
    }

    @Test
    fun fingerprintPurpose_roundTrips() {
        val stored = converters.fromFingerprintPurpose(FingerprintPurpose.ACOUSTID_LOOKUP)
        assertEquals(FingerprintPurpose.ACOUSTID_LOOKUP, converters.toFingerprintPurpose(stored))
    }

    @Test
    fun runStatus_roundTrips() {
        val stored = converters.fromRunStatus(RunStatus.FAILED)
        assertEquals(RunStatus.FAILED, converters.toRunStatus(stored))
    }

    @Test
    fun absentReason_null_roundTrips() {
        assertNull(converters.fromAbsentReason(null))
        assertNull(converters.toAbsentReason(null))
    }

    @Test
    fun absentReason_roundTrips() {
        val stored = converters.fromAbsentReason(AbsentReason.QUARANTINED)
        assertEquals(AbsentReason.QUARANTINED, converters.toAbsentReason(stored))
    }

    @Test
    fun dupDecision_roundTrips() {
        val stored = converters.fromDupDecision(DupDecision.APPLIED)
        assertEquals(DupDecision.APPLIED, converters.toDupDecision(stored))
    }

    @Test
    fun quarantineState_roundTrips() {
        val stored = converters.fromQuarantineState(QuarantineState.UNDONE)
        assertEquals(QuarantineState.UNDONE, converters.toQuarantineState(stored))
    }
}

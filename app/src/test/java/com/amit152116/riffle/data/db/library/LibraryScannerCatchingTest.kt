package com.amit152116.riffle.data.db.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LibraryScannerCatchingTest {
    @Test
    fun runCatchingNonFatal_catchesOutOfMemoryError() = runBlocking {
        val result = runCatchingNonFatal<Unit> { throw OutOfMemoryError("test") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OutOfMemoryError)
    }

    @Test
    fun runCatchingNonFatal_rethrowsCancellationExceptionInsteadOfCatchingIt() = runBlocking {
        try {
            runCatchingNonFatal<Unit> { throw CancellationException("test") }
            fail("expected CancellationException to propagate, not be caught into Result.failure")
        } catch (e: CancellationException) {
            // expected
        }
    }

    @Test
    fun runCatchingNonFatal_returnsSuccessOnNormalCompletion() = runBlocking {
        val result = runCatchingNonFatal { 42 }

        assertEquals(42, result.getOrNull())
    }
}

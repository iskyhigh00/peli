package com.peli.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AttemptTest {
    @Test fun successAndFailureAreReturnedAsResults() = runBlocking {
        assertEquals(3, attempt { 3 }.getOrNull())
        val failure = attempt<Int> { throw IOException("sin red") }
        assertTrue(failure.isFailure)
        assertEquals("sin red", failure.exceptionOrNull()!!.message)
    }

    @Test fun cancellationIsNeverSwallowed() {
        try {
            runBlocking { attempt<Int> { throw CancellationException("cancelada") } }
            fail("debía relanzar la cancelación")
        } catch (e: CancellationException) {
            assertEquals("cancelada", e.message)
        }
    }

    @Test fun tallyFlagsTotalFailureButNotPartialOnes() {
        val allBad = SourceTally()
        allBad.record(Result.failure<List<Int>>(IOException("a")))
        allBad.record(Result.failure<List<Int>>(IOException("b")))
        assertTrue(allBad.allFailed)
        try {
            allBad.requireAny()
            fail("todas fallaron")
        } catch (e: SourcesUnavailableException) {
            assertEquals("a", e.message)
        }

        val partial = SourceTally()
        assertEquals(listOf(1), partial.record(Result.success(listOf(1))))
        assertNull(partial.record(Result.failure<List<Int>>(IOException("x"))))
        assertFalse(partial.allFailed)
        partial.requireAny() // no lanza
        assertEquals(1, partial.ok)
        assertEquals(1, partial.failed)
    }

    @Test fun sourcesThatRespondedEmptyAreNotAnError() {
        val empty = SourceTally()
        empty.record(Result.success(emptyList<Int>()))
        assertFalse(empty.allFailed)
        empty.requireAny()
    }

    @Test fun noSourcesAtAllIsNotAFailureEither() {
        val none = SourceTally()
        assertFalse(none.allFailed)
        none.requireAny()
    }
}

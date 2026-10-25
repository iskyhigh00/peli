package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseInfoTest {
    @Test fun formatsDatesInSpanish() {
        assertEquals("12 nov 2026", ReleaseInfo.format("2026-11-12"))
        assertEquals("5 ene 2027", ReleaseInfo.format("2027-01-05"))
    }

    @Test fun formatRejectsInvalidInput() {
        assertNull(ReleaseInfo.format(null))
        assertNull(ReleaseInfo.format(""))
        assertNull(ReleaseInfo.format("mañana"))
        assertNull(ReleaseInfo.format("2026-13-01"))
    }

    @Test fun futureAndPastDates() {
        assertTrue(ReleaseInfo.isUpcoming("2999-01-01", ""))
        assertFalse(ReleaseInfo.isUpcoming("2000-01-01", "2000"))
    }

    @Test fun withoutDateTheYearDecides() {
        assertTrue(ReleaseInfo.isUpcoming(null, "2999"))
        assertTrue(ReleaseInfo.isUpcoming("  ", "2999"))
        assertFalse(ReleaseInfo.isUpcoming(null, "1999"))
        assertFalse(ReleaseInfo.isUpcoming(null, ""))
    }
}

package com.peli.app.data

import com.peli.app.ui.accepts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class YearRangeTest {
    @Test fun containsUsesTheFirstFourDigitsAndBothEnds() {
        val r = YearRange(1990, 1999)
        assertTrue(r.contains("1990"))
        assertTrue(r.contains("1999"))
        assertTrue(r.contains("1995-06-12"))
        assertFalse(r.contains("1989"))
        assertFalse(r.contains("2000"))
    }

    @Test fun unknownYearIsNeverInsideARange() {
        assertFalse(YearRange(1900, 2100).contains(""))
        assertFalse(YearRange(1900, 2100).contains("s/f"))
    }

    @Test fun noRangeAcceptsEverythingEvenUnknownYears() {
        val none: YearRange? = null
        assertTrue(none.accepts("1985"))
        assertTrue(none.accepts(""))
        assertTrue(YearRange(1980, 1989).accepts("1985"))
        assertFalse(YearRange(1980, 1989).accepts("1990"))
    }

    @Test fun invalidRangesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { YearRange(2000, 1990) }
    }

    @Test fun labelsAndTmdbDates() {
        assertEquals("1990–1999", YearRange(1990, 1999).label())
        assertEquals("2015", YearRange(2015, 2015).label())
        assertEquals("1990-01-01", YearRange(1990, 1999).startDate)
        assertEquals("1999-12-31", YearRange(1990, 1999).endDate)
    }

    @Test fun encodeAndDecodeRoundTrip() {
        val r = YearRange(1975, 2003)
        assertEquals(r, YearRange.decode(r.encode()))
    }

    @Test fun decodeRejectsGarbage() {
        assertNull(YearRange.decode(""))
        assertNull(YearRange.decode("1990"))
        assertNull(YearRange.decode("a-b"))
        assertNull(YearRange.decode("2000-1990"))
        assertNull(YearRange.decode("1-2-3"))
    }

    @Test fun presetsAreValidAndRelativeToTheCurrentYear() {
        val presets = YearRange.presets(2026)
        assertTrue(presets.all { it.second.from <= it.second.to })
        assertEquals(YearRange(2022, 2026), presets.first { it.first == "Últimos 5 años" }.second)
        assertEquals(YearRange(2026, 2028), presets.first { it.first == "Este año y próximos" }.second)
        assertEquals(YearRange(1990, 1999), presets.first { it.first == "90s" }.second)
        assertEquals(YearRange.MIN_YEAR, presets.first { it.first == "Clásicas" }.second.from)
    }

    @Test fun sliderGoesTwoYearsIntoTheFuture() {
        assertEquals(2028, YearRange.maxYear(2026))
    }
}

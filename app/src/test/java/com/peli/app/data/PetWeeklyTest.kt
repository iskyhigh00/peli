package com.peli.app.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetWeeklyTest {
    private val zone = TimeZone.getTimeZone("America/Santiago")
    private fun at(y: Int, m: Int, d: Int, h: Int) = Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, h, 0, 0) }.timeInMillis

    @Test fun `cada semana hay 3 dias distintos y siempre los mismos`() {
        for (week in 1..52) {
            val a = PetWeekly.daysOfWeek(2026, week)
            assertEquals(3, a.size)
            assertEquals(a, PetWeekly.daysOfWeek(2026, week))
        }
        // no es siempre la misma combinación
        assertTrue((1..52).map { PetWeekly.daysOfWeek(2026, it) }.toSet().size > 10)
    }

    @Test fun `solo toca una vez por dia y en horario de dia`() {
        var found = 0
        for (day in 2..8) {      // semana del 2 al 8 de noviembre de 2026
            val noon = at(2026, 11, day, 12)
            if (PetWeekly.due(noon, null, zone)) {
                found++
                assertFalse(PetWeekly.due(noon, PetWeekly.dayKey(noon, zone), zone))     // ya se mandó hoy
                assertFalse(PetWeekly.due(at(2026, 11, day, 3), null, zone))              // de madrugada no
                assertFalse(PetWeekly.due(at(2026, 11, day, 23), null, zone))             // de noche no
            }
        }
        assertEquals(3, found)
    }

    @Test fun `la perrita del dia es siempre la misma ese dia`() {
        val a = PetWeekly.petFor(at(2026, 11, 5, 11), zone)
        assertEquals(a, PetWeekly.petFor(at(2026, 11, 5, 19), zone))
    }

    @Test fun `ambas recomiendan a lo largo del tiempo`() {
        val pets = (1..60).map { PetWeekly.petFor(at(2026, 11, 1, 12) + it * 86_400_000L, zone) }.toSet()
        assertEquals(Pet.entries.toSet(), pets)
    }
}

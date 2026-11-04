package com.peli.app.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekendPlanTest {
    private val zone = TimeZone.getTimeZone("America/Santiago")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, h, min, 0) }.timeInMillis
    private fun parts(t: Long) = Calendar.getInstance(zone).apply { timeInMillis = t }

    // 2026-11-04 es miércoles.
    @Test fun `el martes el proximo aviso es el miercoles al mediodia`() {
        val (t, slot) = WeekendPlan.next(at(2026, 11, 3, 15, 0), zone)
        assertEquals(Calendar.WEDNESDAY, slot.day); assertEquals(12, slot.hour)
        assertEquals(4, parts(t).get(Calendar.DAY_OF_MONTH))
    }

    @Test fun `el miercoles hay dos avisos y despues sigue el jueves`() {
        assertEquals(19, WeekendPlan.next(at(2026, 11, 4, 13, 0), zone).second.hour)
        val (t, slot) = WeekendPlan.next(at(2026, 11, 4, 20, 0), zone)
        assertEquals(Calendar.THURSDAY, slot.day); assertEquals(9, slot.hour)
        assertEquals(5, parts(t).get(Calendar.DAY_OF_MONTH))
    }

    @Test fun `el jueves hay cuatro avisos`() {
        assertEquals(4, WeekendPlan.slots.count { it.day == Calendar.THURSDAY })
        assertEquals(2, WeekendPlan.slots.count { it.day == Calendar.WEDNESDAY })
        assertEquals(1, WeekendPlan.slots.count { it.day == Calendar.FRIDAY })
    }

    @Test fun `despues del viernes en la manana se espera al proximo miercoles`() {
        val (t, slot) = WeekendPlan.next(at(2026, 11, 6, 10, 0), zone)
        assertEquals(Calendar.WEDNESDAY, slot.day)
        assertEquals(11, parts(t).get(Calendar.DAY_OF_MONTH))
    }

    @Test fun `sabado y domingo no hay avisos y siempre es en el futuro`() {
        for (day in 7..13) for (h in listOf(0, 8, 12, 18, 23)) {
            val now = at(2026, 11, day, h, 5)
            val (t, _) = WeekendPlan.next(now, zone)
            assertTrue(t > now)
            assertTrue(parts(t).get(Calendar.DAY_OF_WEEK) in listOf(Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
        }
    }

    @Test fun `el aviso que toca es el de la hora aunque la alarma se atrase`() {
        assertEquals(12, WeekendPlan.due(at(2026, 11, 4, 12, 0), zone = zone)!!.hour)
        assertEquals(12, WeekendPlan.due(at(2026, 11, 4, 12, 20), zone = zone)!!.hour)
        assertEquals(17, WeekendPlan.due(at(2026, 11, 5, 17, 5), zone = zone)!!.hour)
        assertEquals(null, WeekendPlan.due(at(2026, 11, 5, 15, 30), zone = zone))        // entre dos avisos
        assertEquals(null, WeekendPlan.due(at(2026, 11, 8, 12, 0), zone = zone))         // domingo
    }
}

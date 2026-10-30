package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    private fun film(id: Int, date: String?, watchlist: Boolean = true, seen: Boolean = false) =
        entry(id, watchlist = watchlist, seen = seen).copy(releaseDate = date)

    @Test fun `cuenta los dias entre fechas`() {
        assertEquals(0, Reminders.daysUntil("2026-11-12", "2026-11-12"))
        assertEquals(1, Reminders.daysUntil("2026-11-30", "2026-12-01"))
        assertEquals(7, Reminders.daysUntil("2026-12-28", "2027-01-04"))
        assertEquals(-2, Reminders.daysUntil("2026-11-12", "2026-11-10"))
        assertEquals(null, Reminders.daysUntil("2026-11-12", "2026"))
        assertEquals(null, Reminders.daysUntil("2026-11-12", null))
    }

    @Test fun `avisa a 7 dias, un dia y el mismo dia`() {
        val today = "2026-11-05"
        assertEquals(7, Reminders.due(listOf(film(1, "2026-11-12")), today, emptySet()).single().stage)
        assertEquals(7, Reminders.due(listOf(film(1, "2026-11-07")), today, emptySet()).single().stage)
        assertEquals(1, Reminders.due(listOf(film(1, "2026-11-06")), today, emptySet()).single().stage)
        assertEquals(0, Reminders.due(listOf(film(1, "2026-11-05")), today, emptySet()).single().stage)
        assertTrue(Reminders.due(listOf(film(1, "2026-11-13")), today, emptySet()).isEmpty())
        assertTrue(Reminders.due(listOf(film(1, "2026-11-04")), today, emptySet()).isEmpty())
    }

    @Test fun `solo las que quiero ver y no he visto`() {
        val today = "2026-11-05"
        val list = listOf(film(1, "2026-11-06", watchlist = false), film(2, "2026-11-06", seen = true), film(3, "2026-11-06"), film(4, null))
        assertEquals(listOf(3), Reminders.due(list, today, emptySet()).map { it.entry.movieId })
    }

    @Test fun `cada etapa se avisa una sola vez y no se vuelve atras`() {
        val f = film(1, "2026-11-12")
        val first = Reminders.due(listOf(f), "2026-11-05", emptySet()).single()
        val done = Reminders.toRecord(first)
        assertTrue(Reminders.due(listOf(f), "2026-11-06", done).isEmpty())
        val tomorrow = Reminders.due(listOf(f), "2026-11-11", done).single()
        assertEquals(1, tomorrow.stage)
        val done2 = done + Reminders.toRecord(tomorrow)
        assertEquals(0, Reminders.due(listOf(f), "2026-11-12", done2).single().stage)
        // Si ya se avisó "hoy", no se manda "mañana" por un cambio de fecha raro.
        assertTrue(Reminders.due(listOf(f), "2026-11-11", done2 + Reminders.toRecord(Reminder(f, 0, 0))).isEmpty())
    }

    @Test fun `al instalar cerca del estreno solo avisa la etapa actual`() {
        val r = Reminders.due(listOf(film(1, "2026-11-08")), "2026-11-05", emptySet()).single()
        assertEquals(setOf("1:7"), Reminders.toRecord(r))
        assertEquals("Se estrena en 3 días (8 nov 2026)", r.text)
    }

    @Test fun `mas cercanas primero`() {
        val list = listOf(film(1, "2026-11-10"), film(2, "2026-11-06"))
        assertEquals(listOf(2, 1), Reminders.due(list, "2026-11-05", emptySet()).map { it.entry.movieId })
    }
}

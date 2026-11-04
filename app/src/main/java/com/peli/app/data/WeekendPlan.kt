package com.peli.app.data

import java.util.Calendar
import java.util.TimeZone

/** Un aviso de la semana: día (Calendar.WEDNESDAY...), hora, minuto y texto. */
data class WeekendSlot(val day: Int, val hour: Int, val minute: Int, val title: String, val text: String) {
    val id get() = day * 100 + hour
}

/** Cuándo recordar que hay que preparar las películas del fin de semana: miércoles 2 veces, jueves 4 y viernes en la mañana. */
object WeekendPlan {
    val slots: List<WeekendSlot> = listOf(
        WeekendSlot(Calendar.WEDNESDAY, 12, 0, "Fin de semana a la vista", "Ya es miércoles: empieza a pensar qué películas verán."),
        WeekendSlot(Calendar.WEDNESDAY, 19, 30, "Prepara el fin de semana", "Anota en «Quiero verla» las películas para el sábado y el domingo."),
        WeekendSlot(Calendar.THURSDAY, 9, 0, "Mañana ya es viernes", "Es jueves: elijan las películas del fin de semana."),
        WeekendSlot(Calendar.THURSDAY, 12, 30, "Películas del fin de semana", "Revisen los matches y las recomendaciones."),
        WeekendSlot(Calendar.THURSDAY, 17, 0, "Falta poco", "Dejen lista la lista de películas antes del viernes."),
        WeekendSlot(Calendar.THURSDAY, 20, 0, "Última llamada de jueves", "¿Ya eligieron qué ver este fin de semana?"),
        WeekendSlot(Calendar.FRIDAY, 9, 0, "¡Hoy es viernes!", "Terminen de elegir las películas del fin de semana."),
    )

    /** El próximo aviso estrictamente después de [now]: su hora en milisegundos y cuál es. */
    fun next(now: Long, zone: TimeZone = TimeZone.getDefault()): Pair<Long, WeekendSlot> {
        var best: Pair<Long, WeekendSlot>? = null
        for (weeks in 0..1) for (slot in slots) {
            val c = Calendar.getInstance(zone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, slot.hour); set(Calendar.MINUTE, slot.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                // Mueve al día de la semana de ese aviso (hoy o el próximo).
                val delta = (slot.day - get(Calendar.DAY_OF_WEEK) + 7) % 7
                add(Calendar.DAY_OF_YEAR, delta + 7 * weeks)
            }
            val t = c.timeInMillis
            if (t > now && (best == null || t < best.first)) best = t to slot
        }
        return best!!
    }

    /** El aviso que toca ahora: el más reciente que ya pasó, hace menos de [windowMs] (la alarma puede atrasarse). */
    fun due(now: Long, windowMs: Long = 90 * 60_000L, zone: TimeZone = TimeZone.getDefault()): WeekendSlot? {
        var best: Pair<Long, WeekendSlot>? = null
        for (weeks in -1..0) for (slot in slots) {
            val c = Calendar.getInstance(zone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, slot.hour); set(Calendar.MINUTE, slot.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                val delta = (slot.day - get(Calendar.DAY_OF_WEEK) - 7) % 7 // hacia atrás o hoy
                add(Calendar.DAY_OF_YEAR, delta + 7 * (weeks + 1))
            }
            val t = c.timeInMillis
            if (t <= now && now - t <= windowMs && (best == null || t > best.first)) best = t to slot
        }
        return best?.second
    }
}

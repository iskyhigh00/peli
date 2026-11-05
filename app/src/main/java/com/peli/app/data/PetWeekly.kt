package com.peli.app.data

import java.util.Calendar
import java.util.Random
import java.util.TimeZone

/** Una película que Chuba o Pepa recomendaron, con su nota y su explicación. */
data class PetRec(
    val pet: String,
    val movieId: Int,
    val title: String,
    val posterPath: String?,
    val year: String,
    val genres: String?,
    val rating: Double?,
    val score: Int,
    val reason: String,
    val at: Long,
) {
    val petEnum get() = Pet.entries.firstOrNull { it.name == pet } ?: Pet.Chuba
    fun toEntry() = Entry(
        movieId = movieId, owner = OWNER_ME, title = title, overview = "", posterPath = posterPath,
        year = year, genres = genres, rating = rating,
    )
}

/** Cuándo recomiendan las perritas: unas 3 veces por semana, en días y de a una al azar (pero siempre los mismos para esa semana). */
object PetWeekly {
    const val FROM_HOUR = 10
    const val TO_HOUR = 20
    const val PER_WEEK = 3

    /** Los 3 días de esa semana (Calendar.SUNDAY..SATURDAY) en que toca recomendar. */
    fun daysOfWeek(year: Int, week: Int): Set<Int> {
        val days = (1..7).toMutableList()
        val rnd = Random(year * 100L + week)
        for (i in days.indices.reversed()) { val j = rnd.nextInt(i + 1); val t = days[i]; days[i] = days[j]; days[j] = t }
        return days.take(PER_WEEK).toSet()
    }

    fun dayKey(now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(zone).apply { timeInMillis = now }
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** ¿Toca recomendar ahora? Día elegido de la semana, en horario de día y solo una vez ese día. */
    fun due(now: Long, lastDayKey: String?, zone: TimeZone = TimeZone.getDefault()): Boolean {
        val c = Calendar.getInstance(zone).apply { timeInMillis = now }
        if (c.get(Calendar.DAY_OF_WEEK) !in daysOfWeek(c.get(Calendar.YEAR), c.get(Calendar.WEEK_OF_YEAR))) return false
        if (c.get(Calendar.HOUR_OF_DAY) !in FROM_HOUR..TO_HOUR) return false
        return dayKey(now, zone) != lastDayKey
    }

    /** Quién recomienda ese día (al azar, pero fijo para ese día). */
    fun petFor(now: Long, zone: TimeZone = TimeZone.getDefault()): Pet =
        dayKey(now, zone).hashCode().let { h0 ->
            // Mezcla de bits: las semillas de días seguidos darían casi siempre el mismo resultado.
            var h = h0 * -1640531527
            h = h xor (h ushr 15); h *= 0x2c1b3c6d; h = h xor (h ushr 12)
            if (h and 1 == 0) Pet.Chuba else Pet.Pepa
        }

    /** Texto del aviso: cada una habla a su manera. */
    fun message(rec: PetRec): Pair<String, String> {
        val who = rec.petEnum.label
        val title = "$who te recomienda «${rec.title}»"
        val text = "${rec.score}/10 · ${rec.reason}"
        return title to text
    }
}

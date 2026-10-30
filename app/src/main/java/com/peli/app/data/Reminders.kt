package com.peli.app.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Aviso de un estreno cercano: [stage] 7 = faltan de 2 a 7 días, 1 = mañana, 0 = hoy. */
data class Reminder(val entry: Entry, val stage: Int, val days: Int) {
    val key get() = Reminders.key(entry.movieId, stage)
    val title get() = entry.displayTitle
    val text get() = when (stage) {
        0 -> "Se estrena hoy"
        1 -> "Se estrena mañana"
        else -> "Se estrena en $days días" + (ReleaseInfo.format(entry.releaseDate)?.let { " ($it)" } ?: "")
    }
}

/** Qué películas de "Quiero verla" merecen un aviso hoy; cada etapa se avisa una sola vez. */
object Reminders {
    private val stages = listOf(7, 1, 0)

    fun key(movieId: Int, stage: Int) = "$movieId:$stage"

    private fun epochDay(date: String): Long? {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
        return runCatching { f.parse(date)!!.time / 86_400_000L }.getOrNull()
    }

    /** Días desde [today] (yyyy-MM-dd) hasta [date]; null si alguna no se entiende. */
    fun daysUntil(today: String, date: String?): Int? {
        val a = epochDay(today) ?: return null
        val b = epochDay(date ?: return null) ?: return null
        return (b - a).toInt()
    }

    fun stageFor(days: Int): Int? = when {
        days == 0 -> 0
        days == 1 -> 1
        days in 2..7 -> 7
        else -> null
    }

    fun due(mine: List<Entry>, today: String, notified: Set<String>): List<Reminder> = mine.mapNotNull { e ->
        if (!e.watchlist || e.seen || e.disliked) return@mapNotNull null
        val days = daysUntil(today, e.releaseDate) ?: return@mapNotNull null
        val stage = stageFor(days) ?: return@mapNotNull null
        // Si ya se avisó esta etapa o una más urgente, no se vuelve atrás.
        if (stages.filter { it <= stage }.any { key(e.movieId, it) in notified }) return@mapNotNull null
        Reminder(e, stage, days)
    }.sortedBy { it.days }

    /** Al avisar una etapa se dan por avisadas también las anteriores (p. ej. al instalar a 3 días del estreno). */
    fun toRecord(r: Reminder): Set<String> = stages.filter { it >= r.stage }.map { key(r.entry.movieId, it) }.toSet()
}

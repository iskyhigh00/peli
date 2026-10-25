package com.peli.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReleaseInfo {
    private val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Las fechas ISO se comparan como texto. Sin fecha exacta, se usa el año. */
    fun isUpcoming(date: String?, year: String): Boolean {
        if (!date.isNullOrBlank()) return date > today()
        val y = year.toIntOrNull() ?: return false
        return y > Calendar.getInstance().get(Calendar.YEAR)
    }

    /** "2026-11-12" -> "12 nov 2026"; null si no se puede interpretar. */
    fun format(date: String?): String? {
        val parts = date?.split("-") ?: return null
        if (parts.size != 3) return null
        val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        return "$day ${months[month - 1]} ${parts[0]}"
    }
}

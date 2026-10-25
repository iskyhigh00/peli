package com.peli.app.data

/** Rango de años de estreno (ambos extremos incluidos). */
data class YearRange(val from: Int, val to: Int) {
    init {
        require(from <= to) { "from ($from) debe ser menor o igual que to ($to)" }
    }

    /** ¿El año (los 4 primeros dígitos del texto) está dentro del rango? Sin año, no. */
    operator fun contains(year: String): Boolean {
        val y = year.take(4).toIntOrNull() ?: return false
        return y in from..to
    }

    /** "1990–1999", o "2015" si es un solo año. */
    fun label() = if (from == to) "$from" else "$from–$to"

    /** Formato para guardar en SavedStateHandle. */
    fun encode() = "$from-$to"

    /** Fechas para el filtro por fecha de estreno de TMDB. */
    val startDate get() = "$from-01-01"
    val endDate get() = "$to-12-31"

    companion object {
        const val MIN_YEAR = 1920

        /** El selector llega hasta dos años en el futuro para poder ver próximos estrenos. */
        fun maxYear(currentYear: Int) = currentYear + 2

        fun decode(text: String): YearRange? {
            val parts = text.split("-")
            if (parts.size != 2) return null
            val a = parts[0].toIntOrNull() ?: return null
            val b = parts[1].toIntOrNull() ?: return null
            return if (a <= b) YearRange(a, b) else null
        }

        /** Atajos del selector (etiqueta, rango), usando el año actual como referencia. */
        fun presets(currentYear: Int): List<Pair<String, YearRange>> = listOf(
            "Últimos 5 años" to YearRange(currentYear - 4, currentYear),
            "Este año y próximos" to YearRange(currentYear, maxYear(currentYear)),
            "2020s" to YearRange(2020, 2029),
            "2010s" to YearRange(2010, 2019),
            "2000s" to YearRange(2000, 2009),
            "90s" to YearRange(1990, 1999),
            "80s" to YearRange(1980, 1989),
            "Clásicas" to YearRange(MIN_YEAR, 1979),
        )
    }
}

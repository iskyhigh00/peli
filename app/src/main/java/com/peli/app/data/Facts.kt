package com.peli.app.data

/**
 * Datos de una película que no vienen en las listas de TMDB: palabras clave, director y reparto (TMDB) y
 * valoraciones y premios (OMDb). Se piden una sola vez y se guardan en el teléfono.
 * [tmdbAt] / [omdbAt] valen 0 mientras ese origen no se haya consultado con éxito.
 */
data class MovieFacts(
    val movieId: Int,
    val imdbId: String? = null,
    val keywords: List<String> = emptyList(),
    val director: String? = null,
    val actors: List<String> = emptyList(),
    /** Id de TMDB del director (para ver su filmografía). Las filas guardadas antes de existir este dato lo traen null. */
    val directorId: Int? = null,
    val tmdbAt: Long = 0,
    val imdbRating: Double? = null,
    val imdbVotes: Int? = null,
    val rottenTomatoes: Int? = null,
    val metacritic: Int? = null,
    val oscarWins: Int = 0,
    val oscarNominations: Int = 0,
    val wins: Int = 0,
    val nominations: Int = 0,
    val omdbAt: Long = 0,
) {
    val hasOmdb get() = omdbAt > 0
    val hasTmdb get() = tmdbAt > 0

    /** Hay que (volver a) pedir los datos de TMDB: nunca se pidieron, o se guardaron sin el id del director. */
    val needsTmdb get() = !hasTmdb || (director != null && directorId == null)
    val oscarNominated get() = oscarWins > 0 || oscarNominations > 0
}

/** Lectura del texto de premios de OMDb: "Won 3 Oscars. 150 wins & 200 nominations total". */
object Awards {
    data class Parsed(val oscarWins: Int, val oscarNominations: Int, val wins: Int, val nominations: Int)

    private val oscarWon = Regex("""Won ([\d,]+) Oscars?""", RegexOption.IGNORE_CASE)
    private val oscarNominated = Regex("""Nominated for ([\d,]+) Oscars?""", RegexOption.IGNORE_CASE)
    private val leadWon = Regex("""^\s*Won ([\d,]+) """, RegexOption.IGNORE_CASE)
    private val leadNominated = Regex("""^\s*Nominated for ([\d,]+) """, RegexOption.IGNORE_CASE)
    private val winsTotal = Regex("""([\d,]+) wins?""", RegexOption.IGNORE_CASE)
    private val nominationsTotal = Regex("""([\d,]+) nominations?""", RegexOption.IGNORE_CASE)

    private fun Regex.firstNumber(text: String): Int =
        find(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0

    fun parse(text: String?): Parsed {
        val t = text?.trim().orEmpty()
        if (t.isEmpty() || t.equals("N/A", ignoreCase = true)) return Parsed(0, 0, 0, 0)
        val oscarWins = oscarWon.firstNumber(t)
        val oscarNoms = oscarNominated.firstNumber(t)
        // "Won 2 BAFTA Awards." sin totales: al menos esos premios cuentan.
        val wins = maxOf(winsTotal.firstNumber(t), leadWon.firstNumber(t), oscarWins)
        val nominations = maxOf(nominationsTotal.firstNumber(t), leadNominated.firstNumber(t), oscarNoms)
        return Parsed(oscarWins, oscarNoms, wins, nominations)
    }
}

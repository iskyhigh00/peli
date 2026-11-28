package com.peli.app.data

/**
 * Mood para elegir qué ver: cada uno agrupa géneros de TMDB. «Encaja» si tiene al menos un género de [include]
 * y ninguno de [exclude]. Una película sin géneros conocidos solo aparece en [Cualquiera].
 */
enum class Mood(val label: String, private val include: Set<Int>, private val exclude: Set<Int> = emptySet()) {
    Tranqui("Tranqui", setOf(35, 10749, 16, 10751, 10402, 99), setOf(27, 53, 28, 80, 10752, 9648)),
    Reir("Reírnos", setOf(35), setOf(27)),
    Accion("Acción", setOf(28, 12, 10752)),
    Terror("Terror", setOf(27)),
    Misterio("Misterio", setOf(9648, 53, 80)),
    Romantica("Romántica", setOf(10749)),
    Familiar("Animada / familiar", setOf(16, 10751)),
    Pensar("Para pensar", setOf(878, 18, 36, 99)),
    Cualquiera("Cualquiera", emptySet());

    fun accepts(genres: List<Int>): Boolean =
        if (this == Cualquiera) true else genres.any { it in include } && genres.none { it in exclude }
}

/**
 * Qué proponer para ver ahora: de los candidatos de Juntos, los que encajan con el mood y (si se pide) los que
 * ya están disponibles. Primero las que les interesan a los dos, luego las mejor puntuadas por TMDB.
 */
fun moodPicks(matches: List<Match>, available: Set<Int>, mood: Mood, onlyAvailable: Boolean): List<Match> =
    matches
        .filter { mood.accepts(it.entry.genreIds) && (!onlyAvailable || it.entry.movieId in available) }
        .sortedWith(compareByDescending<Match> { it.both }.thenByDescending { it.entry.rating ?: 0.0 }.thenBy { it.entry.title })

/** Cuántas opciones hay para cada mood (para no ofrecer uno vacío sin avisar). */
fun moodCounts(matches: List<Match>, available: Set<Int>, onlyAvailable: Boolean): Map<Mood, Int> =
    Mood.entries.associateWith { moodPicks(matches, available, it, onlyAvailable).size }

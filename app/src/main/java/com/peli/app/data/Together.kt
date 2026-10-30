package com.peli.app.data

/** Película candidata para ver en pareja. */
data class Match(val entry: Entry, val iWant: Boolean, val partnerWants: Boolean) {
    val both get() = iWant && partnerWants
}

/**
 * Candidatas para ver juntos: a al menos una le interesa ("quiero verla"), ninguna la
 * vio ni la descartó. Primero las que les interesan a las dos.
 */
fun computeMatches(mine: List<Entry>, partner: List<Entry>): List<Match> {
    val m = mine.associateBy { it.movieId }
    val p = partner.associateBy { it.movieId }
    return (m.keys + p.keys).mapNotNull { id ->
        val me = m[id]
        val her = p[id]
        if (me?.seen == true || her?.seen == true) return@mapNotNull null
        if (me?.disliked == true || her?.disliked == true) return@mapNotNull null
        val iWant = me?.watchlist == true
        val herWant = her?.watchlist == true
        if (!iWant && !herWant) return@mapNotNull null
        Match((me ?: her)!!, iWant, herWant)
    }.sortedWith(compareByDescending<Match> { it.both }.thenBy { it.entry.title })
}

/** Qué matches avisar (la animación de «¡Match!») y cuáles dar por vistos al conectar con alguien. */
object MatchAnnouncer {
    /** Los que ahora les interesan a las dos personas y todavía no se avisaron en este contexto. */
    fun fresh(matches: List<Match>, announced: Set<Int>): List<Entry> =
        matches.filter { it.both && it.entry.movieId !in announced }.map { it.entry }

    /** Hasta esta cantidad de coincidencias al conectar se celebran igual; más que eso sería una avalancha. */
    const val CELEBRATE_UP_TO = 3

    /**
     * Lo que ya coincidía en el momento de conectar con esa persona (amigo o sala). Si son muchas, se dan por
     * avisadas para no celebrar decenas de matches de golpe (se ven igual en «Juntos»); si son pocas, se avisan.
     * Debe llamarse cuando ya llegó la lista de la otra persona (con una lista vacía no hay nada que comparar).
     */
    fun baseline(mine: List<Entry>, partner: List<Entry>): List<Int> {
        val existing = computeMatches(mine, partner).filter { it.both }.map { it.entry.movieId }
        return if (existing.size > CELEBRATE_UP_TO) existing else emptyList()
    }
}

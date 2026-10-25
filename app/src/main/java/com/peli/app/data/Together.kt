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

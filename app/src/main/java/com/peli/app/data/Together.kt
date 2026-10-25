package com.peli.app.data

/** Película candidata para ver en pareja. */
data class Match(val entry: Entry, val iLike: Boolean, val partnerLikes: Boolean) {
    val both get() = iLike && partnerLikes
}

/**
 * Candidatas para ver juntos: a al menos una le gusta (favorita), ninguna la
 * vio ni la descartó. Primero las que les gustan a las dos.
 */
fun computeMatches(mine: List<Entry>, partner: List<Entry>): List<Match> {
    val m = mine.associateBy { it.movieId }
    val p = partner.associateBy { it.movieId }
    return (m.keys + p.keys).mapNotNull { id ->
        val me = m[id]
        val her = p[id]
        if (me?.seen == true || her?.seen == true) return@mapNotNull null
        if (me?.disliked == true || her?.disliked == true) return@mapNotNull null
        val iLike = me?.favorite == true
        val herLike = her?.favorite == true
        if (!iLike && !herLike) return@mapNotNull null
        Match((me ?: her)!!, iLike, herLike)
    }.sortedWith(compareByDescending<Match> { it.both }.thenBy { it.entry.title })
}

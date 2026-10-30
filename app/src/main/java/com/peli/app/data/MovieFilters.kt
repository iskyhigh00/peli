package com.peli.app.data

enum class OscarFilter(val label: String) {
    Any("Cualquiera"),
    Nominated("Nominada o ganadora"),
    Won("Ganadora"),
}

/**
 * Filtros de la lista principal por valoraciones y premios. Cero (o [OscarFilter.Any]) significa "sin filtro".
 * Las valoraciones de IMDb / Rotten Tomatoes / Metacritic y los premios vienen de OMDb: una película sin ese dato
 * no pasa el filtro que lo pide.
 */
data class MovieFilters(
    val minTmdb: Double = 0.0,
    val minImdb: Double = 0.0,
    val minRottenTomatoes: Int = 0,
    val minMetacritic: Int = 0,
    val oscar: OscarFilter = OscarFilter.Any,
    val minWins: Int = 0,
    val minNominations: Int = 0,
    /** Solo películas de este director (id de TMDB; 0 = sin filtro) y su nombre para mostrarlo. */
    val directorId: Int = 0,
    val directorName: String = "",
) {
    val isActive get() = this != NONE

    /** ¿Algún filtro necesita datos de OMDb (y por tanto consultas extra)? */
    val needsOmdb
        get() = minImdb > 0 || minRottenTomatoes > 0 || minMetacritic > 0 || oscar != OscarFilter.Any ||
            minWins > 0 || minNominations > 0

    /**
     * Antes de gastar una consulta a OMDb: ¿vale la pena revisar esta película? Se descartan las que por su nota de TMDB
     * (que ya viene en la lista) casi seguro no cumplirían: una nota muy baja en TMDB no llega a IMDb alto ni a Oscar.
     * Es una aproximación para cuidar el límite diario de OMDb; sin nota no se prejuzga.
     */
    fun worthChecking(rating: Double?): Boolean {
        if (!needsOmdb) return true
        val r = rating ?: return true
        if (minImdb > 0 && r < minImdb - IMDB_MARGIN) return false
        if (minRottenTomatoes >= 75 && r < CRITIC_FLOOR) return false
        if (minMetacritic >= 70 && r < CRITIC_FLOOR) return false
        if ((oscar != OscarFilter.Any || minWins >= 5 || minNominations >= 5) && r < AWARD_FLOOR) return false
        return true
    }

    /** La nota de TMDB ya viene en la lista: se comprueba sin consultar nada más. */
    fun acceptsTmdb(rating: Double?): Boolean = minTmdb <= 0 || (rating ?: 0.0) >= minTmdb

    fun accepts(rating: Double?, facts: MovieFacts?): Boolean {
        if (!acceptsTmdb(rating)) return false
        if (!needsOmdb) return true
        if (facts == null || !facts.hasOmdb) return false
        if (minImdb > 0 && (facts.imdbRating ?: return false) < minImdb) return false
        if (minRottenTomatoes > 0 && (facts.rottenTomatoes ?: return false) < minRottenTomatoes) return false
        if (minMetacritic > 0 && (facts.metacritic ?: return false) < minMetacritic) return false
        when (oscar) {
            OscarFilter.Any -> {}
            OscarFilter.Nominated -> if (!facts.oscarNominated) return false
            OscarFilter.Won -> if (facts.oscarWins <= 0) return false
        }
        if (facts.wins < minWins) return false
        if (facts.nominations < minNominations) return false
        return true
    }

    /** Resumen corto de lo que está activo, para mostrar bajo los chips. */
    fun summary(): String = listOfNotNull(
        minTmdb.takeIf { it > 0 }?.let { "TMDB ≥ ${Ratings.formatScore(it)}" },
        minImdb.takeIf { it > 0 }?.let { "IMDb ≥ ${Ratings.formatScore(it)}" },
        minRottenTomatoes.takeIf { it > 0 }?.let { "Rotten Tomatoes ≥ $it%" },
        minMetacritic.takeIf { it > 0 }?.let { "Metacritic ≥ $it" },
        when (oscar) {
            OscarFilter.Any -> null
            OscarFilter.Nominated -> "Oscar: nominada o ganadora"
            OscarFilter.Won -> "Oscar: ganadora"
        },
        minWins.takeIf { it > 0 }?.let { "$it+ premios" },
        minNominations.takeIf { it > 0 }?.let { "$it+ nominaciones" },
        directorId.takeIf { it > 0 }?.let { "Director: ${directorName.ifBlank { "#$it" }}" },
    ).joinToString(" · ")

    /** Cantidad de filtros activos (para el número del chip). */
    fun count(): Int = listOf(
        minTmdb > 0, minImdb > 0, minRottenTomatoes > 0, minMetacritic > 0,
        oscar != OscarFilter.Any, minWins > 0, minNominations > 0, directorId > 0,
    ).count { it }

    fun encode(): String =
        "t=$minTmdb;i=$minImdb;r=$minRottenTomatoes;m=$minMetacritic;o=${oscar.name};w=$minWins;n=$minNominations;" +
            "d=$directorId;dn=${java.net.URLEncoder.encode(directorName, "UTF-8")}"

    companion object {
        val NONE = MovieFilters()
        private const val IMDB_MARGIN = 1.0
        private const val CRITIC_FLOOR = 6.0
        private const val AWARD_FLOOR = 5.5

        /** Lee lo guardado; cualquier campo ilegible vuelve a "sin filtro" (nunca falla). */
        fun decode(text: String?): MovieFilters {
            val values = text.orEmpty().split(';').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
            return MovieFilters(
                minTmdb = values["t"]?.toDoubleOrNull()?.takeIf { it in 0.0..10.0 } ?: 0.0,
                minImdb = values["i"]?.toDoubleOrNull()?.takeIf { it in 0.0..10.0 } ?: 0.0,
                minRottenTomatoes = values["r"]?.toIntOrNull()?.takeIf { it in 0..100 } ?: 0,
                minMetacritic = values["m"]?.toIntOrNull()?.takeIf { it in 0..100 } ?: 0,
                oscar = OscarFilter.entries.firstOrNull { it.name == values["o"] } ?: OscarFilter.Any,
                minWins = values["w"]?.toIntOrNull()?.takeIf { it >= 0 } ?: 0,
                minNominations = values["n"]?.toIntOrNull()?.takeIf { it >= 0 } ?: 0,
                directorId = values["d"]?.toIntOrNull()?.takeIf { it > 0 } ?: 0,
                directorName = runCatching { java.net.URLDecoder.decode(values["dn"].orEmpty(), "UTF-8") }.getOrDefault("").take(120),
            )
        }
    }
}

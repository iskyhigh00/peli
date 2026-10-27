package com.peli.app.data

/** De dónde mostrar valoraciones y reseñas (el usuario elige en Ajustes). */
enum class RatingSource(val label: String, val hint: String) {
    Tmdb("TMDB", "Nota del público en TMDB"),
    Imdb("IMDb", "Requiere clave de OMDb"),
    RottenTomatoes("Rotten Tomatoes", "Crítica; requiere clave de OMDb"),
    Metacritic("Metacritic", "Requiere clave de OMDb"),
    Partner("Mi pareja", "Su nota, si ya calificó la película"),
    Reviews("Reseñas de usuarios", "Reseñas escritas en TMDB (suelen estar en inglés)");

    /** Estas tres salen de OMDb y necesitan OMDB_API_KEY. */
    val needsOmdb get() = this == Imdb || this == RottenTomatoes || this == Metacritic

    companion object {
        /** Sin elección guardada, todas activas. Nombres desconocidos se ignoran. */
        fun decode(names: Set<String>?): Set<RatingSource> =
            if (names == null) entries.toSet() else names.mapNotNull { n -> entries.firstOrNull { it.name == n } }.toSet()
    }
}

/** Reseña escrita por un usuario de TMDB. */
data class Review(val author: String, val rating: Int?, val text: String, val date: String)

/** Valoraciones de una película según cada fuente (null = esa fuente no tiene dato). */
data class MovieInsights(
    val tmdbRating: Double?,
    val tmdbVotes: Int,
    val imdbRating: Double?,
    val imdbVotes: Int?,
    val rottenTomatoes: Int?,
    val metacritic: Int?,
    val reviews: List<Review>,
)

/** Estado de la carga de valoraciones de una película. */
sealed interface InsightsState {
    data object Loading : InsightsState
    data class Ready(val data: MovieInsights) : InsightsState
    data class Failed(val message: String) : InsightsState
}

/** Lectura de los textos que devuelven TMDB y OMDb. */
object Ratings {
    /** "91%" -> 91 */
    fun parsePercent(text: String?): Int? = text?.trim()?.removeSuffix("%")?.toIntOrNull()?.takeIf { it in 0..100 }

    /** "73" o "73/100" -> 73 */
    fun parseMetascore(text: String?): Int? = text?.trim()?.substringBefore("/")?.toIntOrNull()?.takeIf { it in 0..100 }

    /** "2,345" -> 2345 */
    fun parseVotes(text: String?): Int? = text?.replace(",", "")?.replace(".", "")?.trim()?.toIntOrNull()

    /** 950 -> "950", 2345 -> "2.3K", 1_250_000 -> "1.3M" */
    fun formatVotes(votes: Int): String = when {
        votes >= 1_000_000 -> "%.1fM".format(java.util.Locale.US, votes / 1_000_000.0)
        votes >= 1_000 -> "%.1fK".format(java.util.Locale.US, votes / 1_000.0)
        else -> votes.toString()
    }

    /** 7.25 -> "7.3", 8.0 -> "8.0" */
    fun formatScore(score: Double): String = "%.1f".format(java.util.Locale.US, score)

    /** Quita saltos de línea repetidos y el formato markdown más común; corta con "…" si es muy largo. */
    fun cleanReview(text: String, maxChars: Int = 700): String {
        val cleaned = text
            .replace("\r", "")
            .replace(Regex("[*_`#>]+"), "")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()
        return if (cleaned.length <= maxChars) cleaned else cleaned.take(maxChars).trimEnd() + "…"
    }
}

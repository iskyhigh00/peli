package com.peli.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Una serie marcada por mí. Mismas marcas que las películas (quiero verla / la vi / me gustó / no me gusta / nota),
 * pero en su propia tabla: las series no se mezclan con las películas ni con lo que se comparte de ellas.
 * Por ahora es solo local (aún no se sincroniza con la pareja).
 */
@Entity(tableName = "series", primaryKeys = ["seriesId"])
data class SeriesEntry(
    val seriesId: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val year: String,
    /** yyyy-MM-dd del primer episodio. */
    val firstAirDate: String? = null,
    @ColumnInfo(name = "liked") val liked: Boolean = false,
    val seen: Boolean = false,
    val disliked: Boolean = false,
    val watchlist: Boolean = false,
    val genres: String? = null,
    val rating: Double? = null,
    val myRating: Int = 0,
    val updatedAt: Long = 0,
    val originalTitle: String? = null,
) {
    val isEmpty get() = !liked && !seen && !disliked && !watchlist
    val displayTitle: String get() = TitleFormat.display(title, originalTitle)
}

// Mismas reglas que las películas: «quiero verla» solo hasta que se ve; «me gustó» implica «la vi».
fun SeriesEntry.toggleWatch() = when {
    watchlist -> copy(watchlist = false)
    seen -> this
    else -> copy(watchlist = true, disliked = false)
}

fun SeriesEntry.toggleLiked() =
    if (liked) copy(liked = false) else copy(liked = true, seen = true, disliked = false, watchlist = false)

fun SeriesEntry.toggleSeen() =
    if (seen) copy(seen = false, liked = false, myRating = 0) else copy(seen = true, watchlist = false)

fun SeriesEntry.withRating(score: Int): SeriesEntry {
    val n = score.coerceIn(0, 10)
    return if (n == 0) copy(myRating = 0) else copy(myRating = n, seen = true, watchlist = false)
}

fun SeriesEntry.toggleDisliked() =
    if (disliked) copy(disliked = false) else copy(disliked = true, liked = false, watchlist = false)

fun SeriesEntry.stateLabel(): String? = when {
    watchlist -> "Quiero verla"
    liked -> "La vi · me gustó"
    disliked && seen -> "La vi · no me gustó"
    disliked -> "No me gusta"
    seen -> "La vi"
    else -> null
}

/** Serie tal como la entrega TMDB (los campos se llaman distinto que en películas: name / first_air_date). */
data class TmdbTv(
    val id: Int,
    val name: String? = null,
    val original_name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val first_air_date: String? = null,
    val genre_ids: List<Int>? = null,
    val vote_average: Double? = null,
    val vote_count: Int? = null,
)

data class TmdbTvPage(val results: List<TmdbTv> = emptyList(), val page: Int = 1, val total_pages: Int = 1)

fun TmdbTv.toSeries() = SeriesEntry(
    seriesId = id,
    title = name.orEmpty(),
    overview = overview.orEmpty(),
    posterPath = poster_path,
    year = first_air_date.orEmpty().take(4),
    firstAirDate = first_air_date?.takeIf { it.isNotBlank() },
    genres = genre_ids?.takeIf { it.isNotEmpty() }?.joinToString(","),
    rating = vote_average,
    originalTitle = original_name?.trim().orEmpty(),
)

/** Lo que muestra la lista de series: filtra por marca. */
enum class SeriesFilter(val label: String) {
    All("Todas"), Want("Quiero verlas"), Seen("Vistas"), Liked("Me gustaron");

    fun accepts(s: SeriesEntry?) = when (this) {
        All -> true
        Want -> s?.watchlist == true
        Seen -> s?.seen == true
        Liked -> s?.liked == true
    }
}

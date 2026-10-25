package com.peli.app.ui

import com.peli.app.data.Entry

/** Filtros de "Buscar"; [accepts] recibe mi marca de la película (null si no la marqué). */
enum class SearchFilter(val label: String) {
    All("Todas"),
    Want("Quiero ver"),
    Liked("Me gustaron"),
    Seen("Vistas"),
    Disliked("No me gustan"),
    Unmarked("Sin marcar");

    fun accepts(mark: Entry?): Boolean = when (this) {
        All -> true
        Want -> mark?.watchlist == true
        Liked -> mark?.liked == true
        Seen -> mark?.seen == true
        Disliked -> mark?.disliked == true
        Unmarked -> mark == null
    }
}

/** Filtros de "Mis pelis". */
enum class MineFilter(val label: String) {
    All("Todas"),
    Want("Quiero ver"),
    Liked("Me gustaron"),
    Seen("Vistas"),
    Disliked("No me gustan");

    fun accepts(e: Entry): Boolean = when (this) {
        All -> true
        Want -> e.watchlist
        Liked -> e.liked
        Seen -> e.seen
        Disliked -> e.disliked
    }
}

/** Quita las películas que ya tienen alguna marca mía (quiero verla, vista, me gustó, no me gusta). */
fun List<Entry>.withoutMarked(marked: Collection<Entry>): List<Entry> {
    val ids = marked.filter { !it.isEmpty }.map { it.movieId }.toSet()
    return filter { it.movieId !in ids }
}

/** Orden de "Mis pelis". Siempre desempata por título para que el orden sea estable. */
enum class MineSort(val label: String) { Recent("Recientes"), Title("Título A–Z"), Year("Año"), Rating("Nota") }

fun List<Entry>.orderedBy(sort: MineSort): List<Entry> {
    val byTitle = compareBy<Entry> { it.title.lowercase() }
    return when (sort) {
        MineSort.Recent -> sortedWith(compareByDescending<Entry> { it.updatedAt }.then(byTitle))
        MineSort.Title -> sortedWith(byTitle)
        MineSort.Year -> sortedWith(compareByDescending<Entry> { it.year.toIntOrNull() ?: 0 }.then(byTitle))
        MineSort.Rating -> sortedWith(compareByDescending<Entry> { it.rating ?: 0.0 }.then(byTitle))
    }
}

package com.peli.app.data

import androidx.room.Entity

const val OWNER_ME = "ME"
const val OWNER_PARTNER = "PARTNER"

/** Una película marcada por una persona (yo o mi pareja). */
@Entity(tableName = "entries", primaryKeys = ["movieId", "owner"])
data class Entry(
    val movieId: Int,
    val owner: String,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val year: String,
    val favorite: Boolean = false,
    val seen: Boolean = false,
    val disliked: Boolean = false,
    /** yyyy-MM-dd de TMDB; null si se guardó antes de existir este campo o TMDB no la tiene. */
    val releaseDate: String? = null,
) {
    val isEmpty get() = !favorite && !seen && !disliked

    /** Aún no se estrena (fecha futura; sin fecha, año posterior al actual). */
    val isUpcoming get() = ReleaseInfo.isUpcoming(releaseDate, year)

    /** Fecha "12 nov 2026" si es próximo estreno; si no, solo el año. */
    val dateLabel get() = if (isUpcoming) ReleaseInfo.format(releaseDate) ?: year else year
}

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
) {
    val isEmpty get() = !favorite && !seen && !disliked
}

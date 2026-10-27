package com.peli.app.data

import android.content.Context

/** Qué fuentes de valoraciones y reseñas quiere ver el usuario (SharedPreferences). Por defecto, todas. */
class SourcePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("peli_sources", Context.MODE_PRIVATE)

    fun load(): Set<RatingSource> = RatingSource.decode(prefs.getStringSet(KEY, null))

    fun save(sources: Set<RatingSource>) {
        prefs.edit().putStringSet(KEY, sources.map { it.name }.toSet()).apply()
    }

    private companion object {
        const val KEY = "enabled"
    }
}

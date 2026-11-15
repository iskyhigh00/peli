package com.peli.app.data

import android.content.Context

/** Títulos en inglés ya consultados (id de película -> título), guardados en el teléfono para no pedirlos otra vez. */
class EnglishTitles(context: Context) {
    private val prefs = context.getSharedPreferences("peli_en_titles", Context.MODE_PRIVATE)

    fun all(): Map<Int, String> = prefs.all.mapNotNull { (k, v) -> (k.toIntOrNull() ?: return@mapNotNull null) to (v as? String ?: return@mapNotNull null) }.toMap()
    fun put(id: Int, title: String) = prefs.edit().putString(id.toString(), title).apply()
}

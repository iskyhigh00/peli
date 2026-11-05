package com.peli.app.data

import android.content.Context

/** Idioma en que TMDB entrega títulos y sinopsis. TMDB no tiene "Latinoamérica" en general: México es lo más cercano. */
object Lang {
    @Volatile var tag: String = "es-MX"

    val options = listOf(
        "es-MX" to "Latinoamérica",
        "es-CL" to "Chile",
        "es-ES" to "España",
    )
}

class LanguagePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("peli_lang", Context.MODE_PRIVATE)

    var tag: String
        get() = prefs.getString("tag", null)?.takeIf { t -> Lang.options.any { it.first == t } } ?: "es-MX"
        set(v) = prefs.edit().putString("tag", v).apply()

    /** Idioma con el que ya se actualizaron los textos de lo que tengo marcado (null = aún no). */
    var textsTag: String?
        get() = prefs.getString("texts_tag", null)
        set(v) = prefs.edit().putString("texts_tag", v).apply()
}

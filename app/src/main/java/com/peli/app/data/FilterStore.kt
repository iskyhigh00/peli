package com.peli.app.data

import android.content.Context

/** Filtros elegidos, guardados en el teléfono: siguen ahí al cerrar y abrir la app hasta que se quiten. */
class FilterStore(context: Context) {
    private val prefs = context.getSharedPreferences("peli_filters", Context.MODE_PRIVATE)

    /** Rango de años de "Buscar" y de "Mis pelis", en el formato de [YearRange.encode] ("" = todos). */
    var searchYears: String
        get() = prefs.getString("years_search", "").orEmpty()
        set(value) = prefs.edit().putString("years_search", value).apply()

    var mineYears: String
        get() = prefs.getString("years_mine", "").orEmpty()
        set(value) = prefs.edit().putString("years_mine", value).apply()

    var movie: MovieFilters
        get() = MovieFilters.decode(prefs.getString("movie", ""))
        set(value) = prefs.edit().putString("movie", value.encode()).apply()
}

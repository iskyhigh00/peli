package com.peli.app.data

import android.content.Context

/**
 * Qué películas ya se mostraron en la lista principal (de más antigua a más reciente), guardado en el
 * teléfono. Al actualizar se evita todo lo mostrado hasta ahora; solo cuando no alcanza el material nuevo
 * se vuelve a permitir lo más antiguo, de modo que lo repetido tarda muchas actualizaciones en volver.
 */
class FeedHistory(context: Context) {
    private val prefs = context.getSharedPreferences("peli_feed", Context.MODE_PRIVATE)

    @Synchronized
    fun ids(): List<Int> = prefs.getString("shown", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    @Synchronized
    fun record(shown: List<Int>) {
        val next = FeedAvoid.append(ids(), shown)
        prefs.edit().putString("shown", next.joinToString(",")).apply()
    }
}

object FeedAvoid {
    /** Cuántas películas se recuerdan (unas 20 actualizaciones de 40). */
    const val CAPACITY = 800

    /** Agrega lo recién mostrado al final, sin repetir ids, y olvida lo más antiguo al pasar [CAPACITY]. */
    fun append(history: List<Int>, shown: List<Int>, capacity: Int = CAPACITY): List<Int> {
        val fresh = shown.toSet()
        return (history.filter { it !in fresh } + shown.distinct()).takeLast(capacity)
    }

    /**
     * Conjuntos de ids a evitar, del más estricto (todo el historial) al más permisivo (nada):
     * se prueba cada uno hasta tener suficiente material nuevo.
     */
    fun levels(history: List<Int>): List<Set<Int>> {
        if (history.isEmpty()) return listOf(emptySet())
        val out = ArrayList<Set<Int>>()
        var keep = history.size
        while (keep >= 20) {
            out += history.takeLast(keep).toSet()
            keep /= 2
        }
        out += emptySet()
        return out
    }
}

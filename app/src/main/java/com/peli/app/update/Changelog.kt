package com.peli.app.update

import android.content.Context
import com.peli.app.APP_VERSION

/** Novedades por versión (orden ascendente). Agregar una entrada por cada versión nueva. */
object Changelog {
    val entries: Map<String, List<String>> = linkedMapOf(
        "1.0.0" to listOf(
            "Rediseño completo con Material 3 Expressive.",
        ),
        "1.0.1" to listOf(
            "La app se actualiza sola desde GitHub.",
            "Versionado simple: un dígito por campo (hasta 9.9.9).",
        ),
        "1.0.2" to listOf(
            "Las películas que aún no se estrenan muestran \"Próximo estreno\" y su fecha.",
        ),
    )

    private const val PREFS = "peli_prefs"
    private const val KEY_LAST_SEEN = "last_seen_version"

    /**
     * Novedades posteriores a la última versión vista, hasta la instalada. Guarda la versión
     * actual como vista. En la primera instalación no muestra nada.
     */
    fun takeUnseen(context: Context): List<Pair<String, List<String>>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getString(KEY_LAST_SEEN, null)
        prefs.edit().putString(KEY_LAST_SEEN, APP_VERSION).apply()
        if (last == null) return emptyList()
        return entries.entries
            .filter { Updater.isNewer(it.key, last) && !Updater.isNewer(it.key, APP_VERSION) }
            .map { it.key to it.value }
            .reversed()
    }
}

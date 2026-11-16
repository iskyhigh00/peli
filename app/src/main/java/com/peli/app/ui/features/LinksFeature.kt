package com.peli.app.ui.features

import com.peli.app.data.Entry
import com.peli.app.data.ExportList
import com.peli.app.data.Match
import com.peli.app.data.Repository
import com.peli.app.data.SearchLink
import com.peli.app.data.exportMatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Enlace de búsqueda configurable, títulos en inglés y las tres listas de enlaces (matches, mías, de mi pareja). */
class LinksFeature(
    private val env: VmEnv,
    private val repo: Repository,
    private val mine: StateFlow<List<Entry>>,
    private val matches: StateFlow<List<Match>>,
    private val available: StateFlow<Set<Int>>,
) {
    /** Plantilla del enlace de búsqueda del botón del detalle (se edita en Ajustes). */
    val template = MutableStateFlow(env.peli.searchLinkPrefs.template)

    fun setTemplate(value: String) {
        template.value = value
        env.peli.searchLinkPrefs.template = value
    }

    /** Títulos en inglés ya conocidos (se piden a TMDB una vez y se guardan en el teléfono). */
    val englishTitles = MutableStateFlow(env.peli.englishTitles.all())

    private suspend fun ensureEnglish(id: Int): String? {
        englishTitles.value[id]?.let { return it }
        val t = repo.englishTitle(id) ?: return null
        env.peli.englishTitles.put(id, t)
        englishTitles.update { it + (id to t) }
        return t
    }

    fun loadEnglish(id: Int) {
        if (id in englishTitles.value) return
        env.launch(Dispatchers.IO) { ensureEnglish(id) }
    }

    /**
     * Arma los enlaces de una de las tres listas, sin las ya disponibles, con el título en inglés y el año, numerados.
     * [onResult] recibe null si no hay enlace configurado o no hay nada pendiente.
     */
    fun export(kind: ExportList, onResult: (String?) -> Unit) {
        val own = mine.value.associateBy { it.movieId }
        val rows = exportMatches(kind, matches.value, available.value).map { it.entry }
        env.launch(Dispatchers.IO) {
            val items = coroutineScope {
                rows.map { e ->
                    async {
                        val en = ensureEnglish(e.movieId)
                        val fallback = (own[e.movieId]?.originalTitle ?: e.originalTitle)?.takeIf { it.isNotBlank() } ?: e.title
                        (en ?: fallback) to e.year
                    }
                }.awaitAll()
            }
            val text = SearchLink.listWithYears(template.value, items).takeIf { it.isNotEmpty() }?.let { SearchLink.numbered(it) }
            withContext(Dispatchers.Main) { onResult(text) }
        }
    }
}

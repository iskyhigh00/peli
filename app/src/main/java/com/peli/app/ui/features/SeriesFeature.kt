package com.peli.app.ui.features

import com.peli.app.data.Repository
import com.peli.app.data.SeriesEntry
import com.peli.app.data.attempt
import com.peli.app.data.toggleDisliked
import com.peli.app.data.toggleLiked
import com.peli.app.data.toggleSeen
import com.peli.app.data.toggleWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Apartado de series: búsqueda en TMDB, populares y mis marcas. Es independiente de las películas
 * (su propia tabla); por ahora no se comparte con la pareja.
 */
class SeriesFeature(private val env: VmEnv, private val repo: Repository) {
    val mine: StateFlow<List<SeriesEntry>> =
        repo.series.stateIn(env.scopeForFlows, SharingStarted.Eagerly, emptyList())

    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<SeriesEntry>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private var job: Job? = null

    /** Con texto busca por título; vacío muestra las series populares. */
    fun search(text: String) {
        query.value = text
        job?.cancel()
        job = env.launch(Dispatchers.IO) {
            loading.value = true
            error.value = null
            val q = text.trim()
            attempt { if (q.isEmpty()) repo.popularSeries() else repo.searchSeries(q) }
                .onSuccess { results.value = it }
                .onFailure { error.value = "No se pudieron cargar las series. Revisa tu conexión." }
            loading.value = false
        }
    }

    fun load() { if (results.value.isEmpty() && !loading.value) search(query.value) }

    private fun change(s: SeriesEntry, f: (SeriesEntry) -> SeriesEntry) {
        // Parte de lo que ya tengo guardado (si lo hay) para no perder marcas.
        val base = mine.value.firstOrNull { it.seriesId == s.seriesId } ?: s
        env.launch(Dispatchers.IO) { repo.saveSeries(f(base)) }
    }

    fun toggleWatch(s: SeriesEntry) = change(s) { it.toggleWatch() }
    fun toggleSeen(s: SeriesEntry) = change(s) { it.toggleSeen() }
    fun toggleLiked(s: SeriesEntry) = change(s) { it.toggleLiked() }
    fun toggleDisliked(s: SeriesEntry) = change(s) { it.toggleDisliked() }
}

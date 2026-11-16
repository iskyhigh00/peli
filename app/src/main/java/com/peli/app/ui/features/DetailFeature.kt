package com.peli.app.ui.features

import com.peli.app.data.InsightsState
import com.peli.app.data.RatingSource
import com.peli.app.data.Repository
import com.peli.app.data.attempt
import com.peli.app.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

sealed interface TrailerState {
    data object Loading : TrailerState
    data class Ready(val key: String) : TrailerState
    data object None : TrailerState
    data class Failed(val message: String) : TrailerState
}
/** Lo que se carga al abrir el detalle de una película: valoraciones y reseñas, director y reparto, tráiler y preferencias de fuentes. */
class DetailFeature(private val env: VmEnv, private val repo: Repository) {
    // ---- Valoraciones y reseñas ------------------------------------------------

    /** ¿Hay clave de OMDb? Sin ella no hay IMDb, Rotten Tomatoes ni Metacritic. */
    val omdbAvailable = com.peli.app.data.Omdb.configured

    /** Fuentes de valoraciones que el usuario quiere ver (se eligen en Ajustes). */
    val sources = MutableStateFlow(env.peli.sourcePrefs.load())

    fun toggleSource(source: RatingSource) {
        val next = sources.value.let { if (source in it) it - source else it + source }
        sources.value = next
        env.peli.sourcePrefs.save(next)
        AppLog.i("Ajustes", "fuentes de valoraciones: ${next.joinToString { it.name }.ifEmpty { "ninguna" }}")
    }

    /** Valoraciones por película, cargadas al abrir su detalle y guardadas mientras la app siga abierta. */
    val insights = MutableStateFlow<Map<Int, InsightsState>>(emptyMap())

    fun loadInsights(movieId: Int, force: Boolean = false) {
        val current = insights.value[movieId]
        if (!force && (current is InsightsState.Ready || current is InsightsState.Loading)) return
        insights.update { it + (movieId to InsightsState.Loading) }
        AppLog.d("Valoraciones", "cargando película $movieId")
        env.launch {
            val state = try {
                InsightsState.Ready(repo.insights(movieId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Valoraciones", "falló la carga de la película $movieId", e)
                InsightsState.Failed(e.message ?: e.javaClass.simpleName)
            }
            insights.update { it + (movieId to state) }
            // Al cargar las valoraciones se guardan también los premios (OMDb): se refrescan para el resumen del detalle.
            repo.cachedFacts(listOf(movieId))[movieId]?.let { f -> facts.update { it + (movieId to f) } }
        }
    }

    // ---- Detalle: director, reparto y tráiler --------------------------------------

    /** Datos extra (director, reparto) de las películas cuyo detalle se abrió. */
    val facts = MutableStateFlow<Map<Int, com.peli.app.data.MovieFacts>>(emptyMap())

    /** Descubrir muestra el tráiler en vez de la carátula (se apaga en Ajustes para ahorrar datos). */
    val discoverVideo = MutableStateFlow(env.peli.uiPrefs.discoverVideo)

    fun setDiscoverVideo(on: Boolean) {
        discoverVideo.value = on
        env.peli.uiPrefs.discoverVideo = on
    }

    private val badgeAsked = HashSet<Int>()

    /** Lee de lo guardado (sin red) los datos de una tarjeta que se ve, para su broche de premios. */
    fun cachedBadge(movieId: Int) {
        if (!badgeAsked.add(movieId) || movieId in facts.value) return
        env.launch(Dispatchers.IO) {
            repo.cachedFacts(listOf(movieId))[movieId]?.takeIf { it.hasOmdb }?.let { f -> facts.update { it + (movieId to f) } }
        }
    }

    fun loadFacts(movieId: Int) {
        env.launch(Dispatchers.IO) {
            repo.cachedFacts(listOf(movieId))[movieId]?.let { f -> facts.update { it + (movieId to f) } }
            attempt { repo.ensureFacts(movieId, false) }.getOrNull()?.let { f -> facts.update { it + (movieId to f) } }
        }
    }


    val trailers = MutableStateFlow<Map<Int, TrailerState>>(emptyMap())

    fun loadTrailer(movieId: Int, force: Boolean = false) {
        val current = trailers.value[movieId]
        if (!force && (current is TrailerState.Ready || current is TrailerState.None || current is TrailerState.Loading)) return
        trailers.update { it + (movieId to TrailerState.Loading) }
        env.launch {
            val state = try {
                repo.trailerKey(movieId)?.let { TrailerState.Ready(it) } ?: TrailerState.None
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Tráiler", "falló la carga del tráiler de $movieId", e)
                TrailerState.Failed(e.message ?: e.javaClass.simpleName)
            }
            trailers.update { it + (movieId to state) }
        }
    }
}

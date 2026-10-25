package com.peli.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.peli.app.BuildConfig
import com.peli.app.PeliApp
import com.peli.app.data.Entry
import com.peli.app.data.Sharing
import com.peli.app.data.computeMatches
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as PeliApp).repository

    val mine = repo.mine.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val partner = repo.partner.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val matches = combine(repo.mine, repo.partner) { m, p -> computeMatches(m, p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val results = MutableStateFlow<List<Entry>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)

    /** Cola de sugerencias para "Descubrir". */
    val queue = MutableStateFlow<List<Entry>>(emptyList())

    init {
        if (BuildConfig.TMDB_API_KEY.isBlank()) {
            error.value = "Falta TMDB_API_KEY en local.properties"
        } else {
            search("")
            refillQueue()
        }
    }

    private fun <T> guarded(block: suspend () -> T) = viewModelScope.launch {
        loading.value = true
        error.value = null
        try { block() } catch (e: Exception) { error.value = "Error de red: ${e.message}" }
        loading.value = false
    }

    /** Con texto busca; vacío muestra las populares. */
    fun search(query: String) {
        guarded {
            results.value = if (query.isBlank()) repo.popular(1) else repo.search(query.trim())
        }
    }

    fun toggleFavorite(e: Entry) = update(e) { it.copy(favorite = !it.favorite, disliked = false) }
    fun toggleSeen(e: Entry) = update(e) { it.copy(seen = !it.seen) }

    private fun update(e: Entry, change: (Entry) -> Entry) {
        viewModelScope.launch { repo.update(e, change) }
    }

    /** Aprender del usuario: me gusta / no me gusta / ya la vi, y pasa a la siguiente. */
    fun answer(e: Entry, like: Boolean?) {
        viewModelScope.launch {
            when (like) {
                true -> repo.update(e) { it.copy(favorite = true, disliked = false) }
                false -> repo.update(e) { it.copy(favorite = false, disliked = true) }
                null -> Unit
            }
            queue.update { it.filter { x -> x.movieId != e.movieId } }
            if (queue.value.size < 3) refillQueue()
        }
    }

    /** Sugerencias basadas en mis favoritas; si no hay, populares al azar. */
    fun refillQueue() {
        guarded {
            val known = mine.value.map { it.movieId }.toSet()
            val favs = mine.value.filter { it.favorite }.shuffled().take(3)
            val fresh = if (favs.isEmpty()) repo.popular((1..5).random())
            else favs.flatMap { repo.recommendations(it.movieId) }
            val merged = (queue.value + fresh)
                .filter { it.movieId !in known && it.posterPath != null && it.overview.isNotBlank() }
                .distinctBy { it.movieId }
                .shuffled()
            queue.value = merged
        }
    }

    fun exportCode() = Sharing.export(mine.value)

    fun importCode(text: String) {
        val list = Sharing.import(text)
        if (list == null) {
            message.value = "Código no válido"
        } else viewModelScope.launch {
            repo.importPartner(list)
            message.value = "Lista de tu pareja importada (${list.size} películas)"
        }
    }

    fun clearPartner() = viewModelScope.launch { repo.clearPartner() }
}

package com.peli.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.peli.app.APP_VERSION
import com.peli.app.BuildConfig
import com.peli.app.PeliApp
import com.peli.app.data.Entry
import com.peli.app.data.Sharing
import com.peli.app.data.computeMatches
import com.peli.app.data.toggleDisliked
import com.peli.app.data.toggleLiked
import com.peli.app.data.toggleSeen
import com.peli.app.data.toggleWatch
import com.peli.app.update.Changelog
import com.peli.app.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** Versión nueva disponible (null = al día o sin consultar). */
    val newVersion = MutableStateFlow<String?>(null)

    /** Progreso de descarga: -2 inactivo, -1 sin tamaño conocido, 0..100. */
    val progress = MutableStateFlow(-2)

    /** Novedades a mostrar tras actualizar (versión -> cambios). */
    val whatsNew = MutableStateFlow<List<Pair<String, List<String>>>>(emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) { Updater.cleanupDownloads(getApplication()) }
        whatsNew.value = Changelog.takeUnseen(getApplication())
        checkForUpdate(manual = false)
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

    fun toggleWant(e: Entry) = update(e) { it.toggleWatch() }
    fun toggleLiked(e: Entry) = update(e) { it.toggleLiked() }
    fun toggleSeen(e: Entry) = update(e) { it.toggleSeen() }
    fun toggleDislike(e: Entry) = update(e) { it.toggleDisliked() }

    private fun update(e: Entry, change: (Entry) -> Entry) {
        viewModelScope.launch { repo.update(e, change) }
    }

    /** Respuesta en "Descubrir": con ella la app aprende tus gustos y pasa a la siguiente. */
    enum class Answer { Want, SeenLiked, NotInterested, Skip }

    fun answer(e: Entry, answer: Answer) {
        viewModelScope.launch {
            when (answer) {
                Answer.Want -> repo.update(e) { it.copy(watchlist = true, seen = false, liked = false, disliked = false) }
                Answer.SeenLiked -> repo.update(e) { it.copy(liked = true, seen = true, watchlist = false, disliked = false) }
                Answer.NotInterested -> repo.update(e) { it.copy(disliked = true, liked = false, watchlist = false) }
                Answer.Skip -> Unit
            }
            queue.update { it.filter { x -> x.movieId != e.movieId } }
            if (queue.value.size < 4) refillQueue()
        }
    }

    /** Sugerencias ordenadas por el recomendador según lo que ya marcaste. */
    fun refillQueue() {
        guarded {
            val marked = mine.value
            val known = marked.map { it.movieId }.toSet()
            val ranked = repo.recommend(marked)
            queue.value = (queue.value + ranked)
                .filter { it.movieId !in known }
                .distinctBy { it.movieId }
        }
    }

    /** Al abrir la app es silencioso; manual (botón) informa también si está al día o falló. */
    fun checkForUpdate(manual: Boolean) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { Updater.checkLatest() }
            val v = res.version
            when {
                v == null -> if (manual) message.value = "No se pudo consultar: ${res.error}"
                Updater.isNewer(v, APP_VERSION) -> {
                    newVersion.value = v
                    if (manual) message.value = "Hay una versión nueva: v$v (tienes v$APP_VERSION)."
                }
                else -> {
                    newVersion.value = null
                    if (manual) message.value = "Estás al día (v$APP_VERSION)."
                }
            }
        }
    }

    fun installUpdate() {
        val v = newVersion.value ?: return
        viewModelScope.launch {
            progress.value = -1
            try {
                withContext(Dispatchers.IO) {
                    Updater.downloadAndInstall(getApplication(), v) { p -> progress.value = p }
                }
            } catch (e: Exception) {
                message.value = "No se pudo actualizar: ${e.message}"
            } finally {
                progress.value = -2
            }
        }
    }

    fun dismissWhatsNew() { whatsNew.value = emptyList() }

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

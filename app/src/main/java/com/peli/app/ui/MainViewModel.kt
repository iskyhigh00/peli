package com.peli.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.peli.app.APP_VERSION
import com.peli.app.BuildConfig
import com.peli.app.PeliApp
import com.peli.app.data.Entry
import com.peli.app.data.OWNER_ME
import com.peli.app.data.Remote
import com.peli.app.data.RoomCodes
import com.peli.app.data.Sharing
import com.peli.app.data.computeMatches
import com.peli.app.data.toggleDisliked
import com.peli.app.data.toggleLiked
import com.peli.app.data.toggleSeen
import com.peli.app.data.toggleWatch
import com.peli.app.update.Changelog
import com.peli.app.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val peli = app as PeliApp
    private val repo = peli.repository
    private val pairing = peli.pairing

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

    // ---- Sala con la pareja -------------------------------------------------

    /** ¿Hay servidor configurado? Si no, solo funciona el intercambio manual de códigos. */
    val cloudAvailable = Remote.configured

    /** Código de la sala actual (null = sin conectar). */
    val room = MutableStateFlow(pairing.room)

    /** Último error de sincronización (null = bien). */
    val syncError = MutableStateFlow<String?>(null)

    /** Matches nuevos pendientes de mostrar (los dos quieren verla). */
    val pendingMatches = MutableStateFlow<List<Entry>>(emptyList())

    private val syncLock = Mutex()

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
        // Tinder: cada vez que aparece una película que a los dos nos interesa, se avisa una sola vez.
        viewModelScope.launch {
            matches.collect { list ->
                val announced = pairing.announced
                val fresh = list.filter { it.both && it.entry.movieId !in announced }
                if (fresh.isNotEmpty()) {
                    pairing.announced = announced + fresh.map { it.entry.movieId }
                    pendingMatches.update { it + fresh.map { m -> m.entry } }
                }
            }
        }
    }

    private fun <T> guarded(block: suspend () -> T) = viewModelScope.launch {
        loading.value = true
        error.value = null
        try { block() } catch (e: Exception) { error.value = "Error de red: ${e.message}" }
        loading.value = false
    }

    /** true mientras se actualiza con el gesto de deslizar hacia abajo. */
    val refreshing = MutableStateFlow(false)
    private var lastQuery: String? = null

    /**
     * Con texto busca; vacío muestra las populares. Si ya está cargada esa misma búsqueda no se repite
     * (así la lista no cambia al volver de ver una película).
     */
    fun search(query: String) {
        if (query == lastQuery && results.value.isNotEmpty()) return
        lastQuery = query
        guarded {
            results.value = if (query.isBlank()) repo.popular(1) else repo.search(query.trim())
        }
    }

    /** Deslizar hacia abajo en la lista principal: vuelve a pedir el listado completo. */
    fun refreshSearch(query: String) {
        viewModelScope.launch {
            refreshing.value = true
            try {
                results.value = if (query.isBlank()) repo.popular(1) else repo.search(query.trim())
                lastQuery = query
                error.value = null
            } catch (e: Exception) {
                error.value = "Error de red: ${e.message}"
            } finally {
                refreshing.value = false
            }
        }
    }

    /** Deslizar hacia abajo en "Juntos": sincroniza con la sala ahora mismo. */
    fun refreshSync() {
        viewModelScope.launch {
            refreshing.value = true
            sync()
            refreshing.value = false
        }
    }

    fun toggleWant(e: Entry) = update(e) { it.toggleWatch() }
    fun toggleLiked(e: Entry) = update(e) { it.toggleLiked() }
    fun toggleSeen(e: Entry) = update(e) { it.toggleSeen() }
    fun toggleDislike(e: Entry) = update(e) { it.toggleDisliked() }

    private fun update(e: Entry, change: (Entry) -> Entry) {
        viewModelScope.launch { applyAndPush(e, change) }
    }

    /** Guarda localmente y sube el cambio a la sala (si falla, el próximo sync lo reintenta). */
    private suspend fun applyAndPush(e: Entry, change: (Entry) -> Entry) {
        val next = repo.update(e, change)
        val r = room.value ?: return
        runCatching { repo.push(r, pairing.userId, next) }
    }

    /** Respuesta en "Descubrir": con ella la app aprende tus gustos y pasa a la siguiente. */
    enum class Answer { Want, SeenLiked, NotInterested, Skip }

    fun answer(e: Entry, answer: Answer) {
        viewModelScope.launch {
            when (answer) {
                Answer.Want -> applyAndPush(e) { it.copy(watchlist = true, seen = false, liked = false, disliked = false) }
                Answer.SeenLiked -> applyAndPush(e) { it.copy(liked = true, seen = true, watchlist = false, disliked = false) }
                Answer.NotInterested -> applyAndPush(e) { it.copy(disliked = true, liked = false, watchlist = false) }
                Answer.Skip -> Unit
            }
            queue.update { it.filter { x -> x.movieId != e.movieId } }
            if (queue.value.size < 4) refillQueue()
        }
    }

    /** Películas que a tu pareja le interesan y yo aún no marqué: van primero en "Descubrir". */
    private fun partnerPicks(): List<Entry> {
        val known = mine.value.map { it.movieId }.toSet()
        return partner.value
            .filter { it.watchlist && !it.seen && !it.disliked && it.movieId !in known }
            .map { it.copy(owner = OWNER_ME, watchlist = false) }
    }

    /** Sugerencias: primero las de tu pareja, luego las ordenadas por el recomendador. */
    fun refillQueue() {
        guarded {
            val marked = mine.value
            val known = marked.map { it.movieId }.toSet()
            val ranked = repo.recommend(marked)
            queue.value = (partnerPicks() + queue.value + ranked)
                .filter { it.movieId !in known }
                .distinctBy { it.movieId }
        }
    }

    // ---- Sala: crear / unirse / sincronizar ----------------------------------

    fun createRoom() = enterRoom(RoomCodes.newCode())

    fun joinRoom(input: String) {
        val code = RoomCodes.normalize(input)
        if (code == null) message.value = "Código no válido: son 10 letras y números" else enterRoom(code)
    }

    private fun enterRoom(code: String) {
        pairing.room = code
        pairing.lastPush = 0
        room.value = code
        viewModelScope.launch { sync() }
    }

    fun leaveRoom() {
        pairing.room = null
        room.value = null
        syncError.value = null
        viewModelScope.launch { repo.clearPartner() }
    }

    fun roomShareText(): String {
        val code = room.value?.let { RoomCodes.format(it) } ?: ""
        return "Únete a mi sala en Peli para ver qué películas queremos ver juntos. Código: $code\n" +
            "(Peli → Juntos → Tengo un código)"
    }

    /** Un ciclo de sincronización; se llama cada pocos segundos mientras la app está abierta. */
    suspend fun sync() {
        val r = room.value ?: return
        if (!Remote.configured) return
        syncLock.withLock {
            val started = System.currentTimeMillis()
            try {
                repo.sync(r, pairing.userId, mine.value, pairing.lastPush)
                pairing.lastPush = started
                syncError.value = null
                // Lo que tu pareja guardó pasa al frente de tus sugerencias.
                val picks = partnerPicks()
                if (picks.isNotEmpty()) queue.update { (picks + it).distinctBy { x -> x.movieId } }
            } catch (e: Exception) {
                syncError.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun dismissMatch() = pendingMatches.update { it.drop(1) }

    // ---- Actualizaciones -----------------------------------------------------

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

    // ---- Intercambio manual de códigos (sin servidor) ------------------------

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

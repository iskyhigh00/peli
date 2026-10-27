package com.peli.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.peli.app.APP_VERSION
import com.peli.app.BuildConfig
import com.peli.app.CrashReporter
import com.peli.app.PeliApp
import com.peli.app.data.Entry
import com.peli.app.data.Remote
import com.peli.app.data.RoomCodes
import com.peli.app.data.YearRange
import com.peli.app.data.Sharing
import com.peli.app.data.computeMatches
import com.peli.app.data.toggleDisliked
import com.peli.app.data.toggleLiked
import com.peli.app.data.toggleSeen
import com.peli.app.data.toggleWatch
import com.peli.app.update.Changelog
import com.peli.app.update.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Ofrece deshacer la última respuesta de "Descubrir". */
data class UndoOffer(val message: String, val card: Entry, val previous: Entry?)

/**
 * Estado de la app. Lo que el usuario elige (búsqueda, filtros, orden) vive en [SavedStateHandle]:
 * sobrevive a cambios de pestaña, navegación, rotación y recuperación tras cerrar el proceso.
 */
class MainViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val peli = app as PeliApp
    private val repo = peli.repository
    private val pairing = peli.pairing

    val mine = repo.mine.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val partner = repo.partner.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val matches = combine(repo.mine, repo.partner) { m, p -> computeMatches(m, p) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Selecciones del usuario (persisten) ---------------------------------

    val searchQuery: StateFlow<String> = saved.getStateFlow(KEY_QUERY, "")
    val searchFilter: StateFlow<SearchFilter> = saved.getStateFlow(KEY_SEARCH_FILTER, SearchFilter.All.name)
        .map { SearchFilter.valueOf(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchFilter.All)
    val mineFilter: StateFlow<MineFilter> = saved.getStateFlow(KEY_MINE_FILTER, MineFilter.All.name)
        .map { MineFilter.valueOf(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, MineFilter.All)
    val searchYears: StateFlow<YearRange?> = saved.getStateFlow(KEY_SEARCH_YEARS, "")
        .map { YearRange.decode(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mineYears: StateFlow<YearRange?> = saved.getStateFlow(KEY_MINE_YEARS, "")
        .map { YearRange.decode(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mineSort: StateFlow<MineSort> = saved.getStateFlow(KEY_MINE_SORT, MineSort.Recent.name)
        .map { MineSort.valueOf(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, MineSort.Recent)

    fun setSearchQuery(q: String) { saved[KEY_QUERY] = q }
    fun setSearchFilter(f: SearchFilter) { saved[KEY_SEARCH_FILTER] = f.name }
    fun setMineFilter(f: MineFilter) { saved[KEY_MINE_FILTER] = f.name }
    fun setMineSort(s: MineSort) { saved[KEY_MINE_SORT] = s.name }
    fun setSearchYears(r: YearRange?) { saved[KEY_SEARCH_YEARS] = r?.encode() ?: "" }
    fun setMineYears(r: YearRange?) { saved[KEY_MINE_YEARS] = r?.encode() ?: "" }

    // ---- Buscar --------------------------------------------------------------

    val results = MutableStateFlow<List<Entry>>(emptyList())
    val searchLoading = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)

    /** true mientras se actualiza con el gesto de deslizar hacia abajo. */
    val refreshing = MutableStateFlow(false)

    private val searchNow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var firstSearch = true
    private var pullPending = false

    /** Último lote del feed personalizado: se reutiliza al volver a la lista vacía y cambia al deslizar. */
    private var feed: List<Entry> = emptyList()
    private var feedYears: YearRange? = null

    // ---- Descubrir -----------------------------------------------------------

    /** Cola de sugerencias (sin filtrar por marcas). */
    val queue = MutableStateFlow<List<Entry>>(emptyList())

    /**
     * Lo que realmente se muestra en "Descubrir": la cola sin lo que ya marcaste, en cualquier pantalla
     * y en el momento en que lo marcas (la cola se arma por adelantado y puede traer películas ya marcadas).
     */
    val discoverQueue: StateFlow<List<Entry>> = combine(queue, repo.mine) { q, marked -> q.withoutMarked(marked) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Las que omitiste en esta sesión no vuelven a aparecer. */
    private val skipped = HashSet<Int>()
    val queueLoading = MutableStateFlow(false)
    val queueError = MutableStateFlow<String?>(null)
    val undo = MutableStateFlow<UndoOffer?>(null)
    private var refillJob: Job? = null

    // ---- Avisos y actualizaciones --------------------------------------------

    val message = MutableStateFlow<String?>(null)

    /** Versión nueva disponible (null = al día o sin consultar). */
    val newVersion = MutableStateFlow<String?>(null)

    /** Progreso de descarga: -2 inactivo, -1 sin tamaño conocido, 0..100. */
    val progress = MutableStateFlow(-2)

    /** Novedades a mostrar tras actualizar (versión -> cambios). */
    val whatsNew = MutableStateFlow<List<Pair<String, List<String>>>>(emptyList())

    // ---- Sala con la pareja --------------------------------------------------

    /** ¿Hay servidor configurado? Si no, solo funciona el intercambio manual de códigos. */
    val cloudAvailable = Remote.configured

    /** Código de la sala actual (null = sin conectar). */
    val room = MutableStateFlow(pairing.room)

    /** Último error de sincronización (null = bien). */
    val syncError = MutableStateFlow<String?>(null)

    /** Matches nuevos pendientes de mostrar (los dos quieren verla). */
    val pendingMatches = MutableStateFlow<List<Entry>>(emptyList())

    private val syncLock = Mutex()

    /** Informe del último cierre inesperado (null = no hubo). Se muestra una vez al abrir la app. */
    val crashReport = MutableStateFlow<String?>(null)

    /**
     * Cualquier error no controlado dentro de una corrutina se muestra como aviso y se registra,
     * en vez de cerrar la app.
     */
    private val errorHandler = CoroutineExceptionHandler { _, e ->
        if (e !is CancellationException) {
            CrashReporter.record(getApplication(), "corrutina", e)
            message.value = "Algo falló: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun launch(
        context: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch(context + errorHandler, block = block)

    init {
        launch(Dispatchers.IO) { Updater.cleanupDownloads(getApplication()) }
        whatsNew.value = Changelog.takeUnseen(getApplication())
        launch(Dispatchers.IO) { crashReport.value = CrashReporter.pending(getApplication()) }
        checkForUpdate(manual = false)
        refillQueue()

        // Búsqueda: espera a que dejes de escribir; si llega otra consulta, la anterior se cancela
        // (así nunca se muestra una respuesta vieja). "Buscar" del teclado o deslizar la lanza al instante.
        launch {
            // El rango se aplica al instante (sin la espera de escritura); su valor inicial no cuenta como cambio.
            merge(searchQuery.map { false }, searchYears.drop(1).map { true }, searchNow.map { true }).collectLatest { immediate ->
                if (!immediate && !firstSearch) delay(SEARCH_DEBOUNCE_MS)
                firstSearch = false
                runSearch(searchQuery.value)
            }
        }

        // Tinder: cada vez que aparece una película que a los dos nos interesa, se avisa una sola vez.
        launch {
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

    // ---- Buscar: lógica ------------------------------------------------------

    private suspend fun runSearch(q: String) {
        val pull = pullPending
        if (BuildConfig.TMDB_API_KEY.isBlank()) {
            searchError.value = "Falta TMDB_API_KEY en local.properties"
            endPull()
            return
        }
        searchLoading.value = true
        searchError.value = null
        try {
            val years = searchYears.value
            results.value = if (q.isBlank()) loadFeed(refresh = pull, years = years) else repo.search(q.trim(), years)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (pull && results.value.isNotEmpty()) {
                message.value = "No se pudo actualizar: ${e.message}"
            } else {
                results.value = emptyList()
                searchError.value = "Error de red: ${e.message}"
            }
        } finally {
            searchLoading.value = false
            endPull()
        }
    }

    /**
     * Sin búsqueda, la lista principal es como "Sorpréndeme": sugerencias según tus gustos.
     * Se mantiene estable mientras navegas y, al deslizar, trae un lote distinto sin repetir lo anterior.
     */
    private suspend fun loadFeed(refresh: Boolean, years: YearRange?): List<Entry> {
        // El lote guardado solo sirve si se pidió con el mismo rango de años.
        if (!refresh && feed.isNotEmpty() && feedYears == years) return feed
        val avoid = if (refresh) feed.map { it.movieId }.toSet() else emptySet()
        // Se lee de la base (no del StateFlow, que al arrancar aún puede estar vacío) para personalizar de verdad.
        val next = repo.recommend(repo.mine.first(), avoid = avoid, noise = FEED_NOISE, years = years).take(FEED_SIZE)
        feed = next
        feedYears = years
        return next
    }

    private fun endPull() {
        if (pullPending) {
            pullPending = false
            refreshing.value = false
        }
    }

    /** "Buscar" del teclado: consulta ya, sin esperar. */
    fun submitSearch() { searchNow.tryEmit(Unit) }

    /** Deslizar hacia abajo en la lista principal: vuelve a pedir el listado completo. */
    fun refreshSearch() {
        pullPending = true
        refreshing.value = true
        searchNow.tryEmit(Unit)
    }

    // ---- Marcar películas ----------------------------------------------------

    fun toggleWant(e: Entry) = update(e) { it.toggleWatch() }
    fun toggleLiked(e: Entry) = update(e) { it.toggleLiked() }
    fun toggleSeen(e: Entry) = update(e) { it.toggleSeen() }
    fun toggleDislike(e: Entry) = update(e) { it.toggleDisliked() }

    private fun update(e: Entry, change: (Entry) -> Entry) {
        launch { applyAndPush(e, change) }
    }

    /** Guarda localmente y sube el cambio a la sala (si falla, el próximo sync lo reintenta). */
    private suspend fun applyAndPush(e: Entry, change: (Entry) -> Entry) {
        push(repo.update(e, change))
    }

    private suspend fun push(next: Entry) {
        val r = room.value ?: return
        try {
            repo.push(r, pairing.userId, next)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // el siguiente sync vuelve a subir lo marcado
        }
    }

    /** La película con ese id desde cualquier lista conocida (para reabrir el detalle tras rotar). */
    fun lookup(id: Int): Entry? =
        mine.value.firstOrNull { it.movieId == id }
            ?: results.value.firstOrNull { it.movieId == id }
            ?: queue.value.firstOrNull { it.movieId == id }
            ?: partner.value.firstOrNull { it.movieId == id }

    // ---- Descubrir -----------------------------------------------------------

    /** Respuesta en "Descubrir": con ella la app aprende tus gustos y pasa a la siguiente. */
    enum class Answer { Want, SeenLiked, NotInterested, Skip }

    fun answer(e: Entry, answer: Answer) {
        // Toques rápidos: solo cuenta la tarjeta que realmente está arriba.
        if (discoverQueue.value.firstOrNull()?.movieId != e.movieId) return
        if (answer == Answer.Skip) skipped += e.movieId
        queue.update { it.filter { x -> x.movieId != e.movieId } }
        launch {
            if (answer != Answer.Skip) {
                val previous = repo.snapshot(e.movieId)
                when (answer) {
                    Answer.Want -> applyAndPush(e) { it.copy(watchlist = true, seen = false, liked = false, disliked = false) }
                    Answer.SeenLiked -> applyAndPush(e) { it.copy(liked = true, seen = true, watchlist = false, disliked = false) }
                    Answer.NotInterested -> applyAndPush(e) { it.copy(disliked = true, liked = false, watchlist = false) }
                    Answer.Skip -> Unit
                }
                undo.value = UndoOffer(
                    message = when (answer) {
                        Answer.Want -> "Guardada en «Quiero verla»"
                        Answer.SeenLiked -> "Guardada como «La vi y me gustó»"
                        else -> "Descartada"
                    },
                    card = e,
                    previous = previous,
                )
            }
            if (discoverQueue.value.size < 4) refillQueue()
        }
    }

    /** Deshace la última respuesta: restaura la marca anterior y devuelve la tarjeta al frente. */
    fun undoAnswer() {
        val offer = undo.value ?: return
        undo.value = null
        launch {
            push(repo.restore(offer.card, offer.previous))
            skipped -= offer.card.movieId
            queue.update { listOf(offer.card) + it.filter { x -> x.movieId != offer.card.movieId } }
        }
    }

    fun clearUndo() { undo.value = null }

    /** Películas que a tu pareja le interesan y yo aún no marqué: van primero en "Descubrir". */
    private fun partnerPicks(): List<Entry> {
        val known = mine.value.map { it.movieId }.toSet()
        return partner.value
            .filter { it.watchlist && !it.seen && !it.disliked && it.movieId !in known }
            .map { it.copy(owner = com.peli.app.data.OWNER_ME, watchlist = false) }
    }

    /** Sugerencias: primero las de tu pareja, luego las ordenadas por el recomendador. Una carga a la vez. */
    fun refillQueue() {
        if (BuildConfig.TMDB_API_KEY.isBlank()) {
            queueError.value = "Falta TMDB_API_KEY en local.properties"
            return
        }
        if (refillJob?.isActive == true) return
        refillJob = launch {
            queueLoading.value = true
            queueError.value = null
            try {
                val marked = repo.mine.first()
                val ranked = repo.recommend(marked)
                val known = repo.mine.first().map { it.movieId }.toSet()
                queue.value = (partnerPicks() + queue.value + ranked)
                    .filter { it.movieId !in known && it.movieId !in skipped }
                    .distinctBy { it.movieId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                queueError.value = "Error de red: ${e.message}"
            } finally {
                queueLoading.value = false
            }
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
        launch { sync() }
    }

    fun leaveRoom() {
        pairing.room = null
        room.value = null
        syncError.value = null
        launch { repo.clearPartner() }
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
                if (picks.isNotEmpty()) queue.update { (picks.filter { p -> p.movieId !in skipped } + it).distinctBy { x -> x.movieId } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                syncError.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    /** Deslizar hacia abajo en "Juntos": sincroniza con la sala ahora mismo. */
    fun refreshSync() {
        launch {
            refreshing.value = true
            try { sync() } finally { refreshing.value = false }
        }
    }

    fun dismissMatch() = pendingMatches.update { it.drop(1) }

    fun dismissCrashReport() {
        crashReport.value = null
        launch(Dispatchers.IO) { CrashReporter.clear(getApplication()) }
    }

    // ---- Actualizaciones -----------------------------------------------------

    /** Al abrir la app es silencioso; manual (botón) informa también si está al día o falló. */
    fun checkForUpdate(manual: Boolean) {
        launch {
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
        if (progress.value != -2) return   // ya se está descargando
        val v = newVersion.value ?: return
        launch {
            progress.value = -1
            try {
                withContext(Dispatchers.IO) {
                    Updater.downloadAndInstall(getApplication(), v) { p -> progress.value = p }
                }
            } catch (e: CancellationException) {
                throw e
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
        } else launch {
            repo.importPartner(list)
            message.value = "Lista de tu pareja importada (${list.size} películas)"
        }
    }

    fun clearPartner() { launch { repo.clearPartner() } }

    private companion object {
        const val KEY_QUERY = "search_query"
        const val KEY_SEARCH_FILTER = "search_filter"
        const val KEY_MINE_FILTER = "mine_filter"
        const val KEY_MINE_SORT = "mine_sort"
        const val KEY_SEARCH_YEARS = "search_years"
        const val KEY_MINE_YEARS = "mine_years"
        const val SEARCH_DEBOUNCE_MS = 350L
        const val FEED_SIZE = 40
        const val FEED_NOISE = 0.3
    }
}

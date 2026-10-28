package com.peli.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.peli.app.APP_VERSION
import com.peli.app.BuildConfig
import com.peli.app.CrashReporter
import com.peli.app.PeliApp
import com.peli.app.auth.GoogleToken
import com.peli.app.data.ContextGuard
import com.peli.app.data.Friend
import com.peli.app.data.CandMeta
import com.peli.app.data.Delivery
import com.peli.app.data.ExposureRules
import com.peli.app.data.MovieFilters
import com.peli.app.data.ReleaseInfo
import com.peli.app.data.Recommender
import com.peli.app.data.Repository
import com.peli.app.data.Scopes
import com.peli.app.data.FriendRequestResult
import com.peli.app.data.Profile
import com.peli.app.data.Social
import com.peli.app.data.SupabaseAuth
import com.peli.app.data.Usernames
import com.peli.app.data.accountMessage
import com.peli.app.data.accepts
import com.peli.app.data.attempt
import com.peli.app.data.Entry
import com.peli.app.data.InsightsState
import com.peli.app.data.RatingSource
import com.peli.app.data.Remote
import com.peli.app.data.RoomCodes
import com.peli.app.data.YearRange
import com.peli.app.data.Sharing
import com.peli.app.data.computeMatches
import com.peli.app.data.toggleDisliked
import com.peli.app.data.toggleLiked
import com.peli.app.data.toggleSeen
import com.peli.app.data.toggleWatch
import com.peli.app.data.stateLabel
import com.peli.app.data.withRating
import com.peli.app.log.AppLog
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

/** Hay películas guardadas sin cuenta y alguien acaba de iniciar sesión: ¿pasan a esa cuenta? */
data class PendingClaim(val scope: String, val count: Int)

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
    private val sourcePrefs = peli.sourcePrefs
    private val account = peli.account
    private val scopes = peli.scopes

    /** Cambia cuando cambia el contexto (cuenta, amigo, sala): lo que llegue tarde de otro contexto se descarta. */
    private val ctx = ContextGuard()
    private val drainLock = Mutex()

    /** De quién es la lista que hay ahora en la tabla de "la otra persona" (para no anunciar matches con datos de otro). */
    private val partnerSource = MutableStateFlow<String?>(null)

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
    private val filterStore = peli.filterStore

    /** Filtros por valoraciones y premios; se guardan en el teléfono hasta que se quiten. */
    val movieFilters = MutableStateFlow(filterStore.movie)
    val searchYears: StateFlow<YearRange?> = saved.getStateFlow(KEY_SEARCH_YEARS, filterStore.searchYears)
        .map { YearRange.decode(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mineYears: StateFlow<YearRange?> = saved.getStateFlow(KEY_MINE_YEARS, filterStore.mineYears)
        .map { YearRange.decode(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mineSort: StateFlow<MineSort> = saved.getStateFlow(KEY_MINE_SORT, MineSort.Recent.name)
        .map { MineSort.valueOf(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, MineSort.Recent)

    fun setSearchQuery(q: String) { saved[KEY_QUERY] = q }
    fun setSearchFilter(f: SearchFilter) { AppLog.d("Filtro", "buscar: ${f.name}"); saved[KEY_SEARCH_FILTER] = f.name }
    fun setMineFilter(f: MineFilter) { AppLog.d("Filtro", "mi lista: ${f.name}"); saved[KEY_MINE_FILTER] = f.name }
    fun setMineSort(s: MineSort) { AppLog.d("Filtro", "orden de mi lista: ${s.name}"); saved[KEY_MINE_SORT] = s.name }
    fun setSearchYears(r: YearRange?) {
        AppLog.d("Filtro", "años (buscar): ${r?.encode() ?: "todos"}")
        filterStore.searchYears = r?.encode() ?: ""
        saved[KEY_SEARCH_YEARS] = r?.encode() ?: ""
        resetQueue()
    }
    fun setMineYears(r: YearRange?) {
        AppLog.d("Filtro", "años (mi lista): ${r?.encode() ?: "todos"}")
        filterStore.mineYears = r?.encode() ?: ""
        saved[KEY_MINE_YEARS] = r?.encode() ?: ""
    }

    /** Aplica filtros de valoraciones y premios: se guardan y la lista principal se arma de nuevo con ellos. */
    fun setMovieFilters(f: MovieFilters) {
        AppLog.i("Filtro", "valoraciones y premios: ${f.summary().ifEmpty { "ninguno" }}")
        filterStore.movie = f
        filterRejected.clear()
        movieFilters.value = f
        searchNow.tryEmit(Unit)
        resetQueue()
    }

    /** Cambiaron los filtros: la cola de Descubrir se arma de nuevo con ellos (los filtros valen en las dos pantallas). */
    private fun resetQueue() {
        refillJob?.cancel()
        queue.value = emptyList()
        refillQueue()
    }

    /** "Quitar filtros": años y valoraciones/premios de la lista principal. */
    fun clearFilters() {
        AppLog.i("Filtro", "se quitan todos los filtros de la lista principal")
        setSearchYears(null)
        setMovieFilters(MovieFilters.NONE)
    }

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
    private var feedFilters = MovieFilters.NONE

    /** Películas que no pasaron los filtros actuales: no se vuelven a evaluar ni cuentan como material disponible. */
    private val filterRejected = HashSet<Int>()

    /** Se están pidiendo más sugerencias al llegar al final de la lista. */
    val loadingMore = MutableStateFlow(false)

    /** Ya no hay más sugerencias para estos filtros (por ahora). */
    val feedEnd = MutableStateFlow(false)

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

    /** Entrega = elegir + reservar. La lista principal y Descubrir la hacen de una en una. */
    private val deliveryMutex = Mutex()

    /** La tarjeta de Descubrir que se ve ahora, las últimas vistas (para la diversidad) y las devueltas por «deshacer». */
    private var shownCardId: Int? = null
    private val shownCards = ArrayDeque<Entry>()
    private val undoReturned = HashSet<Int>()

    /** Votos y señal de TMDB de las tarjetas de la cola, para volver a ordenarla al aprender. */
    private val queueMeta = HashMap<Int, CandMeta>()
    val queueLoading = MutableStateFlow(false)
    val queueError = MutableStateFlow<String?>(null)
    val undo = MutableStateFlow<UndoOffer?>(null)
    private var refillJob: Job? = null

    // ---- Valoraciones y reseñas ------------------------------------------------

    /** ¿Hay clave de OMDb? Sin ella no hay IMDb, Rotten Tomatoes ni Metacritic. */
    val omdbAvailable = com.peli.app.data.Omdb.configured

    /** Fuentes de valoraciones que el usuario quiere ver (se eligen en Ajustes). */
    val sources = MutableStateFlow(sourcePrefs.load())

    fun toggleSource(source: RatingSource) {
        val next = sources.value.let { if (source in it) it - source else it + source }
        sources.value = next
        sourcePrefs.save(next)
        AppLog.i("Ajustes", "fuentes de valoraciones: ${next.joinToString { it.name }.ifEmpty { "ninguna" }}")
    }

    /** Valoraciones por película, cargadas al abrir su detalle y guardadas mientras la app siga abierta. */
    val insights = MutableStateFlow<Map<Int, InsightsState>>(emptyMap())

    fun loadInsights(movieId: Int, force: Boolean = false) {
        val current = insights.value[movieId]
        if (!force && (current is InsightsState.Ready || current is InsightsState.Loading)) return
        insights.update { it + (movieId to InsightsState.Loading) }
        AppLog.d("Valoraciones", "cargando película $movieId")
        launch {
            val state = try {
                InsightsState.Ready(repo.insights(movieId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Valoraciones", "falló la carga de la película $movieId", e)
                InsightsState.Failed(e.message ?: e.javaClass.simpleName)
            }
            insights.update { it + (movieId to state) }
        }
    }

    /** Mi nota (1..10, 0 la quita). Calificar implica que ya la vi; se sincroniza con la sala. */
    fun setMyRating(e: Entry, score: Int) = update(e) { it.withRating(score) }

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
            CrashReporter.record(getApplication(), "corrutina", e)  // también lo deja en el registro
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
        scopes.ensureInitialized(account.session?.userId)
        partnerSource.value = matchContext()
        if (account.session != null) launch {
            // Si la sesión ya estaba pero la colección activa no es la de esa cuenta (p. ej. se cerró la app
            // sin responder si las películas del teléfono pasaban a la cuenta), se vuelve a preguntar.
            val uid = account.session?.userId
            if (uid != null && scopes.current(uid) != Scopes.account(uid)) applyLoginPlan(scopes.current(uid), uid)
            if (account.profile == null) loadProfile() else sync()
        }
        checkForUpdate(manual = false)
        refillQueue()

        // Datos extra (palabras clave, director, reparto) de lo que ya marqué: se completan de a poco en segundo plano.
        launch(Dispatchers.IO) {
            delay(ENRICH_DELAY_MS)
            attempt { repo.enrichMarked(repo.mine.first()) }
                .onFailure { AppLog.w("Datos", "no se pudieron completar los datos de mis películas", it) }
        }

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
                // Solo se anuncia si la lista de la otra persona es realmente la del contexto actual.
                val c = matchContext() ?: return@collect
                if (partnerSource.value != c) return@collect
                val announced = pairing.announced(c)
                val fresh = list.filter { it.both && it.entry.movieId !in announced }
                if (fresh.isNotEmpty()) {
                    AppLog.i("Match", "nuevos: ${fresh.joinToString { it.entry.title }}")
                    pairing.addAnnounced(c, fresh.map { it.entry.movieId })
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
        val t0 = System.currentTimeMillis()
        try {
            val years = searchYears.value
            val filters = movieFilters.value
            if (q.isBlank()) feedEnd.value = false
            results.value = if (q.isBlank()) loadFeed(refresh = pull, years = years, filters = filters) else searchFiltered(q.trim(), years, filters)
            AppLog.i(
                "Buscar",
                "${if (q.isBlank()) "sugerencias${if (pull) " (actualizadas)" else ""}" else "«${q.trim()}»"} " +
                    "años=${years?.encode() ?: "todos"} filtros=${filters.summary().ifEmpty { "ninguno" }} → ${results.value.size} resultados (${System.currentTimeMillis() - t0} ms)",
            )
        } catch (e: CancellationException) {
            AppLog.d("Buscar", "búsqueda «$q» cancelada (llegó otra)")
            throw e
        } catch (e: Exception) {
            AppLog.w("Buscar", "falló la búsqueda «$q»", e)
            if (q.isBlank() && results.value.isNotEmpty()) {
                // Lista principal: ante un fallo de red se conserva lo que ya se veía (no se avanza de ronda).
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
    private suspend fun loadFeed(refresh: Boolean, years: YearRange?, filters: MovieFilters, append: Boolean = false): List<Entry> {
        // El lote guardado solo sirve si se pidió con el mismo rango de años y los mismos filtros.
        if (!refresh && feed.isNotEmpty() && feedYears == years && feedFilters == filters) return feed
        // Se lee de la base (no del StateFlow, que al arrancar aún puede estar vacío) para personalizar de verdad.
        val marked = repo.mine.first()
        // Elegir, reservar y entregar es una sola operación: la lista principal y Descubrir no se pisan.
        return deliveryMutex.withLock {
            // Una petición de "más" que quedó vieja (cambió el rango o los filtros mientras esperaba) no hace nada.
            if (append && (feedYears != years || feedFilters != filters)) return@withLock feed
            val store = peli.exposure
            val round = store.round() + 1
            val now = System.currentTimeMillis()
            val before = store.snapshot()
            // Con un filtro explícito de años futuros los próximos estrenos pueden ser toda la lista.
            val upcomingCap = if (years != null && years.to >= ReleaseInfo.currentYear()) FEED_SIZE else null
            val next = ArrayList<Entry>()
            val fb = FilterBudget(FILTER_FETCH_BUDGET)
            var tries = 0
            var candidates = 0
            var fresh = 0
            var repeatable = 0
            // Sin filtros basta una vuelta; con filtros se sigue buscando material hasta llenar la lista (o gastar el presupuesto).
            val maxTries = if (filters.isActive) FILTER_ROUNDS else 1
            while (next.size < FEED_SIZE && tries < maxTries) {
                tries++
                val taken = next.map { it.movieId }.toSet()
                val pool = repo.candidatePool(
                    marked, years, needed = FEED_SIZE,
                    isUsable = { it !in taken && it !in filterRejected && ExposureRules.eligible(before[it], round, now) },
                )
                candidates = pool.candidates.size
                // La red tarda: se vuelve a leer la memoria por si Descubrir entregó algo mientras tanto.
                val tiers = Delivery.tiers(
                    pool.candidates.filter { it.movieId !in taken && it.movieId !in filterRejected },
                    store.snapshot(), round, now,
                )
                if (tries == 1) {
                    fresh = tiers[0].size
                    repeatable = tiers[1].size + tiers[2].size
                }
                next += pickTiers(tiers, pool, FEED_SIZE - next.size, filters, fb, already = next.toList()) { usable, need, chosen ->
                    Recommender.select(usable, pool.profile, pool.meta, need, recent = chosen, maxUpcoming = upcomingCap)
                }
                if (fb.fetch <= 0 || (filters.needsOmdb && repo.omdbBlocked)) break
            }
            if (next.isNotEmpty()) {
                val ids = next.map { it.movieId }
                store.deliver(ids, round, now)
                repo.consume(ids)
            }
            AppLog.i(
                "Feed",
                "ronda $round: ${next.size} películas (candidatas $candidates, nuevas $fresh, repetibles $repeatable" +
                    (if (filters.isActive) ", filtros: ${filters.summary()}, sin datos ${fb.unknown}" else "") + ")",
            )
            if (filters.needsOmdb && (fb.limited || repo.omdbBlocked) && next.size < FEED_SIZE) {
                message.value = "OMDb llegó a su límite de consultas por hoy: algunas películas no se pudieron revisar con estos filtros."
            } else if (next.size < FEED_SIZE / 2) {
                message.value = if (filters.isActive) "Pocas películas cumplen estos filtros. Prueba con otros o quítalos."
                else "Hay pocas películas nuevas por ahora. Prueba más tarde o cambia los filtros."
            }
            feed = if (append) (feed + next).distinctBy { it.movieId }.takeLast(MAX_FEED) else next
            feedYears = years
            feedFilters = filters
            feedEnd.value = next.isEmpty()
            feed
        }
    }

    /** Presupuesto de consultas a OMDb de una carga y lo que se vio en ella. */
    private class FilterBudget(var fetch: Int) {
        var unknown = 0
        var limited = false
    }

    /**
     * Toma hasta [k] películas nivel por nivel (el siguiente solo si el anterior no alcanza). Con filtros activos,
     * cada nivel se ordena por puntaje y se revisa hasta juntar las suficientes; las que no pasan se anotan para no
     * volver a evaluarlas. [pick] elige entre las que pasaron (recibe cuántas faltan y lo ya elegido).
     */
    private suspend fun pickTiers(
        tiers: List<List<Entry>>,
        pool: Repository.Pool,
        k: Int,
        filters: MovieFilters,
        fb: FilterBudget,
        already: List<Entry> = emptyList(),
        pick: (usable: List<Entry>, need: Int, chosen: List<Entry>) -> List<Entry>,
    ): List<Entry> {
        val out = ArrayList<Entry>()
        for (tier in tiers) {
            if (out.size >= k) break
            if (tier.isEmpty()) continue
            val usable = if (filters.isActive) {
                val ranked = Recommender.rank(tier, pool.profile, pool.meta, noise = FILTER_RANK_NOISE)
                val pass = repo.applyFilters(ranked, filters, want = (k - out.size) * 2, maxFetch = fb.fetch)
                filterRejected += pass.rejected
                fb.fetch -= pass.fetched
                fb.unknown += pass.unknown
                fb.limited = fb.limited || pass.limited
                pass.kept
            } else tier
            if (usable.isEmpty()) continue
            out += pick(usable, k - out.size, already + out)
        }
        return out
    }

    /** Búsqueda por título con los filtros de valoraciones y premios (lo que no tiene datos para decidir queda fuera). */
    private suspend fun searchFiltered(q: String, years: YearRange?, filters: MovieFilters): List<Entry> {
        val found = repo.search(q, years)
        if (!filters.isActive) return found
        val pass = repo.applyFilters(found, filters, want = found.size, maxFetch = FILTER_FETCH_BUDGET)
        if (pass.unknown > 0) message.value = "${pass.unknown} películas no tienen datos suficientes para estos filtros y se ocultaron."
        return pass.kept
    }

    /** Al llegar al final de la lista principal: trae otro lote y lo agrega abajo (sin tener que volver arriba). */
    fun loadMore() {
        if (loadingMore.value || searchLoading.value || refreshing.value || feedEnd.value) return
        if (searchQuery.value.isNotBlank() || feed.isEmpty()) return
        loadingMore.value = true
        launch {
            try {
                val years = searchYears.value
                val filters = movieFilters.value
                val all = loadFeed(refresh = true, years = years, filters = filters, append = true)
                if (searchQuery.value.isBlank() && years == searchYears.value && filters == movieFilters.value) results.value = all
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Feed", "no se pudieron cargar más sugerencias", e)
                message.value = "No se pudieron cargar más: ${e.message}"
            } finally {
                loadingMore.value = false
            }
        }
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
    fun toggleLiked(e: Entry) {
        launch {
            val next = applyAndPush(e) { it.toggleLiked() }
            askRating(next)
        }
    }
    fun toggleSeen(e: Entry) = update(e) { it.toggleSeen() }
    fun toggleDislike(e: Entry) = update(e) { it.toggleDisliked() }

    private fun update(e: Entry, change: (Entry) -> Entry) {
        launch { applyAndPush(e, change) }
    }

    private fun describe(e: Entry) = "«${e.title}» (${e.movieId}) [${e.stateLabel()}${if (e.myRating > 0) ", nota ${e.myRating}" else ""}]"

    /** Guarda localmente y sube el cambio a la sala (si falla, el próximo sync lo reintenta). */
    private suspend fun applyAndPush(e: Entry, change: (Entry) -> Entry): Entry {
        AppLog.i("Marca", "antes: ${describe(e)}")
        val next = repo.update(e, change)
        AppLog.i("Marca", "después: ${describe(next)}")
        requestDrain()   // la marca ya está guardada y encolada; la red no bloquea nada
        return next
    }

    /** Película recién marcada «La vi y me gustó» sin nota: se le pide una de 1 a 10 para afinar las recomendaciones. */
    val ratePrompt = MutableStateFlow<Entry?>(null)

    private fun askRating(e: Entry) {
        if (e.liked && e.myRating == 0) {
            AppLog.d("Nota", "se pide nota para «${e.title}»")
            ratePrompt.value = e
        }
    }

    fun rateFromPrompt(score: Int) {
        val e = ratePrompt.value ?: return
        ratePrompt.value = null
        AppLog.i("Nota", "«${e.title}» → $score")
        setMyRating(e, score)
    }

    fun dismissRatePrompt() {
        ratePrompt.value?.let { AppLog.d("Nota", "omitida para «${it.title}»") }
        ratePrompt.value = null
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
        if (discoverQueue.value.firstOrNull()?.movieId != e.movieId) {
            AppLog.d("Descubrir", "respuesta ignorada: «${e.title}» ya no es la tarjeta de arriba")
            return
        }
        AppLog.i("Descubrir", "$answer → «${e.title}» (${e.movieId}); cola=${discoverQueue.value.size}")
        val seq = ++answerSeq
        if (answer == Answer.Skip) {
            skipped += e.movieId
            peli.exposure.markSkipped(e.movieId, System.currentTimeMillis())
        }
        undoReturned -= e.movieId
        queue.update { it.filter { x -> x.movieId != e.movieId } }
        revalidateQueue()
        launch {
            if (answer != Answer.Skip) {
                val previous = repo.snapshot(e.movieId)
                when (answer) {
                    Answer.Want -> applyAndPush(e) { it.copy(watchlist = true, seen = false, liked = false, disliked = false) }
                    Answer.SeenLiked -> askRating(
                        applyAndPush(e) { it.copy(liked = true, seen = true, watchlist = false, disliked = false) },
                    )
                    Answer.NotInterested -> applyAndPush(e) { it.copy(disliked = true, liked = false, watchlist = false) }
                    Answer.Skip -> Unit
                }
                // Con varias respuestas seguidas, solo la última manda la oferta de deshacer.
                if (seq == answerSeq) undo.value = UndoOffer(
                    message = when (answer) {
                        Answer.Want -> "Guardada en «Quiero verla»"
                        Answer.SeenLiked -> "Guardada como «La vi y me gustó»"
                        else -> "Descartada"
                    },
                    card = e,
                    previous = previous,
                )
            }
            peli.exposure.countDiscoverAnswer()
            rerankQueue()
            if (discoverQueue.value.size < 4) refillQueue()
        }
    }

    /**
     * Descubrir aprende en cada respuesta: se conserva la tarjeta que está arriba y el resto de la cola
     * se vuelve a ordenar con el perfil actualizado.
     */
    private suspend fun rerankQueue() {
        val marked = repo.mine.first()
        val profile = Recommender.buildProfile(marked, facts = repo.profileFacts(marked, 0))
        queue.update { q ->
            if (q.size <= 2) q else {
                val head = q.first()
                val tail = q.drop(1)
                // Se reordena solo lo que aún no se vio, con diversidad respecto de las últimas tarjetas vistas.
                listOf(head) + Recommender.select(
                    tail, profile, queueMeta, tail.size,
                    recent = listOf(head) + shownCards.toList(),
                    surface = Recommender.Surface.DISCOVER, maxUpcoming = tail.size,
                )
            }
        }
    }

    /** Quita de la cola lo que otra pantalla entregó después de armarla (la siguiente tarjeta incluida). */
    private fun revalidateQueue() {
        val store = peli.exposure
        val snapshot = store.snapshot()
        val round = store.round() + 1
        val now = System.currentTimeMillis()
        queue.update { q ->
            q.filter {
                stillValid(it, snapshot, round, now) ||
                    (it.movieId in promoted && (snapshot[it.movieId]?.skips ?: 0) == 0)
            }
        }
    }

    /**
     * ¿Sigue sirviendo una tarjeta de la cola? No si, mientras tanto, se entregó en otra pantalla o ya se
     * respondió. Excepciones: la que se está viendo ahora y la que volvió por "deshacer".
     */
    private fun stillValid(e: Entry, snapshot: Map<Int, com.peli.app.data.Exposure>, round: Long, now: Long): Boolean =
        e.movieId == shownCardId || e.movieId in undoReturned || ExposureRules.eligible(snapshot[e.movieId], round, now)

    /**
     * Lo que le interesa a la otra persona pasa por la misma memoria, con UNA excepción documentada: no se
     * esconde por haberse mostrado y no respondido (se cerró la app antes de contestar); solo por haberla omitido.
     */
    private fun partnerPicksAllowed(snapshot: Map<Int, com.peli.app.data.Exposure>, round: Long, now: Long): List<Entry> =
        partnerPicks().filter { (snapshot[it.movieId]?.skips ?: 0) == 0 || ExposureRules.eligible(snapshot[it.movieId], round, now) }

    /** Descubrir muestra esta tarjeta: se anota UNA vez por presentación (no la precargada ni una recomposición). */
    fun onDiscoverCardShown(e: Entry) {
        if (shownCardId == e.movieId) return
        shownCardId = e.movieId
        shownCards.addLast(e)
        while (shownCards.size > 5) shownCards.removeFirst()
        undoReturned -= e.movieId
        launch(Dispatchers.IO) { peli.exposure.markShown(e.movieId, System.currentTimeMillis()) }
    }

    /** Películas de la lista principal que llegaron a verse en pantalla (para la memoria de lo mostrado). */
    fun markVisible(ids: Collection<Int>) {
        if (ids.isEmpty()) return
        launch(Dispatchers.IO) { peli.exposure.markVisible(ids) }
    }

    /** Deshace la última respuesta: restaura la marca anterior y devuelve la tarjeta al frente. */
    fun undoAnswer() {
        val offer = undo.value ?: return
        AppLog.i("Descubrir", "deshacer «${offer.card.title}»")
        undo.value = null
        // Una nota pedida para la acción que se deshace ya no corresponde.
        if (ratePrompt.value?.movieId == offer.card.movieId) ratePrompt.value = null
        launch {
            repo.restore(offer.card, offer.previous)
            requestDrain()
            undoReturned += offer.card.movieId
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
            .also { picks -> promoted += picks.map { it.movieId } }
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
            val t0 = System.currentTimeMillis()
            try {
                val marked = repo.mine.first()
                val years = searchYears.value
                val filters = movieFilters.value
                deliveryMutex.withLock {
                    val store = peli.exposure
                    val round = store.round() + 1
                    val now = System.currentTimeMillis()
                    val before = store.snapshot()
                    val recent = shownCards.toList()
                    val fb = FilterBudget(FILTER_FETCH_BUDGET)
                    val chosen = ArrayList<Entry>()
                    var snapshot = before
                    var tries = 0
                    val maxTries = if (filters.isActive) FILTER_ROUNDS else 1
                    while (chosen.size < REFILL_TARGET && tries < maxTries) {
                        tries++
                        val taken = chosen.map { it.movieId }.toSet()
                        val pool = repo.candidatePool(
                            marked, years, needed = REFILL_TARGET,
                            isUsable = { it !in taken && it !in filterRejected && ExposureRules.eligible(before[it], round, now) },
                        )
                        snapshot = store.snapshot()
                        val tiers = Delivery.tiers(
                            pool.candidates.filter { it.movieId !in taken && it.movieId !in filterRejected },
                            snapshot, round, now,
                        )
                        queueMeta.putAll(pool.meta)
                        chosen += pickTiers(tiers, pool, REFILL_TARGET - chosen.size, filters, fb, already = chosen.toList()) { usable, need, already ->
                            Recommender.select(
                                usable, pool.profile, pool.meta, need,
                                recent = recent + already, surface = Recommender.Surface.DISCOVER,
                            )
                        }
                        if (fb.fetch <= 0 || (filters.needsOmdb && repo.omdbBlocked)) break
                    }
                    val known = repo.mine.first().map { it.movieId }.toSet()
                    // Lo que ya estaba en la cola se vuelve a validar contra lo entregado después de armarla.
                    val keep = queue.value.filter { stillValid(it, snapshot, round, now) }
                    val picks = repo.applyFilters(partnerPicksAllowed(snapshot, round, now), filters, want = Int.MAX_VALUE, maxFetch = 10).kept
                        .filter { years.accepts(it.year) }
                    queue.value = (picks + keep + chosen)
                        .filter { it.movieId !in known && it.movieId !in skipped }
                        .distinctBy { it.movieId }
                    if (filters.needsOmdb && (fb.limited || repo.omdbBlocked) && chosen.size < REFILL_TARGET) {
                        message.value = "OMDb llegó a su límite de consultas por hoy: algunas películas no se pudieron revisar con estos filtros."
                    }
                }
                AppLog.i("Descubrir", "cola rellenada: ${queue.value.size} sugerencias (${System.currentTimeMillis() - t0} ms)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Descubrir", "falló al rellenar la cola", e)
                queueError.value = "Error de red: ${e.message}"
            } finally {
                // Si este trabajo fue reemplazado (cambiaron los filtros), el nuevo es quien apaga el "cargando".
                if (refillJob === coroutineContext[Job]) queueLoading.value = false
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
        AppLog.i("Sala", "entrando a una sala (código ${code.take(2)}…)")
        ctx.invalidate()
        pairing.room = code
        scopes.clearSeeded(Scopes.room(code))   // al entrar se encola todo lo guardado
        room.value = code
        launch {
            repo.clearPartner()
            partnerSource.value = null
            sync()
        }
    }

    fun leaveRoom() {
        AppLog.i("Sala", "saliendo de la sala")
        ctx.invalidate()
        pairing.room = null
        room.value = null
        syncError.value = null
        launch {
            repo.clearPartner()
            partnerSource.value = null
        }
    }

    fun roomShareText(): String {
        val code = room.value?.let { RoomCodes.format(it) } ?: ""
        return "Únete a mi sala en Peli para ver qué películas queremos ver juntos. Código: $code\n" +
            "(Peli → Juntos → Tengo un código)"
    }

    /**
     * Un ciclo de sincronización (cada pocos segundos mientras la app está visible): primero se envía lo
     * pendiente (cola durable) y después se trae la lista de la otra persona.
     */
    suspend fun sync() {
        ensureSeeded()
        drainOutbox()
        if (account.session != null && account.profile != null) pullAccount() else pullRoom()
    }

    /** Pide enviar lo pendiente sin bloquear la pantalla (los botones no esperan a la red). */
    private fun requestDrain() {
        launch { drainOutbox() }
    }

    /** Hay UN solo drenador a la vez; ninguna pantalla envía por una vía paralela. */
    private suspend fun drainOutbox() {
        if (!Remote.configured) return
        drainLock.withLock {
            for (dest in repo.destinations()) {
                try {
                    val n = repo.drain(dest, pairing.userId)
                    if (n > 0) AppLog.d("Envío", "$n cambios enviados a ${dest.substringBefore(':')}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w("Envío", "no se pudo enviar lo pendiente (se reintenta): ${repo.pending(dest)} en cola", e)
                    syncError.value = e.accountMessage()
                    return
                }
            }
        }
    }

    /** Al activar un destino por primera vez (o de nuevo) se encola todo lo guardado; así nada depende de lo que la pantalla ya cargó. */
    private suspend fun ensureSeeded() {
        for (dest in repo.destinations()) {
            if (!scopes.isSeeded(dest)) {
                repo.enqueueAll(dest)
                scopes.markSeeded(dest)
                AppLog.i("Envío", "se encoló todo lo guardado para ${dest.substringBefore(':')}")
            }
        }
    }

    /** Sala del modo anterior (solo sin cuenta con usuario): trae la lista completa de las demás personas. */
    private suspend fun pullRoom() {
        val r = room.value ?: return
        if (!Remote.configured) return
        val token = ctx.token()
        syncLock.withLock {
            try {
                val others = repo.pullRoom(r, pairing.userId)
                if (!ctx.valid(token) || room.value != r) {
                    AppLog.d("Sala", "respuesta descartada: la sala cambió mientras se pedía")
                    return
                }
                repo.setPartner(others)
                partnerSource.value = Scopes.room(r)
                if (syncError.value != null) AppLog.i("Sala", "sincronización recuperada")
                syncError.value = null
                picksToFront()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (syncError.value == null) AppLog.w("Sala", "falló la sincronización", e)
                syncError.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    // ---- Cuenta de Google, usuario y amigos -----------------------------------

    /** Hay servidor y cliente de Google configurados en esta compilación. */
    val googleAvailable = Social.configured

    val signedIn = MutableStateFlow(account.session != null)
    val accountEmail = MutableStateFlow(account.session?.email)
    val profile = MutableStateFlow(account.profile)
    val friends = MutableStateFlow<List<Friend>>(emptyList())
    val activeFriend = MutableStateFlow(account.activeFriend)
    val accountBusy = MutableStateFlow(false)
    val claim = MutableStateFlow<PendingClaim?>(null)
    private var answerSeq = 0
    private val promoted = HashSet<Int>()

    /** Hay algo que sincronizar en segundo plano: una sala (modo anterior) o mi cuenta con usuario. */
    val syncActive: StateFlow<Boolean> = combine(room, profile) { r, p -> r != null || p != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, room.value != null || profile.value != null)

    fun showMessage(text: String) { message.value = text }

    /** Google entregó su token: se canjea por una sesión de Supabase y se busca mi perfil. */
    fun signInWithGoogle(token: GoogleToken) {
        launch {
            accountBusy.value = true
            try {
                val session = withContext(Dispatchers.IO) { SupabaseAuth.signInWithGoogle(token.idToken, token.rawNonce) }
                // Qué colección de películas queda activa se decide ANTES de guardar la sesión.
                val from = scopes.current(account.session?.userId)
                ctx.invalidate()
                account.save(session)
                signedIn.value = true
                accountEmail.value = session.email
                AppLog.i("Cuenta", "sesión iniciada")
                applyLoginPlan(from, session.userId)
                loadProfile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Cuenta", "falló el canje del token de Google", e)
                message.value = e.accountMessage()
            } finally {
                accountBusy.value = false
            }
        }
    }

    private suspend fun loadProfile() {
        val p = repo.myProfile()
        account.profile = p
        profile.value = p
        AppLog.i("Cuenta", if (p == null) "sin nombre de usuario todavía" else "perfil: @${p.username}")
        if (p != null) sync()
    }

    fun createProfile(username: String) {
        val name = username.trim()
        if (!Usernames.isValid(name)) {
            message.value = Usernames.explain(name) ?: "Escribe un nombre de usuario."
            return
        }
        launch {
            accountBusy.value = true
            try {
                val p: Profile = repo.createProfile(name)
                account.profile = p
                profile.value = p
                AppLog.i("Cuenta", "perfil creado: @${p.username}")
                message.value = "Listo, @${p.username}. Tu ID de amigo es ${com.peli.app.data.FriendCodes.format(p.friend_code)}."
                sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Cuenta", "no se pudo crear el perfil", e)
                message.value = e.accountMessage()
            } finally {
                accountBusy.value = false
            }
        }
    }

    fun addFriend(query: String) {
        val q = query.trim()
        if (q.length < 3) {
            message.value = "Escribe el ID (ABCD-EFGH) o el @usuario de tu amigo."
            return
        }
        launch {
            accountBusy.value = true
            try {
                message.value = when (val r = FriendRequestResult.parse(repo.requestFriend(q))) {
                    is FriendRequestResult.Sent -> "Solicitud enviada a @${r.username}. Cuando la acepte, podrán comparar listas."
                    is FriendRequestResult.Accepted -> "¡Ahora eres amigo de @${r.username}!"
                    is FriendRequestResult.AlreadyExists -> "Ya tienes una solicitud o amistad con @${r.username}."
                    FriendRequestResult.NotFound -> "No encontré a nadie con ese ID o usuario."
                    FriendRequestResult.Self -> "Ese eres tú 🙂"
                    null -> "Respuesta inesperada del servidor."
                }
                AppLog.i("Amigos", "agregar «$q» → ${message.value}")
                pullAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Amigos", "no se pudo agregar a «$q»", e)
                message.value = e.accountMessage()
            } finally {
                accountBusy.value = false
            }
        }
    }

    fun acceptFriend(f: Friend) = friendAction("aceptar a @${f.username}") {
        repo.acceptFriend(f.friend_id)
        if (account.activeFriend == null) switchFriend(f.friend_id)
    }

    fun removeFriend(f: Friend) = friendAction("quitar a @${f.username}") {
        repo.removeFriend(f.friend_id)
        if (account.activeFriend == f.friend_id) switchFriend(null)
    }

    private fun friendAction(what: String, block: suspend () -> Unit) {
        launch {
            accountBusy.value = true
            try {
                AppLog.i("Amigos", what)
                block()
                pullAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Amigos", "no se pudo $what", e)
                message.value = e.accountMessage()
            } finally {
                accountBusy.value = false
            }
        }
    }

    /** Elige con qué amigo se comparan las listas (matches, "Descubrir" y nota de tu pareja). */
    fun selectFriend(id: String) {
        if (account.activeFriend == id) return
        AppLog.i("Amigos", "se compara con el amigo $id")
        launch {
            switchFriend(id)
            pullAccount()
        }
    }

    /**
     * Cambia el amigo activo (o ninguno). Invalida lo que estaba en vuelo, vacía la lista del amigo anterior
     * y retira de las sugerencias lo que venía de él.
     */
    private suspend fun switchFriend(id: String?) {
        ctx.invalidate()
        repo.clearPartner()
        partnerSource.value = null
        account.activeFriend = id
        account.baselineFriend = null
        activeFriend.value = id
        queue.update { q -> q.filter { it.movieId !in promoted } }
        promoted.clear()
    }

    fun signOut() {
        AppLog.i("Cuenta", "cerrar sesión")
        ctx.invalidate()
        val s = account.session
        val scope = scopes.scope
        account.clear()
        signedIn.value = false
        accountEmail.value = null
        profile.value = null
        friends.value = emptyList()
        activeFriend.value = null
        claim.value = null
        launch {
            // Las películas de la cuenta quedan guardadas aparte y vuelve la colección sin cuenta del teléfono.
            if (s != null && scope == Scopes.account(s.userId)) {
                repo.switchScope(scope, Scopes.GUEST)
                scopes.scope = Scopes.GUEST
            }
            repo.clearPartner()
            partnerSource.value = null
            queue.update { q -> q.filter { it.movieId !in promoted } }
            promoted.clear()
        }
    }

    /** Trae mis amigos y la lista COMPLETA del amigo elegido; si el contexto cambió mientras tanto, se descarta. */
    private suspend fun pullAccount() {
        val s = account.session ?: return
        if (account.profile == null) return
        val token = ctx.token()
        val friendId = account.activeFriend
        syncLock.withLock {
            try {
                val result = repo.pullAccount(friendId)
                if (!ctx.valid(token) || account.session?.userId != s.userId || account.activeFriend != friendId) {
                    AppLog.d("Cuenta", "respuesta descartada: el contexto cambió mientras se pedía")
                    return
                }
                friends.value = result.friends
                val accepted = result.friends.filter { it.accepted }
                when {
                    // El amigo elegido ya no es amigo: se deja de comparar.
                    friendId != null && accepted.none { it.friend_id == friendId } -> {
                        switchFriend(null)
                        syncError.value = null
                    }
                    // Sin amigo elegido: si hay exactamente uno aceptado, se elige solo (la lista llega en el próximo ciclo).
                    friendId == null -> {
                        if (accepted.size == 1) switchFriend(accepted.first().friend_id)
                        syncError.value = null
                    }
                    else -> {
                        val partner = result.partner ?: return
                        val c = "friend:$friendId"
                        // Los matches que ya existían al elegir al amigo no se "estrenan" todos de golpe.
                        if (account.baselineFriend != friendId) {
                            val existing = computeMatches(repo.mine.first(), partner).filter { it.both }.map { it.entry.movieId }
                            pairing.addAnnounced(c, existing)
                            account.baselineFriend = friendId
                        }
                        repo.setPartner(partner)
                        partnerSource.value = c
                        picksToFront()
                        syncError.value = null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (syncError.value == null) AppLog.w("Cuenta", "falló la sincronización de la cuenta", e)
                syncError.value = e.accountMessage()
            }
        }
    }

    /** Contexto de los matches: el amigo elegido (con cuenta) o la sala (modo anterior). */
    private fun matchContext(): String? =
        if (account.session != null && account.profile != null) account.activeFriend?.let { "friend:$it" }
        else room.value?.let { Scopes.room(it) }

    /** Colección de películas del teléfono según la sesión (ver [Scopes.loginPlan]); nunca mezcla en silencio. */
    private suspend fun applyLoginPlan(from: String, userId: String) {
        val to = Scopes.account(userId)
        val plan = Scopes.loginPlan(from, to, hasStash = repo.stashCount(to) > 0, meCount = repo.meCount())
        AppLog.i("Cuenta", "colección local al iniciar sesión: $plan")
        when (plan) {
            Scopes.LoginPlan.Same -> scopes.scope = to
            Scopes.LoginPlan.Restore -> {
                repo.switchScope(from, to)
                scopes.scope = to
                scopes.clearSeeded(to)
            }
            Scopes.LoginPlan.Adopt -> {
                scopes.scope = to
                scopes.clearSeeded(to)
            }
            // Hay películas guardadas sin cuenta: el usuario decide si pasan a esta cuenta.
            Scopes.LoginPlan.AskClaim -> claim.value = PendingClaim(to, repo.meCount())
        }
    }

    /** Respuesta del usuario: usar (true) o no las películas de este teléfono con la cuenta que acaba de entrar. */
    fun claimGuestCollection(use: Boolean) {
        val c = claim.value ?: return
        claim.value = null
        launch {
            if (!use) repo.switchScope(Scopes.GUEST, c.scope)   // quedan guardadas aparte; la cuenta empieza vacía
            scopes.scope = c.scope
            scopes.clearSeeded(c.scope)
            AppLog.i("Cuenta", if (use) "las películas de este teléfono pasan a la cuenta" else "las películas de este teléfono se guardan aparte")
            sync()
        }
    }

    private fun picksToFront() {
        val store = peli.exposure
        val years = searchYears.value
        val filters = movieFilters.value
        // Aquí solo se revisa lo que no necesita consultar nada (años y nota de TMDB); el resto lo revisa el relleno de la cola.
        val picks = partnerPicksAllowed(store.snapshot(), store.round() + 1, System.currentTimeMillis())
            .filter { years.accepts(it.year) && filters.acceptsTmdb(it.rating) && !filters.needsOmdb }
        if (picks.isNotEmpty()) queue.update { (picks.filter { p -> p.movieId !in skipped } + it).distinctBy { x -> x.movieId } }
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
            AppLog.d("Actualizador", "consultando actualización (${if (manual) "manual" else "automática"})")
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
        AppLog.i("Actualizador", "el usuario pidió instalar v$v")
        launch {
            progress.value = -1
            try {
                withContext(Dispatchers.IO) {
                    Updater.downloadAndInstall(getApplication(), v) { p -> progress.value = p }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Actualizador", "no se pudo instalar v$v", e)
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
        const val ENRICH_DELAY_MS = 4_000L
        const val MAX_FEED = 300
        const val FILTER_ROUNDS = 3
        const val FILTER_FETCH_BUDGET = 45
        const val FILTER_RANK_NOISE = 0.1
        const val REFILL_TARGET = 40
        const val FEED_NOISE = 0.3
    }
}
